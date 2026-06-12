package com.lexipopup.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.lexipopup.data.local.dao.ChatDao
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.database.toDomain
import com.lexipopup.data.local.database.toEntity
import com.lexipopup.data.local.entities.ChatMessageEntity
import com.lexipopup.data.local.entities.ChatSessionEntity
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.VocabularyRepository
import com.lexipopup.utils.ModeManager
import com.lexipopup.utils.SettingsDataStore
import com.lexipopup.utils.ai.AiChatClient
import com.lexipopup.utils.ai.AiProviderManager
import com.lexipopup.utils.ai.AiProviderType
import com.lexipopup.utils.ai.ChatApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val chatDao: ChatDao,
    private val wordDao: WordDao,
    private val vocabularyRepository: VocabularyRepository,
    private val aiChatClient: AiChatClient,
    private val aiProviderManager: AiProviderManager,
    private val settingsDataStore: SettingsDataStore,
    private val modeManager: ModeManager,
    private val gson: Gson
) : ViewModel() {

    companion object {
        private const val ENGLISH_SYSTEM_PROMPT = """You are LexiPopup AI — an advanced English language and vocabulary assistant. Help users:
• Learn new words, meanings, etymology, synonyms, antonyms
• Translate text to/from Hindi and other languages  
• Understand grammar rules and usage
• Discuss any topic with vocabulary enrichment
• Answer questions about English literature and language

Be conversational, educational, accurate, and concise. When you mention an interesting word, you may briefly note its meaning inline."""

        private const val BIOLOGY_SYSTEM_PROMPT = """You are LexiPopup Biology Expert — an advanced biology tutor specialising in NEET UG and NEET PG preparation (India). Your role is to:
• Explain biological concepts clearly: Cell Biology, Genetics & Evolution, Ecology, Human Physiology, Plant Physiology, Reproduction, Biotechnology, Biodiversity
• Cover advanced NEET PG topics: Pathology, Pharmacology, Biochemistry, Microbiology, Anatomy, Physiology
• Define scientific terminology with etymology (Greek/Latin roots)
• Provide mnemonics and memory aids for complex topics
• Answer MCQ-style NEET questions with step-by-step explanations and the correct option clearly stated
• Explain diagrams, biological mechanisms, and biochemical pathways in clear text
• Provide clinical correlations for NEET PG topics (disease mechanisms, drug targets)

Use precise scientific language. When mentioning a biological term, briefly note its significance or etymology. Always be accurate — errors in biology and medicine can be harmful. Cite classic experiments and their authors where relevant (e.g., Mendel, Watson & Crick, Hershey-Chase)."""
    }

    /** Returns the system prompt appropriate for the current mode. */
    private fun systemPrompt(): String =
        if (modeManager.currentMode.value == AppMode.BIOLOGY) BIOLOGY_SYSTEM_PROMPT
        else ENGLISH_SYSTEM_PROMPT

    // ── Active mode (exposed to UI) ────────────────────────────────────────────

    val activeMode: StateFlow<AppMode> = modeManager.currentMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppMode.ENGLISH)

    // ── Sessions — filtered by current mode ───────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<ChatSessionEntity>> = modeManager.currentMode
        .flatMapLatest { mode -> chatDao.getSessionsByMode(mode.id) }
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
        // When mode switches, clear current session so sessions list refreshes cleanly
        viewModelScope.launch {
            var previousMode = modeManager.currentMode.value
            modeManager.currentMode.collect { newMode ->
                if (newMode != previousMode) {
                    previousMode = newMode
                    startNewSession()
                }
            }
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
            val currentMode = modeManager.currentMode.value

            // Ensure session exists — tag it with the active mode
            val sessionId = _currentSessionId.value ?: run {
                val newId = chatDao.insertSession(
                    ChatSessionEntity(
                        title    = text.take(50).trimEnd().let { if (it.length == 50) "$it…" else it },
                        provider = _selectedProvider.value.id,
                        mode     = currentMode.id
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
                add(AiChatClient.Message("system", systemPrompt()))
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

            val currentMode   = modeManager.currentMode.value
            val currentModeId = currentMode.id
            var saved = 0
            var skipped = 0
            words.forEach { word ->
                val existing = wordDao.findWord(word.word, currentModeId)
                if (existing == null) {
                    try {
                        wordDao.insertWord(word.toEntity(gson))
                        wordDao.updateAccess(word.word, currentModeId)
                        vocabularyRepository.recordSearch(word.word, "LexiPopup AI Chat", currentMode)
                        saved++
                    } catch (_: Exception) { skipped++ }
                } else {
                    wordDao.updateAccess(word.word, currentModeId)
                    vocabularyRepository.recordSearch(word.word, "LexiPopup AI Chat", currentMode)
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
            val currentMode   = modeManager.currentMode.value
            val currentModeId = currentMode.id
            val local = wordDao.findWord(word, currentModeId)
            if (local != null) {
                wordDao.updateAccess(word, currentModeId)
                vocabularyRepository.recordSearch(word, "LexiPopup AI Chat", currentMode)
                _wordLookup.value = WordLookupState.Found(word, local.toDomain(gson))
                return@launch
            }
            val entry = aiProviderManager.explain(word)
            if (entry != null) {
                vocabularyRepository.recordSearch(word, "LexiPopup AI Chat", currentMode)
                _wordLookup.value = WordLookupState.Found(word, entry)
            } else {
                _wordLookup.value = WordLookupState.NotFound(word)
            }
        }
    }

    fun saveWordToHistory(entry: WordEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMode   = modeManager.currentMode.value
            val currentModeId = currentMode.id
            val existing = wordDao.findWord(entry.word, currentModeId)
            if (existing == null) {
                wordDao.insertWord(entry.toEntity(gson))
            }
            wordDao.updateAccess(entry.word, currentModeId)
            vocabularyRepository.recordSearch(entry.word, "LexiPopup AI Chat", currentMode)
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
}
