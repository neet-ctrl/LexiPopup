package com.lexipopup.presentation.random

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.data.local.dao.RandomWordDao
import com.lexipopup.data.local.entities.RandomWordEntity
import com.lexipopup.utils.SettingsDataStore
import com.lexipopup.workers.RandomWordWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RandomWordSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val randomWordDao: RandomWordDao
) : ViewModel() {

    val settings = settingsDataStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.lexipopup.domain.models.AppSettings()
    )

    val discoveredWords: StateFlow<List<RandomWordEntity>> = randomWordDao
        .getDiscoveredWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _fetchTriggered = MutableStateFlow(false)
    val fetchTriggered: StateFlow<Boolean> = _fetchTriggered.asStateFlow()

    // ── Setting updaters ──────────────────────────────────────────────────────

    fun setProvider(value: String) = viewModelScope.launch {
        settingsDataStore.update { it[SettingsDataStore.RANDOM_WORD_PROVIDER] = value }
    }

    fun setDifficulty(value: String) = viewModelScope.launch {
        settingsDataStore.update { it[SettingsDataStore.RANDOM_WORD_DIFFICULTY] = value }
    }

    fun setTopics(value: String) = viewModelScope.launch {
        settingsDataStore.update { it[SettingsDataStore.RANDOM_WORD_TOPICS] = value }
    }

    fun setPrefetchCount(value: Int) = viewModelScope.launch {
        settingsDataStore.update { it[SettingsDataStore.RANDOM_WORD_PREFETCH_COUNT] = value }
    }

    fun setAutoRefresh(value: Boolean) = viewModelScope.launch {
        settingsDataStore.update { it[SettingsDataStore.RANDOM_WORD_AUTO_REFRESH] = value }
        if (value) RandomWordWorker.schedulePeriodic(context)
        else RandomWordWorker.cancelPeriodic(context)
    }

    fun setShowHindi(value: Boolean) = viewModelScope.launch {
        settingsDataStore.update { it[SettingsDataStore.RANDOM_WORD_SHOW_HINDI] = value }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /** Trigger an immediate one-time word fetch */
    fun triggerFetch() {
        RandomWordWorker.scheduleOneTime(context)
        _fetchTriggered.value = true
    }

    fun clearFetchTrigger() { _fetchTriggered.value = false }

    /** Wipe the unseen queue and fetch fresh words */
    fun regenerateQueue() = viewModelScope.launch {
        randomWordDao.clearQueue()
        RandomWordWorker.scheduleOneTime(context)
        _fetchTriggered.value = true
    }
}
