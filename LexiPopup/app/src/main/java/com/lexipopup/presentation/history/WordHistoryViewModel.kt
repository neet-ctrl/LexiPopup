package com.lexipopup.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.utils.ModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class WordHistoryViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val modeManager: ModeManager
) : ViewModel() {

    val activeMode: StateFlow<AppMode> = modeManager.currentMode

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyWords: StateFlow<List<WordEntry>> = modeManager.currentMode
        .flatMapLatest { mode -> repository.getWordHistory(mode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyCount: StateFlow<Int> = modeManager.currentMode
        .flatMapLatest { mode -> repository.getWordHistoryCount(mode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
