package com.lexipopup.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.domain.repositories.VocabularyRepository
import com.lexipopup.utils.NotificationHelper
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsDataStore: SettingsDataStore,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val todayCount: StateFlow<Int> = vocabularyRepository.getTodayCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val recentWords: StateFlow<List<WordEntry>> = dictionaryRepository.getRecentWords(20)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favorites: StateFlow<List<WordEntry>> = dictionaryRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostSearched: StateFlow<List<Pair<String, Int>>> = vocabularyRepository.getMostSearchedWords(10)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val weeklyStats: StateFlow<List<Pair<String, Int>>> = vocabularyRepository.getWeeklyStats()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSetting(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.update { prefs -> prefs[key] = value }
            // Handle notification toggle
            if (key == SettingsDataStore.SHOW_NOTIFICATION) {
                if (value) notificationHelper.showPersistentNotification()
                else notificationHelper.dismissPersistentNotification()
            }
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            settingsDataStore.resetToDefaults()
        }
    }

    fun removeFavorite(word: String) {
        viewModelScope.launch { dictionaryRepository.toggleFavorite(word) }
    }
}
