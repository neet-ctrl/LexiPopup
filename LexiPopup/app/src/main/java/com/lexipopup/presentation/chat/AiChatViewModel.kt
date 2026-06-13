package com.lexipopup.presentation.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
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
import java.io.ByteArrayOutputStream
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

/**
 * A file the user has attached to the pending message.
 *
 * @param uri           Content URI from SAF file picker
 * @param name          Display name (filename)
 * @param mimeType      Resolved MIME type
 * @param extractedText Readable text content — set for text files and PDFs
 * @param imageBase64   Base64-encoded image bytes — set for image/jpeg and image/png MIMEs
 */
data class AttachedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val extractedText: String? = null,
    val imageBase64: String? = null
)

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

        // Max text length injected into AI context from attached files
        private const val MAX_FILE_TEXT = 15_000
        // Max image dimension (px) before downscaling to keep base64 payload sane
        private const val MAX_IMG_SIDE = 1280
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

    // ── Attachment state ──────────────────────────────────────────────────────

    private val _pendingAttachment = MutableStateFlow<AttachedFile?>(null)
    val pendingAttachment: StateFlow<AttachedFile?> = _pendingAttachment.asStateFlow()

    private val _attachmentLoading = MutableStateFlow(false)
    val attachmentLoading: StateFlow<Boolean> = _attachmentLoading.asStateFlow()

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

    // ── File attachment ───────────────────────────────────────────────────────

    /**
     * Read a user-picked file from SAF.  Runs on IO dispatcher.
     * Sets [pendingAttachment] when done; sets [attachmentLoading] during processing.
     */
    fun attachFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _attachmentLoading.value = true
            try {
                val cr = context.contentResolver
                val mimeType = cr.getType(uri) ?: inferMime(uri)
                val name = resolveFileName(uri)

                val attached: AttachedFile = when {
                    mimeType.startsWith("image/") -> {
                        val b64 = readImageAsBase64(uri, mimeType)
                        AttachedFile(uri, name, mimeType, imageBase64 = b64)
                    }
                    mimeType == "application/pdf" -> {
                        val text = extractPdfText(uri)
                        AttachedFile(uri, name, mimeType, extractedText = text)
                    }
                    else -> {
                        // text/plain, text/csv, application/json, etc.
                        val text = runCatching {
                            cr.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                                ?.readText()?.take(MAX_FILE_TEXT)
                        }.getOrNull() ?: "[Could not read file content]"
                        AttachedFile(uri, name, mimeType, extractedText = text)
                    }
                }
                _pendingAttachment.value = attached
            } catch (e: Exception) {
                Log.e("AiChatViewModel", "attachFile error", e)
                _pendingAttachment.value = AttachedFile(
                    uri, resolveFileName(uri), "application/octet-stream",
                    extractedText = "[Error reading file: ${e.message}]"
                )
            } finally {
                _attachmentLoading.value = false
            }
        }
    }

    fun clearAttachment() { _pendingAttachment.value = null }

    private fun resolveFileName(uri: Uri): String {
        // Try content resolver display name first
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Exception) { null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "attachment"
    }

    private fun inferMime(uri: Uri): String {
        val path = uri.lastPathSegment?.lowercase() ?: return "application/octet-stream"
        return when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png")  -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif")  -> "image/gif"
            path.endsWith(".pdf")  -> "application/pdf"
            path.endsWith(".txt")  -> "text/plain"
            path.endsWith(".csv")  -> "text/csv"
            path.endsWith(".md")   -> "text/markdown"
            path.endsWith(".json") -> "application/json"
            else                   -> "application/octet-stream"
        }
    }

    private fun readImageAsBase64(uri: Uri, mimeType: String): String {
        val cr = context.contentResolver
        val rawBytes = cr.openInputStream(uri)?.readBytes()
            ?: return "[Image could not be read]"

        // Decode and optionally downscale
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)

        val scale = maxOf(opts.outWidth, opts.outHeight)
            .let { if (it > MAX_IMG_SIDE) it / MAX_IMG_SIDE else 1 }

        val finalOpts = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, finalOpts)
            ?: return "[Image could not be decoded]"

        val format = if (mimeType == "image/png") Bitmap.CompressFormat.PNG
                     else Bitmap.CompressFormat.JPEG
        val out = ByteArrayOutputStream()
        bitmap.compress(format, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Best-effort PDF text extraction.  Parses raw content streams looking for
     * BT…ET blocks and Tj / TJ operators — works for most PDFs produced by modern tools.
     */
    private fun extractPdfText(uri: Uri): String {
        // First try to count pages via PdfRenderer
        val pageCount = try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
            if (fd != null) {
                val r = PdfRenderer(fd)
                val n = r.pageCount
                r.close(); fd.close()
                n
            } else 0
        } catch (_: Exception) { 0 }

        // Parse raw bytes for text operators
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                ?: return "[PDF: could not open]"
            val content = String(bytes, Charsets.ISO_8859_1)
            val sb = StringBuilder()

            val btEt = Regex("BT.*?ET", setOf(RegexOption.DOT_MATCHES_ALL))
            val tjOp = Regex("""\(([^)]*)\)\s*Tj|\[([^\]]*)\]\s*TJ""")

            btEt.findAll(content).take(500).forEach { block ->
                tjOp.findAll(block.value).forEach { m ->
                    val text = (m.groupValues[1] + m.groupValues[2])
                        .replace("\\n", "\n").replace("\\r", "\r")
                        .replace("\\t", "\t")
                    sb.append(text)
                }
                sb.append("\n")
            }

            val extracted = sb.toString().trim()
            val header = if (pageCount > 0) "[PDF: $pageCount page${if (pageCount > 1) "s" else ""}]\n\n" else "[PDF]\n\n"
            if (extracted.length < 30) {
                "${header}[Text could not be extracted from this PDF. The document may be scanned or image-based.]"
            } else {
                header + extracted.take(MAX_FILE_TEXT)
            }
        } catch (e: Exception) {
            "[PDF: could not extract text — ${e.message}]"
        }
    }

    // ── Send message ──────────────────────────────────────────────────────────

    /**
     * @param text           The user's typed text (may be empty if only attaching a file).
     * @param quotedMessage  Message being replied to (WhatsApp-style quote). Its excerpt is
     *                       prepended to the context so the AI can answer in reference.
     */
    fun sendMessage(text: String, quotedMessage: ChatMessageEntity? = null) {
        val attachment = _pendingAttachment.value
        _pendingAttachment.value = null

        val hasContent = text.isNotBlank() || attachment != null
        if (!hasContent || _isTyping.value) return

        viewModelScope.launch {
            val cfg = settingsDataStore.settings.first()

            // Ensure session exists
            val sessionTitle = text.ifBlank { attachment?.name ?: "File" }
                .take(50).trimEnd().let { if (it.length == 50) "$it…" else it }

            val sessionId = _currentSessionId.value ?: run {
                val newId = chatDao.insertSession(
                    ChatSessionEntity(
                        title    = sessionTitle,
                        provider = _selectedProvider.value.id
                    )
                )
                _currentSessionId.value = newId
                observeMessages(newId)
                newId
            }

            val historySnapshot = _messages.value

            // ── Build visible user message (what gets stored in DB) ────────────
            val displayText = buildString {
                if (attachment != null) {
                    val icon = when {
                        attachment.mimeType.startsWith("image/") -> "📷"
                        attachment.mimeType == "application/pdf" -> "📄"
                        else -> "📎"
                    }
                    append("$icon ${attachment.name}\n")
                }
                if (quotedMessage != null) {
                    val who = if (quotedMessage.role == "assistant") "AI" else "Me"
                    val excerpt = quotedMessage.content.take(80)
                    val ellipsis = if (quotedMessage.content.length > 80) "…" else ""
                    append("↩ $who: \"${excerpt.trimEnd()}$ellipsis\"\n")
                }
                if (text.isNotBlank()) append(text)
            }.trim()

            // ── Build AI context message (richer, not stored visually) ─────────
            val aiContextText = buildString {
                if (quotedMessage != null) {
                    val who = if (quotedMessage.role == "assistant") "AI response" else "user message"
                    appendLine("[Replying to this $who: \"${quotedMessage.content.take(400).trim()}\"]")
                    appendLine()
                }
                when {
                    attachment?.imageBase64 != null -> {
                        appendLine("[User attached image: ${attachment.name}]")
                        appendLine()
                    }
                    attachment?.extractedText != null -> {
                        val typeLabel = when {
                            attachment.mimeType == "application/pdf"    -> "PDF"
                            attachment.mimeType.startsWith("text/")     -> "text file"
                            else                                         -> "file"
                        }
                        appendLine("[User attached $typeLabel: ${attachment.name}]")
                        appendLine("--- FILE CONTENT START ---")
                        appendLine(attachment.extractedText)
                        appendLine("--- FILE CONTENT END ---")
                        appendLine()
                    }
                }
                if (text.isNotBlank()) append(text)
                else if (attachment != null) append("Please read and summarise the attached ${attachment.name}.")
            }.trim()

            // Persist display version to DB
            chatDao.insertMessage(ChatMessageEntity(
                sessionId = sessionId,
                role      = "user",
                content   = displayText,
                provider  = _selectedProvider.value.id
            ))
            chatDao.incrementMessageCount(sessionId)

            // Build AI history: prior messages (snapshot) + the new user message
            val history = buildList {
                add(AiChatClient.Message("system", SYSTEM_PROMPT))
                historySnapshot.takeLast(23).forEach { m ->
                    add(AiChatClient.Message(m.role, m.content))
                }
                add(AiChatClient.Message("user", aiContextText))
            }

            _isTyping.value = true
            _typingText.value = ""

            val result = aiChatClient.chat(
                messages         = history,
                provider         = _selectedProvider.value,
                groqApiKey       = cfg.groqApiKey,
                openAiApiKey     = cfg.openAiApiKey,
                onDeviceProvider = aiProviderManager.onDeviceProvider,
                imageBase64      = attachment?.imageBase64,
                imageMimeType    = attachment?.mimeType ?: "image/jpeg"
            )

            _isTyping.value = false

            when (result) {
                is ChatApiResult.Success -> {
                    val full = result.content
                    var displayed = ""
                    for (char in full) {
                        displayed += char
                        _typingText.value = displayed
                        delay(8)
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

    private val _isAutoSpeaking   = MutableStateFlow(false)
    val isAutoSpeaking: StateFlow<Boolean> = _isAutoSpeaking.asStateFlow()

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
                            _isAutoSpeaking.value    = false
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
        _isAutoSpeaking.value = false
        speakQueue    = listOf(message)
        pausedAtIndex = 0
        _speakTotal.value = 1
        _ttsActive.value  = true
        enqueueFrom(0)
    }

    fun autoSpeakMessage(message: ChatMessageEntity) {
        if (message.role != "assistant" || message.isError) return
        initTts()
        if (_ttsActive.value) return
        rawStop()
        speakQueue         = listOf(message)
        pausedAtIndex      = 0
        _speakTotal.value  = 1
        _isAutoSpeaking.value = true
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
        _isAutoSpeaking.value    = false
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
