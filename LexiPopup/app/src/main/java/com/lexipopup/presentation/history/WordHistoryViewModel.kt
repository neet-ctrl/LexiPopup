package com.lexipopup.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class WordHistoryViewModel @Inject constructor(
    private val repository: DictionaryRepository
) : ViewModel() {

    val historyWords: StateFlow<List<WordEntry>> = repository.getWordHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyCount: StateFlow<Int> = repository.getWordHistoryCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
