package com.lexipopup.presentation.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionaryBrowserViewModel @Inject constructor(
    private val repo: DictionaryRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

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
        viewModelScope.launch {
            val s = settingsDataStore.settings.first()
            _wordOfDay.value = repo.getWordOfDay(s.wotdMode, s.wotdUserLevel)
        }
        loadLetter("A")
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
            delay(280)
            _isLoading.value = true
            _suggestions.value = repo.searchSuggestions(query, 6)
            _searchResults.value = repo.searchWords(query, 60)
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
            _isLoading.value = true
            _letterCount.value = repo.countByLetter(letter)
            _browseWords.value = repo.getWordsByLetter(
                letter, 60, 0, _sortBy.value, _filterPos.value
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
            val s = settingsDataStore.settings.first()
            _wordOfDay.value = repo.getWordOfDay(s.wotdMode, s.wotdUserLevel)
        }
    }
}
