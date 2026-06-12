package com.lexipopup.presentation.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.utils.ModeManager
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionaryBrowserViewModel @Inject constructor(
    private val repo: DictionaryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val modeManager: ModeManager
) : ViewModel() {

    val activeMode: StateFlow<AppMode> = modeManager.currentMode

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _searchResults = MutableStateFlow<List<WordEntry>>(emptyList())
    val searchResults: StateFlow<List<WordEntry>> = _searchResults.asStateFlow()

    private val _selectedLetter = MutableStateFlow("A")
    val selectedLetter: StateFlow<String> = _selectedLetter.asStateFlow()

    private val _browseWords = MutableStateFlow<List<WordEntry>>(emptyList())
    val browseWords: StateFlow<List<WordEntry>> = _browseWords.asStateFlow()

    private val _letterCount = MutableStateFlow(0)
    val letterCount: StateFlow<Int> = _letterCount.asStateFlow()

    private val _wordOfDay = MutableStateFlow<WordEntry?>(null)
    val wordOfDay: StateFlow<WordEntry?> = _wordOfDay.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortBy = MutableStateFlow("alpha")
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    private val _filterPos = MutableStateFlow("")
    val filterPos: StateFlow<String> = _filterPos.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Reload everything whenever mode changes (collect skips first emission to avoid
        // double-load, but we want the initial load too, so we just collect all).
        viewModelScope.launch {
            modeManager.currentMode.collect {
                clearSearch()
                refreshWordOfDay()
                loadLetter(_selectedLetter.value)
            }
        }
    }

    fun onSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _suggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            val mode = modeManager.currentMode.value
            delay(280)
            _isLoading.value = true
            _suggestions.value = repo.searchSuggestions(query, 6, mode)
            _searchResults.value = repo.searchWords(query, 60, mode)
            _isLoading.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _suggestions.value = emptyList()
    }

    fun selectLetter(letter: String) {
        _selectedLetter.value = letter
        loadLetter(letter)
    }

    private fun loadLetter(letter: String) {
        viewModelScope.launch {
            val mode = modeManager.currentMode.value
            _isLoading.value = true
            _letterCount.value = repo.countByLetter(letter, mode)
            _browseWords.value = repo.getWordsByLetter(
                letter, 60, 0, _sortBy.value, _filterPos.value, mode
            )
            _isLoading.value = false
        }
    }

    fun setSortBy(sort: String) {
        _sortBy.value = sort
        loadLetter(_selectedLetter.value)
    }

    fun setFilterPos(pos: String) {
        val next = if (_filterPos.value == pos) "" else pos
        _filterPos.value = next
        loadLetter(_selectedLetter.value)
    }

    fun refreshWordOfDay() {
        viewModelScope.launch {
            _wordOfDay.value = if (modeManager.currentMode.value == AppMode.BIOLOGY) {
                repo.getBiologyTermOfDay()
            } else {
                val s = settingsDataStore.settings.first()
                repo.getWordOfDay(s.wotdMode, s.wotdUserLevel)
            }
        }
    }
}
