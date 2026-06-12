package com.lexipopup.presentation.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.lexipopup.data.local.dao.ChatDao
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.database.toDomain
import com.lexipopup.data.local.database.toEntity
import com.lexipopup.data.local.entities.ChatMessageEntity
import com.lexipopup.data.local.entities.ChatSessionEntity
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.utils.SettingsDataStore
import com.lexipopup.utils.ai.AiChatClient
import com.lexipopup.utils.ai.AiProviderManager
import com.lexipopup.utils.ai.AiProviderType
import com.lexipopup.utils.ai.ChatApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

// ── Supporting types ─────────────────────────────────────────────────────────

data class RateLimitInfo(val provider: String, val retryAfterSeconds: Int)

sealed class ExtractionResult {
    object Loading : ExtractionResult()
    data class Done(
        val total: Int, val saved: Int, val skipped: Int,
        val words: List<WordEntry>
    ) : ExtractionResult()
    data class Err(val message: String) : ExtractionResult()
}

sealed class WordLookupState {
    data class Loading(val word: String) : WordLookupState()
    data class Found(val word: String, val entry: WordEntry) : WordLookupState()
    data class NotFound(val word: String) : WordLookupState()
}

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class AiChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatDao: ChatDao,
    private val wordDao: WordDao,
    private val aiChatClient: AiChatClient,
    private val aiProviderManager: AiProviderManager,
    private val settingsDataStore: SettingsDataStore,
    private val gson: Gson
) : ViewModel() {

    companion object {
        private const val SYSTEM_PROMPT = """You are LexiPopup AI — an advanced English language and vocabulary assistant. Help users:
• Learn new words, meanings, etymology, synonyms, antonyms
• Translate text to/from Hindi and other languages  
• Understand grammar rules and usage
• Discuss any topic with vocabulary enrichment
• Answer questions about English literature and language

Be conversational, educational, accurate, and concise. When you mention an interesting word, you may briefly note its meaning inline."""
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    val sessions: StateFlow<List<ChatSessionEntity>> =
        chatDao.getAllSessions()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)

    val currentSession: StateFlow<ChatSessionEntity?> =
        combine(_currentSessionId, sessions) { id, list ->
            list.find { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    private var messagesJob: Job? = null

    // ── Provider & settings ───────────────────────────────────────────────────

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _selectedProvider = MutableStateFlow(AiProviderType.GROQ)
    val selectedProvider: StateFlow<AiProviderType> = _selectedProvider.asStateFlow()

    // ── Chat state ────────────────────────────────────────────────────────────

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _typingText = MutableStateFlow("")
    val typingText: StateFlow<String> = _typingText.asStateFlow()

    private val _rateLimitInfo = MutableStateFlow<RateLimitInfo?>(null)
    val rateLimitInfo: StateFlow<RateLimitInfo?> = _rateLimitInfo.asStateFlow()

    // ── Extraction & word lookup ──────────────────────────────────────────────

    private val _extractionResult = MutableStateFlow<ExtractionResult?>(null)
    val extractionResult: StateFlow<ExtractionResult?> = _extractionResult.asStateFlow()

    private val _wordLookup = MutableStateFlow<WordLookupState?>(null)
    val wordLookup: StateFlow<WordLookupState?> = _wordLookup.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            val s = settingsDataStore.settings.first()
            _selectedProvider.value = AiProviderType.fromId(s.aiProviderName)
        }
    }

    // ── Session management ────────────────────────────────────────────────────

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
        observeMessages(sessionId)
    }

    fun startNewSession() {
        _currentSessionId.value = null
        _messages.value = emptyList()
        messagesJob?.cancel()
        _rateLimitInfo.value = null
        _typingText.value = ""
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatDao.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) startNewSession()
        }
    }

    private fun observeMessages(sessionId: Long) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatDao.getMessagesForSession(sessionId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    // ── Provider ──────────────────────────────────────────────────────────────

    fun setProvider(type: AiProviderType) {
        _selectedProvider.value = type
    }

    // ── Send message ──────────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank() || _isTyping.value) return

        viewModelScope.launch {
            val cfg = settingsDataStore.settings.first()

            // Ensure session exists
            val sessionId = _currentSessionId.value ?: run {
                val newId = chatDao.insertSession(
                    ChatSessionEntity(
                        title    = text.take(50).trimEnd().let { if (it.length == 50) "$it…" else it },
                        provider = _selectedProvider.value.id
                    )
                )
                _currentSessionId.value = newId
                observeMessages(newId)
                newId
            }

            // Snapshot existing messages before inserting — the collect() coroutine that
            // updates _messages is dispatched separately and may not have fired yet,
            // so reading _messages.value after insertMessage risks missing the new message.
            val historySnapshot = _messages.value

            // Persist user message
            chatDao.insertMessage(ChatMessageEntity(
                sessionId = sessionId,
                role      = "user",
                content   = text,
                provider  = _selectedProvider.value.id
            ))
            chatDao.incrementMessageCount(sessionId)

            // Build full history: prior messages (snapshot) + the new user message, totalling up to 25
            val history = buildList {
                add(AiChatClient.Message("system", SYSTEM_PROMPT))
                historySnapshot.takeLast(23).forEach { m ->
                    add(AiChatClient.Message(m.role, m.content))
                }
                add(AiChatClient.Message("user", text))
            }

            // Call AI — animate typing while waiting
            _isTyping.value = true
            _typingText.value = ""

            val result = aiChatClient.chat(
                messages     = history,
                provider     = _selectedProvider.value,
                groqApiKey   = cfg.groqApiKey,
                openAiApiKey = cfg.openAiApiKey
            )

            _isTyping.value = false

            when (result) {
                is ChatApiResult.Success -> {
                    // Animated character reveal
                    val full = result.content
                    var displayed = ""
                    for (char in full) {
                        displayed += char
                        _typingText.value = displayed
                        delay(8)  // ~125 chars/sec — feels natural
                    }
                    _typingText.value = ""

                    chatDao.insertMessage(ChatMessageEntity(
                        sessionId  = sessionId,
                        role       = "assistant",
                        content    = full,
                        provider   = _selectedProvider.value.id,
                        tokensUsed = result.tokensUsed
                    ))
                    chatDao.incrementMessageCount(sessionId)
                    _rateLimitInfo.value = null
                }

                is ChatApiResult.RateLimit -> {
                    _rateLimitInfo.value = RateLimitInfo(result.provider.ifBlank { _selectedProvider.value.label }, result.retryAfterSeconds)
                    val msg = "⚠️ Rate limit reached for ${_selectedProvider.value.label}. Please wait ${result.retryAfterSeconds} seconds, then try again — or switch to another provider."
                    chatDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = "assistant", content = msg, isError = true, provider = _selectedProvider.value.id))
                    chatDao.incrementMessageCount(sessionId)
                }

                is ChatApiResult.NoKey -> {
                    val msg = "⚠️ No ${result.provider} API key configured.\n\nGo to **Settings → AI Settings** to add your free API key.\n• Groq: free at console.groq.com (no credit card needed)\n• OpenAI: platform.openai.com"
                    chatDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = "assistant", content = msg, isError = true, provider = _selectedProvider.value.id))
                    chatDao.incrementMessageCount(sessionId)
                }

                is ChatApiResult.Error -> {
                    val msg = "⚠️ ${result.message}"
                    chatDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = "assistant", content = msg, isError = true, provider = _selectedProvider.value.id))
                    chatDao.incrementMessageCount(sessionId)
                }
            }
        }
    }

    // ── Vocabulary extraction ─────────────────────────────────────────────────

    fun extractVocabulary() {
        val msgs = _messages.value.filter { !it.isError }
        if (msgs.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _extractionResult.value = ExtractionResult.Loading
            val cfg = settingsDataStore.settings.first()

            val conversationText = msgs.joinToString("\n") {
                "[${it.role.uppercase()}]: ${it.content}"
            }

            val words = aiChatClient.extractVocabulary(
                conversationText = conversationText,
                provider         = _selectedProvider.value,
                groqApiKey       = cfg.groqApiKey,
                openAiApiKey     = cfg.openAiApiKey
            )

            if (words.isEmpty()) {
                _extractionResult.value = ExtractionResult.Err("AI couldn't extract vocabulary from this conversation. Try chatting more or switching providers.")
                return@launch
            }

            var saved = 0
            var skipped = 0
            words.forEach { word ->
                val existing = wordDao.findWord(word.word)
                if (existing == null) {
                    try {
                        wordDao.insertWord(word.toEntity(gson))
                        wordDao.updateAccess(word.word)
                        saved++
                    } catch (_: Exception) { skipped++ }
                } else {
                    skipped++
                }
            }

            _extractionResult.value = ExtractionResult.Done(
                total   = words.size,
                saved   = saved,
                skipped = skipped,
                words   = words
            )
        }
    }

    // ── Word lookup (long-press) ──────────────────────────────────────────────

    fun lookupWord(word: String) {
        _wordLookup.value = WordLookupState.Loading(word)
        viewModelScope.launch(Dispatchers.IO) {
            val local = wordDao.findWord(word)
            if (local != null) {
                _wordLookup.value = WordLookupState.Found(word, local.toDomain(gson))
                return@launch
            }
            val entry = aiProviderManager.explain(word)
            _wordLookup.value = if (entry != null) {
                WordLookupState.Found(word, entry)
            } else {
                WordLookupState.NotFound(word)
            }
        }
    }

    fun saveWordToHistory(entry: WordEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = wordDao.findWord(entry.word)
            if (existing == null) {
                wordDao.insertWord(entry.toEntity(gson))
            }
            wordDao.updateAccess(entry.word)
        }
    }

    fun toggleUnderline(messageId: Long, word: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = _messages.value.find { it.id == messageId } ?: return@launch
            val set = runCatching {
                gson.fromJson(msg.underlinedWords, Array<String>::class.java).toMutableSet()
            }.getOrDefault(mutableSetOf())

            if (set.any { it.equals(word, ignoreCase = true) }) {
                set.removeIf { it.equals(word, ignoreCase = true) }
            } else {
                set.add(word)
            }
            chatDao.updateMessage(msg.copy(underlinedWords = gson.toJson(set.toList())))
        }
    }

    // ── Dismiss helpers ───────────────────────────────────────────────────────

    fun clearRateLimitInfo() { _rateLimitInfo.value = null }
    fun clearExtractionResult() { _extractionResult.value = null }
    fun clearWordLookup() { _wordLookup.value = null }

    // ── Text-to-Speech ────────────────────────────────────────────────────────

    private val _isSpeaking       = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsActive        = MutableStateFlow(false)
    val ttsActive: StateFlow<Boolean> = _ttsActive.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<Long?>(-1L)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    private val _speakingIndex    = MutableStateFlow(0)
    val speakingIndex: StateFlow<Int> = _speakingIndex.asStateFlow()

    private val _speakTotal       = MutableStateFlow(0)
    val speakTotal: StateFlow<Int> = _speakTotal.asStateFlow()

    private val _speakSpeed       = MutableStateFlow(1.0f)
    val speakSpeed: StateFlow<Float> = _speakSpeed.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady           = false
    private var speakQueue         = emptyList<ChatMessageEntity>()
    private var pausedAtIndex      = 0

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady) {
                tts?.setSpeechRate(_speakSpeed.value)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val idx = utteranceId?.toIntOrNull() ?: return
                        _speakingIndex.value    = idx
                        _speakingMessageId.value = speakQueue.getOrNull(idx)?.id
                        _isSpeaking.value       = true
                    }
                    override fun onDone(utteranceId: String?) {
                        val idx = (utteranceId?.toIntOrNull() ?: return)
                        if (idx >= speakQueue.size - 1) {
                            _isSpeaking.value        = false
                            _ttsActive.value         = false
                            _speakingMessageId.value = null
                        }
                    }
                    @Suppress("DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value        = false
                        _speakingMessageId.value = null
                    }
                    override fun onError(utteranceId: String, errorCode: Int) {
                        _isSpeaking.value        = false
                        _speakingMessageId.value = null
                    }
                })
            }
        }
    }

    fun speakMessage(message: ChatMessageEntity) {
        initTts()
        rawStop()
        speakQueue    = listOf(message)
        pausedAtIndex = 0
        _speakTotal.value = 1
        _ttsActive.value  = true
        enqueueFrom(0)
    }

    fun speakAllMessages(messages: List<ChatMessageEntity>) {
        val aiMsgs = messages.filter { it.role == "assistant" && !it.isError }
        if (aiMsgs.isEmpty()) return
        initTts()
        rawStop()
        speakQueue    = aiMsgs
        pausedAtIndex = 0
        _speakTotal.value = aiMsgs.size
        _ttsActive.value  = true
        enqueueFrom(0)
    }

    fun pauseSpeaking() {
        pausedAtIndex = _speakingIndex.value
        rawStop()
        _isSpeaking.value = false
        // keep _ttsActive = true so controller stays visible
    }

    fun resumeSpeaking() {
        if (speakQueue.isEmpty()) return
        enqueueFrom(pausedAtIndex)
        _isSpeaking.value = true
    }

    fun stopSpeaking() {
        rawStop()
        speakQueue               = emptyList()
        _isSpeaking.value        = false
        _ttsActive.value         = false
        _speakingMessageId.value = null
        _speakingIndex.value     = 0
        _speakTotal.value        = 0
    }

    fun seekToIndex(index: Int) {
        if (speakQueue.isEmpty()) return
        val clamped = index.coerceIn(0, speakQueue.size - 1)
        rawStop()
        pausedAtIndex = clamped
        _speakingIndex.value = clamped
        enqueueFrom(clamped)
        _isSpeaking.value = true
    }

    fun setSpeakSpeed(speed: Float) {
        _speakSpeed.value = speed
        tts?.setSpeechRate(speed)
    }

    private fun enqueueFrom(startIndex: Int) {
        speakQueue.drop(startIndex).forEachIndexed { i, msg ->
            val globalIdx = startIndex + i
            val locale = if (containsHindi(msg.content)) Locale("hi", "IN") else Locale.ENGLISH
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.ENGLISH
            }
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(msg.content, mode, null, globalIdx.toString())
        }
    }

    private fun rawStop() { tts?.stop() }

    private fun containsHindi(text: String) = text.any { it.code in 0x0900..0x097F }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
        tts = null
    }
}
