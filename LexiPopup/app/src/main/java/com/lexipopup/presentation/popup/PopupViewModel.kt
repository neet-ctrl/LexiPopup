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
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<PopupUiState>(PopupUiState.Idle)
    val uiState: StateFlow<PopupUiState> = _uiState.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isBubbleMode = MutableStateFlow(false)
    val isBubbleMode: StateFlow<Boolean> = _isBubbleMode.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var autoCloseJob: Job? = null
    private var searchJob: Job? = null
    private var openTimestamp = System.currentTimeMillis()

    fun lookupWord(word: String, sourceApp: String = "LexiPopup") {
        viewModelScope.launch {
            _uiState.value = PopupUiState.Loading
            openTimestamp = System.currentTimeMillis()
            val result = lookupWordUseCase(word)
            result.fold(
                onSuccess = {
                    _uiState.value = PopupUiState.Success(it)
                    if (settings.value.saveSearchHistory) {
                        vocabularyRepository.recordSearch(word, sourceApp)
                    }
                    if (settings.value.autoGenerateFlashcards) {
                        vocabularyRepository.createFlashcard(
                            word, word, it.shortMeaning.ifEmpty { it.detailedMeaning.take(100) }
                        )
                    }
                    scheduleAutoClose()
                },
                onFailure = {
                    _uiState.value = PopupUiState.Error("\"$word\" not found. Try checking the spelling.")
                }
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200)
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
                settingsDataStore.update { /* no-op — toggle handled via repo */ }
            }
        }
    }

    fun speakWord(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) ttsHelper.speak(state.entry.word)
    }

    fun speakMeaning(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) ttsHelper.speak(state.entry.shortMeaning, 0.85f)
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
            val uri = Uri.parse("https://translate.google.com/?text=${Uri.encode(state.entry.word)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun shareWord(context: Context) {
        val state = _uiState.value
        if (state is PopupUiState.Success) {
            val text = "${state.entry.word}: ${state.entry.shortMeaning}"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
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
