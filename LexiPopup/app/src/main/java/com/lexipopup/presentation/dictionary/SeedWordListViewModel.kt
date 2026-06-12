package com.lexipopup.presentation.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeedWordListViewModel @Inject constructor(
    private val repo: DictionaryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _words = MutableStateFlow<List<WordEntry>>(emptyList())
    val words: StateFlow<List<WordEntry>> = _words.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _totalCount.value = repo.getSeedWordCount()
        }
        loadWords("")
    }

    fun onSearch(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(250)
            _isLoading.value = true
            _words.value = repo.getSeedWords(query.trim(), 1000)
            _isLoading.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        loadWords("")
    }

    private fun loadWords(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _words.value = repo.getSeedWords(query, 1000)
            _isLoading.value = false
        }
    }
}
