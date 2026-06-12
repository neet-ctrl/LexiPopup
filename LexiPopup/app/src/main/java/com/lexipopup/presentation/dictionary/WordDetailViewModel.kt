package com.lexipopup.presentation.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.domain.repositories.VocabularyRepository
import com.lexipopup.domain.usecases.LookupWordUseCase
import com.lexipopup.utils.ModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    private val lookupWord: LookupWordUseCase,
    private val repo: DictionaryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val modeManager: ModeManager
) : ViewModel() {

    private val _wordEntry = MutableStateFlow<WordEntry?>(null)
    val wordEntry: StateFlow<WordEntry?> = _wordEntry.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _addedToFlashcards = MutableStateFlow(false)
    val addedToFlashcards: StateFlow<Boolean> = _addedToFlashcards.asStateFlow()

    private val _showNoteDialog = MutableStateFlow(false)
    val showNoteDialog: StateFlow<Boolean> = _showNoteDialog.asStateFlow()

    private val _noteText = MutableStateFlow("")
    val noteText: StateFlow<String> = _noteText.asStateFlow()

    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    fun load(word: String) {
        if (_wordEntry.value?.word == word) return
        viewModelScope.launch {
            _isLoading.value = true
            val currentMode = modeManager.currentMode.value
            val entry = lookupWord(word, currentMode).getOrNull()
            _wordEntry.value = entry
            _isLoading.value = false
            if (entry != null) {
                vocabularyRepository.recordSearch(word, "LexiPopup", currentMode)
            }
        }
    }

    fun toggleFavorite() {
        val w = _wordEntry.value ?: return
        viewModelScope.launch {
            repo.toggleFavorite(w.word, modeManager.currentMode.value)
            _wordEntry.value = w.copy(isFavorite = !w.isFavorite)
            _snackMessage.value = if (!w.isFavorite) "Added to favorites ⭐" else "Removed from favorites"
        }
    }

    fun openNoteDialog() {
        _noteText.value = _wordEntry.value?.userNote ?: ""
        _showNoteDialog.value = true
    }

    fun closeNoteDialog() { _showNoteDialog.value = false }

    fun onNoteTextChange(text: String) { _noteText.value = text }

    fun saveNote() {
        val w = _wordEntry.value ?: return
        viewModelScope.launch {
            repo.saveNote(w.word, _noteText.value, modeManager.currentMode.value)
            _wordEntry.value = w.copy(userNote = _noteText.value)
            _showNoteDialog.value = false
            _snackMessage.value = "Note saved ✓"
        }
    }

    fun clearSnack() { _snackMessage.value = null }
}
