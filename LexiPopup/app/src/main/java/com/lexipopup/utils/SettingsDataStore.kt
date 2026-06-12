package com.lexipopup.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lexipopup.domain.models.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val SHOW_PRONUNCIATION = booleanPreferencesKey("show_pronunciation")
        val SHOW_POS = booleanPreferencesKey("show_pos")
        val SHOW_DETAILED = booleanPreferencesKey("show_detailed")
        val SHOW_HINDI = booleanPreferencesKey("show_hindi")
        val SHOW_EXAMPLE = booleanPreferencesKey("show_example")
        val SHOW_SYNONYMS = booleanPreferencesKey("show_synonyms")
        val SHOW_ANTONYMS = booleanPreferencesKey("show_antonyms")
        val SHOW_ETYMOLOGY = booleanPreferencesKey("show_etymology")
        val SHOW_DIFFICULTY = booleanPreferencesKey("show_difficulty")
        val SHOW_FREQUENCY = booleanPreferencesKey("show_frequency")
        val SHOW_COPY = booleanPreferencesKey("show_copy")
        val SHOW_SPEAK_WORD = booleanPreferencesKey("show_speak_word")
        val SHOW_SPEAK_MEANING = booleanPreferencesKey("show_speak_meaning")
        val SHOW_TRANSLATE = booleanPreferencesKey("show_translate")
        val SHOW_SHARE = booleanPreferencesKey("show_share")
        val SHOW_SAVE_NOTE = booleanPreferencesKey("show_save_note")
        val SHOW_FAVORITE = booleanPreferencesKey("show_favorite")
        val SHOW_FULL_DETAILS = booleanPreferencesKey("show_full_details")
        val ENABLE_DRAGGING = booleanPreferencesKey("enable_dragging")
        val ENABLE_RESIZING = booleanPreferencesKey("enable_resizing")
        val ENABLE_BUBBLE = booleanPreferencesKey("enable_bubble")
        val AUTO_CLOSE_SECONDS = intPreferencesKey("auto_close_seconds")
        val AUTO_CLOSE = booleanPreferencesKey("auto_close_enabled")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val TRACK_DAILY = booleanPreferencesKey("track_daily")
        val AUTO_FLASHCARDS = booleanPreferencesKey("auto_flashcards")
        val USE_DARK_MODE = booleanPreferencesKey("dark_mode")
        val USE_SYSTEM_THEME = booleanPreferencesKey("system_theme")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        // AI — legacy OpenAI key
        val OPEN_AI_KEY = stringPreferencesKey("open_ai_key")

        // AI — dual provider system
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val HYBRID_AUTO_SELECT = booleanPreferencesKey("hybrid_auto_select")
        val HYBRID_SHOW_COMPARISON = booleanPreferencesKey("hybrid_show_comparison")
        val ON_DEVICE_MODEL_ID = stringPreferencesKey("on_device_model_id")
        val HINDI_DISCLAIMER_SHOWN = booleanPreferencesKey("hindi_disclaimer_shown")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            showPronunciation = prefs[SHOW_PRONUNCIATION] ?: true,
            showPartOfSpeech = prefs[SHOW_POS] ?: true,
            showDetailedMeaning = prefs[SHOW_DETAILED] ?: true,
            showHindiMeaning = prefs[SHOW_HINDI] ?: true,
            showExampleSentence = prefs[SHOW_EXAMPLE] ?: true,
            showSynonyms = prefs[SHOW_SYNONYMS] ?: true,
            showAntonyms = prefs[SHOW_ANTONYMS] ?: true,
            showEtymology = prefs[SHOW_ETYMOLOGY] ?: false,
            showDifficultyBadge = prefs[SHOW_DIFFICULTY] ?: true,
            showFrequencyMeter = prefs[SHOW_FREQUENCY] ?: true,
            showCopyButton = prefs[SHOW_COPY] ?: true,
            showSpeakWordButton = prefs[SHOW_SPEAK_WORD] ?: true,
            showSpeakMeaningButton = prefs[SHOW_SPEAK_MEANING] ?: true,
            showTranslateButton = prefs[SHOW_TRANSLATE] ?: true,
            showShareButton = prefs[SHOW_SHARE] ?: true,
            showSaveNoteButton = prefs[SHOW_SAVE_NOTE] ?: true,
            showFavoriteButton = prefs[SHOW_FAVORITE] ?: true,
            showFullDetailsButton = prefs[SHOW_FULL_DETAILS] ?: true,
            enableDragging = prefs[ENABLE_DRAGGING] ?: true,
            enableResizing = prefs[ENABLE_RESIZING] ?: true,
            enableCollapseTooBubble = prefs[ENABLE_BUBBLE] ?: true,
            autoCloseSeconds = prefs[AUTO_CLOSE_SECONDS] ?: 0,
            showPersistentNotification = prefs[SHOW_NOTIFICATION] ?: true,
            saveSearchHistory = prefs[SAVE_HISTORY] ?: true,
            trackDailyWords = prefs[TRACK_DAILY] ?: true,
            autoGenerateFlashcards = prefs[AUTO_FLASHCARDS] ?: true,
            useDarkMode = prefs[USE_DARK_MODE] ?: false,
            useSystemTheme = prefs[USE_SYSTEM_THEME] ?: true,
            openAiApiKey = prefs[OPEN_AI_KEY] ?: "",
            groqApiKey = prefs[GROQ_API_KEY] ?: "",
            aiProviderName = prefs[AI_PROVIDER] ?: "groq",
            hybridAutoSelectBest = prefs[HYBRID_AUTO_SELECT] ?: true,
            hybridShowComparison = prefs[HYBRID_SHOW_COMPARISON] ?: true,
            onDeviceModelId = prefs[ON_DEVICE_MODEL_ID] ?: "gemma-2b-tiny"
        )
    }

    suspend fun update(block: suspend (MutablePreferences) -> Unit) {
        dataStore.edit { prefs -> block(prefs) }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs[SHOW_PRONUNCIATION] = true
            prefs[SHOW_POS] = true
            prefs[SHOW_DETAILED] = true
            prefs[SHOW_HINDI] = true
            prefs[SHOW_EXAMPLE] = true
            prefs[SHOW_SYNONYMS] = true
            prefs[SHOW_ANTONYMS] = true
            prefs[SHOW_ETYMOLOGY] = false
            prefs[SHOW_DIFFICULTY] = true
            prefs[SHOW_FREQUENCY] = true
            prefs[SHOW_COPY] = true
            prefs[SHOW_SPEAK_WORD] = true
            prefs[SHOW_SPEAK_MEANING] = true
            prefs[SHOW_TRANSLATE] = true
            prefs[SHOW_SHARE] = true
            prefs[SHOW_SAVE_NOTE] = true
            prefs[SHOW_FAVORITE] = true
            prefs[SHOW_FULL_DETAILS] = true
            prefs[ENABLE_DRAGGING] = true
            prefs[ENABLE_RESIZING] = true
            prefs[ENABLE_BUBBLE] = true
            prefs[AUTO_CLOSE_SECONDS] = 0
            prefs[SHOW_NOTIFICATION] = true
            prefs[SAVE_HISTORY] = true
            prefs[TRACK_DAILY] = true
            prefs[AUTO_FLASHCARDS] = true
            prefs[USE_DARK_MODE] = false
            prefs[USE_SYSTEM_THEME] = true
        }
    }

    val isOnboardingDone: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_DONE] ?: false }

    suspend fun markOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    val isHindiDisclaimerShown: Flow<Boolean> = dataStore.data.map { it[HINDI_DISCLAIMER_SHOWN] ?: false }

    suspend fun markHindiDisclaimerShown() {
        dataStore.edit { it[HINDI_DISCLAIMER_SHOWN] = true }
    }
}
