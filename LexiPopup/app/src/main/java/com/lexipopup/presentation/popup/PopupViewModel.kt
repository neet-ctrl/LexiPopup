package com.lexipopup.presentation.popup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.VocabularyRepository
import com.lexipopup.domain.usecases.LookupWordUseCase
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
    private val aiProviderManager: AiProviderManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<PopupUiState>(PopupUiState.Idle)
    val uiState: StateFlow<PopupUiState> = _uiState.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /** Non-null only when provider is Hybrid and both AI calls returned results. */
    val hybridAiResult: StateFlow<HybridAiResult?> = aiProviderManager.lastHybridResult

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isBubbleMode = MutableStateFlow(false)
    val isBubbleMode: StateFlow<Boolean> = _isBubbleMode.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var autoCloseJob: Job? = null
    private var searchJob: Job? = null

    fun lookupWord(word: String, sourceApp: String = "LexiPopup") {
        // Single-word extraction
        val clean = word.trim().lowercase()
            .replace(Regex("[^a-z'\\-]"), "")
            .trimStart('\'', '-').trimEnd('\'', '-')
        if (clean.isBlank()) {
            _uiState.value = PopupUiState.Error("Word not recognized. Please enter a valid word.")
            return
        }

        viewModelScope.launch {
            _uiState.value = PopupUiState.Loading
            val result = lookupWordUseCase(clean)
            result.fold(
                onSuccess = { entry ->
                    _uiState.value = PopupUiState.Success(entry)
                    if (settings.value.saveSearchHistory) {
                        vocabularyRepository.recordSearch(clean, sourceApp)
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
                _suggestions.value = lookupWordUseCase.suggestions(query)
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
                vocabularyRepository.toggleFavorite(state.entry.word)
                // Reload to reflect new favorite state
                val refreshed = lookupWordUseCase(state.entry.word)
                refreshed.onSuccess { _uiState.value = PopupUiState.Success(it) }
            }
        }
    }

    fun saveNote(note: String) {
        val state = _uiState.value
        if (state is PopupUiState.Success && note.isNotBlank()) {
            viewModelScope.launch {
                vocabularyRepository.saveNote(state.entry.word, note)
            }
        }
    }

    fun speakWord(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            // If TTS engine is not ready, send the user to TTS settings to install one.
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
            val uri = Uri.parse("https://translate.google.com/?text=${Uri.encode(state.entry.word)}&sl=en&tl=hi")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    fun shareWord(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            val text = buildString {
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
