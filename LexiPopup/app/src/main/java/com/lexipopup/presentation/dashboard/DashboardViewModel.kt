package com.lexipopup.presentation.dashboard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.domain.repositories.VocabularyRepository
import com.lexipopup.utils.ExportFormat
import com.lexipopup.utils.ExportHelper
import com.lexipopup.utils.NotificationHelper
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsDataStore: SettingsDataStore,
    private val notificationHelper: NotificationHelper,
    private val exportHelper: ExportHelper,
    private val gson: Gson
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val todayCount: StateFlow<Int> = vocabularyRepository.getTodayCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val recentWords: StateFlow<List<WordEntry>> = dictionaryRepository.getRecentWords(50)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favorites: StateFlow<List<WordEntry>> = dictionaryRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostSearched: StateFlow<List<Pair<String, Int>>> = vocabularyRepository.getMostSearchedWords(10)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val weeklyStats: StateFlow<List<Pair<String, Int>>> = vocabularyRepository.getWeeklyStats()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activityHeatmapData: StateFlow<Map<LocalDate, Int>> = vocabularyRepository.getActivityHeatmap(84)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val difficultyDistribution: StateFlow<Map<Int, Int>> = flow {
        emit(dictionaryRepository.getDifficultyDistribution())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun updateSetting(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.update { prefs -> prefs[key] = value }
            if (key == SettingsDataStore.SHOW_NOTIFICATION) {
                if (value) notificationHelper.showPersistentNotification()
                else notificationHelper.dismissPersistentNotification()
            }
        }
    }

    fun resetSettings() {
        viewModelScope.launch { settingsDataStore.resetToDefaults() }
    }

    fun removeFavorite(word: String) {
        viewModelScope.launch { vocabularyRepository.toggleFavorite(word) }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch {
            settingsDataStore.update { prefs -> prefs[SettingsDataStore.OPEN_AI_KEY] = key }
        }
    }

    /** Export all vocabulary as CSV/JSON/Anki and open share sheet */
    fun exportVocabulary(words: List<WordEntry>, format: ExportFormat, context: Context) {
        viewModelScope.launch {
            val uri = exportHelper.exportWords(words, format)
            val intent = exportHelper.shareExport(uri, format)
            context.startActivity(intent)
        }
    }

    /** Export current settings as JSON and return Uri for sharing */
    fun exportSettingsUri(): Uri? {
        return try {
            val json = gson.toJson(settings.value)
            exportHelper.exportSettingsJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
