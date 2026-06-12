package com.lexipopup.domain.models

data class AppSettings(
    // Popup Layout
    val showPronunciation: Boolean = true,
    val showPartOfSpeech: Boolean = true,
    val showDetailedMeaning: Boolean = true,
    val showHindiMeaning: Boolean = true,
    val showExampleSentence: Boolean = true,
    val showSynonyms: Boolean = true,
    val showAntonyms: Boolean = true,
    val showEtymology: Boolean = false,
    val showDifficultyBadge: Boolean = true,
    val showFrequencyMeter: Boolean = true,

    // Action Buttons
    val showCopyButton: Boolean = true,
    val showSpeakWordButton: Boolean = true,
    val showSpeakMeaningButton: Boolean = true,
    val showTranslateButton: Boolean = true,
    val showShareButton: Boolean = true,
    val showSaveNoteButton: Boolean = true,
    val showFavoriteButton: Boolean = true,
    val showFullDetailsButton: Boolean = true,

    // Popup Behavior
    val enableDragging: Boolean = true,
    val enableResizing: Boolean = true,
    val enableCollapseTooBubble: Boolean = true,
    val autoCloseSeconds: Int = 0,
    val showOnTopOfAllApps: Boolean = true,

    // Notification
    val showPersistentNotification: Boolean = true,

    // Vocabulary Tracking
    val saveSearchHistory: Boolean = true,
    val trackDailyWords: Boolean = true,
    val autoGenerateFlashcards: Boolean = true,

    // Theme
    val useDarkMode: Boolean = false,
    val useSystemTheme: Boolean = true,

    // AI Features — legacy OpenAI key (still supported)
    val openAiApiKey: String = "",

    // Dual AI System
    val groqApiKey: String = "",
    val aiProviderName: String = "groq",       // "groq" | "openai" | "on_device" | "hybrid"
    val hybridAutoSelectBest: Boolean = true,
    val hybridShowComparison: Boolean = true,
    val onDeviceModelId: String = "gemma-2b-tiny",

    // Word of the Day
    val wotdMode: String = "global",           // "global" | "personalized" | "random"
    val wotdUserLevel: Int = 2,               // 1=Beginner 2=Intermediate 3=Advanced 4=Expert
    val wotdNotificationEnabled: Boolean = true,
    val wotdNotificationHour: Int = 9
)
