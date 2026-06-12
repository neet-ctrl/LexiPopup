package com.lexipopup.presentation.popup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.LAYER_CACHE
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.VocabularyRepository
import com.lexipopup.domain.usecases.LookupWordUseCase
import com.lexipopup.utils.ModeManager
import com.lexipopup.utils.NotificationHelper
import com.lexipopup.utils.SettingsDataStore
import com.lexipopup.utils.TtsHelper
import com.lexipopup.utils.ai.AiProviderManager
import com.lexipopup.utils.ai.HybridAiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PopupUiState {
    object Idle : PopupUiState()
    object Loading : PopupUiState()
    data class Success(val entry: WordEntry) : PopupUiState()
    data class Error(val message: String) : PopupUiState()
    object ManualSearch : PopupUiState()
}

@HiltViewModel
class PopupViewModel @Inject constructor(
    private val lookupWordUseCase: LookupWordUseCase,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsDataStore: SettingsDataStore,
    private val ttsHelper: TtsHelper,
    private val notificationHelper: NotificationHelper,
    private val aiProviderManager: AiProviderManager,
    val modeManager: ModeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<PopupUiState>(PopupUiState.Idle)
    val uiState: StateFlow<PopupUiState> = _uiState.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    // ── Mode selection sheet (Moon+ Reader flow) ───────────────────────────────
    /** When set, popup should show mode selection sheet before looking up this word */
    private val _pendingWord = MutableStateFlow<String?>(null)
    val pendingWord: StateFlow<String?> = _pendingWord.asStateFlow()

    private val _showModeSelection = MutableStateFlow(false)
    val showModeSelection: StateFlow<Boolean> = _showModeSelection.asStateFlow()

    /** Current active mode in popup (can differ from app-level mode during mode selection) */
    val activeMode: StateFlow<AppMode> = modeManager.currentMode

    // ── Layer picker ──────────────────────────────────────────────────────────
    private val _showLayerPicker = MutableStateFlow(false)
    val showLayerPicker: StateFlow<Boolean> = _showLayerPicker.asStateFlow()

    private val _forcingLayerId = MutableStateFlow<String?>(null)
    val forcingLayerId: StateFlow<String?> = _forcingLayerId.asStateFlow()

    val activeLayers: StateFlow<List<String>> = settingsDataStore.layerSystemConfig
        .map { cfg -> cfg.activeLayers().filter { it != LAYER_CACHE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hybridAiResult: StateFlow<HybridAiResult?> = aiProviderManager.lastHybridResult

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isBubbleMode = MutableStateFlow(false)
    val isBubbleMode: StateFlow<Boolean> = _isBubbleMode.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var autoCloseJob: Job? = null
    private var searchJob: Job? = null

    fun lookupWord(word: String, sourceApp: String = "LexiPopup", mode: AppMode? = null) {
        val effectiveMode = mode ?: modeManager.currentMode.value
        val clean = word.trim().lowercase()
            .replace(Regex("[^a-z'\\-]"), "")
            .trimStart('\'', '-').trimEnd('\'', '-')
        if (clean.isBlank()) {
            _uiState.value = PopupUiState.Error("Word not recognized. Please enter a valid word.")
            return
        }

        viewModelScope.launch {
            _uiState.value = PopupUiState.Loading
            val result = lookupWordUseCase(clean, effectiveMode)
            result.fold(
                onSuccess = { entry ->
                    _uiState.value = PopupUiState.Success(entry)
                    if (settings.value.saveSearchHistory) {
                        vocabularyRepository.recordSearch(clean, sourceApp, effectiveMode)
                    }
                    if (settings.value.autoGenerateFlashcards) {
                        vocabularyRepository.createFlashcard(clean, clean, entry.shortMeaning.take(100))
                    }
                    scheduleAutoClose()
                },
                onFailure = {
                    _uiState.value = PopupUiState.Error("\"$clean\" not found. No offline data and no internet. Try another word.")
                }
            )
        }
    }

    /** Called from PopupActivity when PROCESS_TEXT arrives — shows mode selection sheet first. */
    fun requestModeSelection(word: String) {
        val settings = settings.value
        val englishEnabled = settings.englishModeEnabled
        val bioEnabled = settings.biologyModeEnabled
        when {
            !englishEnabled && bioEnabled -> {
                // Only biology available — skip the sheet
                lookupWord(word, mode = AppMode.BIOLOGY)
            }
            englishEnabled && !bioEnabled -> {
                // Only English available — skip the sheet
                lookupWord(word, mode = AppMode.ENGLISH)
            }
            else -> {
                _pendingWord.value = word
                _showModeSelection.value = true
            }
        }
    }

    fun confirmModeSelection(mode: AppMode) {
        val word = _pendingWord.value ?: return
        _showModeSelection.value = false
        _pendingWord.value = null
        modeManager.setMode(mode)
        lookupWord(word, mode = mode)
    }

    fun dismissModeSelection() {
        _showModeSelection.value = false
        _pendingWord.value = null
        _uiState.value = PopupUiState.ManualSearch
    }

    fun switchMode(mode: AppMode) {
        modeManager.setMode(mode)
        // Re-lookup current word in new mode if we have a result
        val currentWord = (_uiState.value as? PopupUiState.Success)?.entry?.word
        if (!currentWord.isNullOrBlank()) {
            lookupWord(currentWord, mode = mode)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.contains(" ")) {
            _uiState.value = PopupUiState.Error("Only single words are supported. Type one word at a time.")
            return
        }
        searchJob = viewModelScope.launch {
            delay(180)
            if (query.length >= 2) {
                _suggestions.value = lookupWordUseCase.suggestions(query, modeManager.currentMode.value)
            } else {
                _suggestions.value = emptyList()
            }
        }
    }

    fun setManualSearchMode() {
        _uiState.value = PopupUiState.ManualSearch
    }

    fun toggleFavorite() {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            viewModelScope.launch {
                val mode = AppMode.fromId(state.entry.mode)
                vocabularyRepository.toggleFavorite(state.entry.word, mode)
                val refreshed = lookupWordUseCase(state.entry.word, mode)
                refreshed.onSuccess { _uiState.value = PopupUiState.Success(it) }
            }
        }
    }

    fun saveNote(note: String) {
        val state = _uiState.value
        if (state is PopupUiState.Success && note.isNotBlank()) {
            viewModelScope.launch {
                vocabularyRepository.saveNote(state.entry.word, note, AppMode.fromId(state.entry.mode))
            }
        }
    }

    fun speakWord(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            if (!ttsHelper.speak(state.entry.word)) ttsHelper.openTtsSettings()
        }
    }

    fun speakMeaning(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            if (!ttsHelper.speak(state.entry.shortMeaning, 0.85f)) ttsHelper.openTtsSettings()
        }
    }

    fun copyToClipboard(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(state.entry.word, state.entry.shortMeaning))
        }
    }

    fun openTranslate(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            val meaningText = buildString {
                append(state.entry.shortMeaning)
                if (state.entry.detailedMeaning.isNotBlank() && state.entry.detailedMeaning != state.entry.shortMeaning) {
                    append(". "); append(state.entry.detailedMeaning)
                }
            }.trim()
            val uri = Uri.parse("https://translate.google.com/?text=${Uri.encode(meaningText)}&sl=en&tl=hi")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    fun openSearchWeb(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            val query = if (state.entry.isBiology()) "${state.entry.word} biology" else "${state.entry.word} definition"
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    fun openInBrowser(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(state.entry.word + " meaning")}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    fun addFlashcard() {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            viewModelScope.launch {
                val mode = if (state.entry.isBiology()) AppMode.BIOLOGY.id else AppMode.ENGLISH.id
                vocabularyRepository.createFlashcard(
                    state.entry.word,
                    state.entry.word,
                    state.entry.shortMeaning.take(100),
                    mode
                )
            }
        }
    }

    fun saveWindowPosition(x: Float, y: Float) {
        viewModelScope.launch {
            settingsDataStore.update { prefs ->
                prefs[SettingsDataStore.POPUP_LAST_X] = x
                prefs[SettingsDataStore.POPUP_LAST_Y] = y
            }
        }
    }

    fun saveWindowSize(widthFraction: Float, heightFraction: Float) {
        viewModelScope.launch {
            settingsDataStore.update { prefs ->
                prefs[SettingsDataStore.POPUP_WIDTH_FRACTION] = widthFraction
                prefs[SettingsDataStore.POPUP_HEIGHT_FRACTION] = heightFraction
            }
        }
    }

    fun shareWord(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            val text = if (state.entry.isBiology()) buildString {
                append("🧬 ${state.entry.word}: ${state.entry.shortMeaning}")
                if (state.entry.hindiMeaning.isNotBlank()) append("\nहिंदी: ${state.entry.hindiMeaning}")
                val bioData = state.entry.biologyData()
                if (bioData.functions.isNotEmpty()) append("\nFunctions: ${bioData.functions.take(2).joinToString("; ")}")
                append("\n\n— via LexiPopup (Biology Mode)")
            } else buildString {
                append("${state.entry.word}: ${state.entry.shortMeaning}")
                if (state.entry.hindiMeaning.isNotBlank()) append("\nहिंदी: ${state.entry.hindiMeaning}")
                if (state.entry.exampleSentence.isNotBlank()) append("\nExample: \"${state.entry.exampleSentence}\"")
                append("\n\n— via LexiPopup")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share via").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    fun showLayerPicker() { _showLayerPicker.value = true }
    fun hideLayerPicker() { _showLayerPicker.value = false }

    fun lookupWithLayer(layerId: String) {
        val word = (_uiState.value as? PopupUiState.Success)?.entry?.word ?: return
        viewModelScope.launch {
            _forcingLayerId.value = layerId
            val mode = modeManager.currentMode.value
            val result = lookupWordUseCase.invokeWithLayer(word, layerId, mode)
            result.fold(
                onSuccess = { entry ->
                    _uiState.value = PopupUiState.Success(entry)
                    _showLayerPicker.value = false
                    scheduleAutoClose()
                },
                onFailure = {
                    _uiState.value = PopupUiState.Error("\"$word\" not found via $layerId. Try another layer.")
                    _showLayerPicker.value = false
                }
            )
            _forcingLayerId.value = null
        }
    }

    fun toggleBubble() { _isBubbleMode.value = !_isBubbleMode.value }

    fun toggleNotification(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) notificationHelper.showPersistentNotification()
            else notificationHelper.dismissPersistentNotification()
            settingsDataStore.update { prefs ->
                prefs[SettingsDataStore.SHOW_NOTIFICATION] = enabled
            }
        }
    }

    private fun scheduleAutoClose() {
        autoCloseJob?.cancel()
        val seconds = settings.value.autoCloseSeconds
        if (seconds > 0) {
            autoCloseJob = viewModelScope.launch {
                delay(seconds * 1000L)
                _uiState.value = PopupUiState.Idle
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.stop()
    }
}
