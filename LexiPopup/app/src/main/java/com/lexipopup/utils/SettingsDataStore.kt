package com.lexipopup.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.LayerSystemConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {
    companion object {
        // ── English popup display ──────────────────────────────────────────────
        val SHOW_PRONUNCIATION     = booleanPreferencesKey("show_pronunciation")
        val SHOW_POS               = booleanPreferencesKey("show_pos")
        val SHOW_DETAILED          = booleanPreferencesKey("show_detailed")
        val SHOW_HINDI             = booleanPreferencesKey("show_hindi")
        val SHOW_EXAMPLE           = booleanPreferencesKey("show_example")
        val SHOW_SYNONYMS          = booleanPreferencesKey("show_synonyms")
        val SHOW_ANTONYMS          = booleanPreferencesKey("show_antonyms")
        val SHOW_ETYMOLOGY         = booleanPreferencesKey("show_etymology")
        val SHOW_DIFFICULTY        = booleanPreferencesKey("show_difficulty")
        val SHOW_FREQUENCY         = booleanPreferencesKey("show_frequency")
        val SHOW_COPY              = booleanPreferencesKey("show_copy")
        val SHOW_SPEAK_WORD        = booleanPreferencesKey("show_speak_word")
        val SHOW_SPEAK_MEANING     = booleanPreferencesKey("show_speak_meaning")
        val SHOW_TRANSLATE         = booleanPreferencesKey("show_translate")
        val SHOW_SHARE             = booleanPreferencesKey("show_share")
        val SHOW_SAVE_NOTE         = booleanPreferencesKey("show_save_note")
        val SHOW_FAVORITE          = booleanPreferencesKey("show_favorite")
        val SHOW_FULL_DETAILS      = booleanPreferencesKey("show_full_details")
        val ENABLE_DRAGGING        = booleanPreferencesKey("enable_dragging")
        val ENABLE_RESIZING        = booleanPreferencesKey("enable_resizing")
        val ENABLE_BUBBLE          = booleanPreferencesKey("enable_bubble")
        val ENABLE_EDGE_COLLAPSE   = booleanPreferencesKey("enable_edge_collapse")
        val AUTO_CLOSE_SECONDS     = intPreferencesKey("auto_close_seconds")
        val AUTO_CLOSE             = booleanPreferencesKey("auto_close_enabled")
        val SHOW_NOTIFICATION      = booleanPreferencesKey("show_notification")
        val SHOW_SEARCH_WEB        = booleanPreferencesKey("show_search_web")
        val SHOW_FLASHCARD_BTN     = booleanPreferencesKey("show_flashcard_btn")
        val SHOW_BROWSER_BTN       = booleanPreferencesKey("show_browser_btn")
        val BUTTON_ORDER           = stringPreferencesKey("button_order")
        val POPUP_WIDTH_FRACTION   = androidx.datastore.preferences.core.floatPreferencesKey("popup_width_fraction")
        val POPUP_HEIGHT_FRACTION  = androidx.datastore.preferences.core.floatPreferencesKey("popup_height_fraction")
        val POPUP_BG_ALPHA         = androidx.datastore.preferences.core.floatPreferencesKey("popup_bg_alpha")
        val POPUP_LAST_X           = androidx.datastore.preferences.core.floatPreferencesKey("popup_last_x")
        val POPUP_LAST_Y           = androidx.datastore.preferences.core.floatPreferencesKey("popup_last_y")
        val SAVE_HISTORY           = booleanPreferencesKey("save_history")
        val TRACK_DAILY            = booleanPreferencesKey("track_daily")
        val AUTO_FLASHCARDS        = booleanPreferencesKey("auto_flashcards")
        val USE_DARK_MODE          = booleanPreferencesKey("dark_mode")
        val USE_SYSTEM_THEME       = booleanPreferencesKey("system_theme")
        val ONBOARDING_DONE        = booleanPreferencesKey("onboarding_done")
        val OPEN_AI_KEY            = stringPreferencesKey("open_ai_key")
        val GROQ_API_KEY           = stringPreferencesKey("groq_api_key")
        val AI_PROVIDER            = stringPreferencesKey("ai_provider")
        val HYBRID_AUTO_SELECT     = booleanPreferencesKey("hybrid_auto_select")
        val HYBRID_SHOW_COMPARISON = booleanPreferencesKey("hybrid_show_comparison")
        val ON_DEVICE_MODEL_ID     = stringPreferencesKey("on_device_model_id")
        val HINDI_DISCLAIMER_SHOWN = booleanPreferencesKey("hindi_disclaimer_shown")

        // ── Word of the Day (English) ──────────────────────────────────────────
        val WOTD_MODE                     = stringPreferencesKey("wotd_mode")
        val WOTD_USER_LEVEL               = intPreferencesKey("wotd_user_level")
        val WOTD_NOTIFICATION_ENABLED     = booleanPreferencesKey("wotd_notification_enabled")
        val WOTD_NOTIFICATION_HOUR        = intPreferencesKey("wotd_notification_hour")

        // ── Layer System Config ────────────────────────────────────────────────
        val LAYER_SYSTEM_CONFIG = stringPreferencesKey("layer_system_config")

        // ── Mode enable / disable ──────────────────────────────────────────────
        val ENGLISH_MODE_ENABLED = booleanPreferencesKey("english_mode_enabled")
        val BIOLOGY_MODE_ENABLED = booleanPreferencesKey("biology_mode_enabled")

        // ── Biology popup display settings ─────────────────────────────────────
        val BIO_SHOW_PRONUNCIATION   = booleanPreferencesKey("bio_show_pronunciation")
        val BIO_SHOW_CATEGORY        = booleanPreferencesKey("bio_show_category")
        val BIO_SHOW_DEFINITION      = booleanPreferencesKey("bio_show_definition")
        val BIO_SHOW_HINDI           = booleanPreferencesKey("bio_show_hindi")
        val BIO_SHOW_EXAMPLE         = booleanPreferencesKey("bio_show_example")
        val BIO_SHOW_CLASSIFICATION  = booleanPreferencesKey("bio_show_classification")
        val BIO_SHOW_FUNCTIONS       = booleanPreferencesKey("bio_show_functions")
        val BIO_SHOW_STRUCTURE       = booleanPreferencesKey("bio_show_structure")
        val BIO_SHOW_RELATED_TERMS   = booleanPreferencesKey("bio_show_related_terms")
        val BIO_SHOW_DISEASES        = booleanPreferencesKey("bio_show_diseases")
        val BIO_SHOW_ETYMOLOGY       = booleanPreferencesKey("bio_show_etymology")
        val BIO_SHOW_DIFFICULTY      = booleanPreferencesKey("bio_show_difficulty")
        val BIO_SHOW_FREQUENCY       = booleanPreferencesKey("bio_show_frequency")
        val BIO_SHOW_COPY_BTN        = booleanPreferencesKey("bio_show_copy_btn")
        val BIO_SHOW_SPEAK_BTN       = booleanPreferencesKey("bio_show_speak_btn")
        val BIO_SHOW_SHARE_BTN       = booleanPreferencesKey("bio_show_share_btn")
        val BIO_SHOW_FAVORITE_BTN    = booleanPreferencesKey("bio_show_favorite_btn")
        val BIO_SHOW_SEARCH_WEB_BTN  = booleanPreferencesKey("bio_show_search_web_btn")

        // ── Biology Term of Day ────────────────────────────────────────────────
        val TOTD_NOTIFICATION_ENABLED = booleanPreferencesKey("totd_notification_enabled")
        val TOTD_NOTIFICATION_HOUR    = intPreferencesKey("totd_notification_hour")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            showPronunciation       = prefs[SHOW_PRONUNCIATION] ?: true,
            showPartOfSpeech        = prefs[SHOW_POS] ?: true,
            showDetailedMeaning     = prefs[SHOW_DETAILED] ?: true,
            showHindiMeaning        = prefs[SHOW_HINDI] ?: true,
            showExampleSentence     = prefs[SHOW_EXAMPLE] ?: true,
            showSynonyms            = prefs[SHOW_SYNONYMS] ?: true,
            showAntonyms            = prefs[SHOW_ANTONYMS] ?: true,
            showEtymology           = prefs[SHOW_ETYMOLOGY] ?: false,
            showDifficultyBadge     = prefs[SHOW_DIFFICULTY] ?: true,
            showFrequencyMeter      = prefs[SHOW_FREQUENCY] ?: true,
            showCopyButton          = prefs[SHOW_COPY] ?: true,
            showSpeakWordButton     = prefs[SHOW_SPEAK_WORD] ?: true,
            showSpeakMeaningButton  = prefs[SHOW_SPEAK_MEANING] ?: true,
            showTranslateButton     = prefs[SHOW_TRANSLATE] ?: true,
            showShareButton         = prefs[SHOW_SHARE] ?: true,
            showSaveNoteButton      = prefs[SHOW_SAVE_NOTE] ?: true,
            showFavoriteButton      = prefs[SHOW_FAVORITE] ?: true,
            showFullDetailsButton   = prefs[SHOW_FULL_DETAILS] ?: true,
            enableDragging          = prefs[ENABLE_DRAGGING] ?: true,
            enableResizing          = prefs[ENABLE_RESIZING] ?: true,
            enableEdgeCollapse      = prefs[ENABLE_EDGE_COLLAPSE] ?: true,
            enableCollapseTooBubble = prefs[ENABLE_BUBBLE] ?: true,
            autoCloseSeconds        = prefs[AUTO_CLOSE_SECONDS] ?: 0,
            showSearchWebButton     = prefs[SHOW_SEARCH_WEB] ?: false,
            showFlashcardButton     = prefs[SHOW_FLASHCARD_BTN] ?: false,
            showBrowserButton       = prefs[SHOW_BROWSER_BTN] ?: true,
            buttonOrder             = prefs[BUTTON_ORDER] ?: "copy,speak,meaning,translate,share,note,details,web,flashcard,browser",
            popupWidthFraction      = prefs[POPUP_WIDTH_FRACTION] ?: 0.88f,
            popupHeightFraction     = prefs[POPUP_HEIGHT_FRACTION] ?: 0.65f,
            popupBgAlpha            = prefs[POPUP_BG_ALPHA] ?: 1.0f,
            popupLastOffsetX        = prefs[POPUP_LAST_X] ?: 0f,
            popupLastOffsetY        = prefs[POPUP_LAST_Y] ?: 0f,
            showPersistentNotification  = prefs[SHOW_NOTIFICATION] ?: true,
            saveSearchHistory           = prefs[SAVE_HISTORY] ?: true,
            trackDailyWords             = prefs[TRACK_DAILY] ?: true,
            autoGenerateFlashcards      = prefs[AUTO_FLASHCARDS] ?: true,
            useDarkMode                 = prefs[USE_DARK_MODE] ?: false,
            useSystemTheme              = prefs[USE_SYSTEM_THEME] ?: true,
            openAiApiKey                = prefs[OPEN_AI_KEY] ?: "",
            groqApiKey                  = prefs[GROQ_API_KEY] ?: "",
            aiProviderName              = prefs[AI_PROVIDER] ?: "groq",
            hybridAutoSelectBest        = prefs[HYBRID_AUTO_SELECT] ?: true,
            hybridShowComparison        = prefs[HYBRID_SHOW_COMPARISON] ?: true,
            onDeviceModelId             = prefs[ON_DEVICE_MODEL_ID] ?: "gemma-2b-tiny",
            wotdMode                    = prefs[WOTD_MODE] ?: "global",
            wotdUserLevel               = prefs[WOTD_USER_LEVEL] ?: 2,
            wotdNotificationEnabled     = prefs[WOTD_NOTIFICATION_ENABLED] ?: true,
            wotdNotificationHour        = prefs[WOTD_NOTIFICATION_HOUR] ?: 9,
            // Mode toggles
            englishModeEnabled          = prefs[ENGLISH_MODE_ENABLED] ?: true,
            biologyModeEnabled          = prefs[BIOLOGY_MODE_ENABLED] ?: true,
            // Biology popup display
            bioShowPronunciation        = prefs[BIO_SHOW_PRONUNCIATION] ?: true,
            bioShowCategory             = prefs[BIO_SHOW_CATEGORY] ?: true,
            bioShowDefinition           = prefs[BIO_SHOW_DEFINITION] ?: true,
            bioShowHindi                = prefs[BIO_SHOW_HINDI] ?: true,
            bioShowExample              = prefs[BIO_SHOW_EXAMPLE] ?: true,
            bioShowClassification       = prefs[BIO_SHOW_CLASSIFICATION] ?: true,
            bioShowFunctions            = prefs[BIO_SHOW_FUNCTIONS] ?: true,
            bioShowStructure            = prefs[BIO_SHOW_STRUCTURE] ?: true,
            bioShowRelatedTerms         = prefs[BIO_SHOW_RELATED_TERMS] ?: true,
            bioShowDiseases             = prefs[BIO_SHOW_DISEASES] ?: true,
            bioShowEtymology            = prefs[BIO_SHOW_ETYMOLOGY] ?: true,
            bioShowDifficulty           = prefs[BIO_SHOW_DIFFICULTY] ?: true,
            bioShowFrequency            = prefs[BIO_SHOW_FREQUENCY] ?: true,
            bioShowCopyButton           = prefs[BIO_SHOW_COPY_BTN] ?: true,
            bioShowSpeakButton          = prefs[BIO_SHOW_SPEAK_BTN] ?: true,
            bioShowShareButton          = prefs[BIO_SHOW_SHARE_BTN] ?: true,
            bioShowFavoriteButton       = prefs[BIO_SHOW_FAVORITE_BTN] ?: true,
            bioShowSearchWebButton      = prefs[BIO_SHOW_SEARCH_WEB_BTN] ?: true,
            // TOTD
            totdNotificationEnabled     = prefs[TOTD_NOTIFICATION_ENABLED] ?: true,
            totdNotificationHour        = prefs[TOTD_NOTIFICATION_HOUR] ?: 9
        )
    }

    suspend fun updateWotdSettings(mode: String, level: Int, notifEnabled: Boolean, hour: Int) {
        dataStore.edit { prefs ->
            prefs[WOTD_MODE] = mode
            prefs[WOTD_USER_LEVEL] = level
            prefs[WOTD_NOTIFICATION_ENABLED] = notifEnabled
            prefs[WOTD_NOTIFICATION_HOUR] = hour
        }
    }

    suspend fun update(block: suspend (MutablePreferences) -> Unit) {
        dataStore.edit { prefs -> block(prefs) }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs[SHOW_PRONUNCIATION] = true; prefs[SHOW_POS] = true; prefs[SHOW_DETAILED] = true
            prefs[SHOW_HINDI] = true; prefs[SHOW_EXAMPLE] = true; prefs[SHOW_SYNONYMS] = true
            prefs[SHOW_ANTONYMS] = true; prefs[SHOW_ETYMOLOGY] = false; prefs[SHOW_DIFFICULTY] = true
            prefs[SHOW_FREQUENCY] = true; prefs[SHOW_COPY] = true; prefs[SHOW_SPEAK_WORD] = true
            prefs[SHOW_SPEAK_MEANING] = true; prefs[SHOW_TRANSLATE] = true; prefs[SHOW_SHARE] = true
            prefs[SHOW_SAVE_NOTE] = true; prefs[SHOW_FAVORITE] = true; prefs[SHOW_FULL_DETAILS] = true
            prefs[ENABLE_DRAGGING] = true; prefs[ENABLE_RESIZING] = true; prefs[ENABLE_BUBBLE] = true
            prefs[POPUP_BG_ALPHA] = 1.0f; prefs[AUTO_CLOSE_SECONDS] = 0
            prefs[SHOW_NOTIFICATION] = true; prefs[SAVE_HISTORY] = true
            prefs[TRACK_DAILY] = true; prefs[AUTO_FLASHCARDS] = true
            prefs[USE_DARK_MODE] = false; prefs[USE_SYSTEM_THEME] = true
        }
    }

    suspend fun resetBioSettingsToDefaults() {
        dataStore.edit { prefs ->
            prefs[BIO_SHOW_PRONUNCIATION] = true; prefs[BIO_SHOW_CATEGORY] = true
            prefs[BIO_SHOW_DEFINITION] = true; prefs[BIO_SHOW_HINDI] = true
            prefs[BIO_SHOW_EXAMPLE] = true; prefs[BIO_SHOW_CLASSIFICATION] = true
            prefs[BIO_SHOW_FUNCTIONS] = true; prefs[BIO_SHOW_STRUCTURE] = true
            prefs[BIO_SHOW_RELATED_TERMS] = true; prefs[BIO_SHOW_DISEASES] = true
            prefs[BIO_SHOW_ETYMOLOGY] = true; prefs[BIO_SHOW_DIFFICULTY] = true
            prefs[BIO_SHOW_FREQUENCY] = true; prefs[BIO_SHOW_COPY_BTN] = true
            prefs[BIO_SHOW_SPEAK_BTN] = true; prefs[BIO_SHOW_SHARE_BTN] = true
            prefs[BIO_SHOW_FAVORITE_BTN] = true; prefs[BIO_SHOW_SEARCH_WEB_BTN] = true
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

    val layerSystemConfig: Flow<LayerSystemConfig> = dataStore.data.map { prefs ->
        val json = prefs[LAYER_SYSTEM_CONFIG]
        if (json.isNullOrBlank()) LayerSystemConfig()
        else try { gson.fromJson(json, LayerSystemConfig::class.java) } catch (_: Exception) { LayerSystemConfig() }
    }

    suspend fun updateLayerSystemConfig(config: LayerSystemConfig) {
        dataStore.edit { prefs -> prefs[LAYER_SYSTEM_CONFIG] = gson.toJson(config) }
    }

    fun exportLayerConfig(config: LayerSystemConfig): String = gson.toJson(config)

    fun parseLayerConfig(json: String): LayerSystemConfig? =
        try { gson.fromJson(json, LayerSystemConfig::class.java) } catch (_: Exception) { null }
}
