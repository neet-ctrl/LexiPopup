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
    val showSearchWebButton: Boolean = false,
    val showFlashcardButton: Boolean = false,
    val showBrowserButton: Boolean = true,

    // Button order — comma-separated button IDs
    val buttonOrder: String = "copy,speak,meaning,translate,share,note,details,web,flashcard,browser",

    // Popup Behavior
    val enableDragging: Boolean = true,
    val enableResizing: Boolean = true,
    val enableEdgeCollapse: Boolean = true,   // YouTube-style edge tab collapse
    val enableCollapseTooBubble: Boolean = true,
    val autoCloseSeconds: Int = 0,
    val showOnTopOfAllApps: Boolean = true,

    // Window size
    val popupWidthFraction: Float = 0.88f,   // 0.50 – 0.95
    val popupHeightFraction: Float = 0.65f,  // 0.35 – 0.88

    // Last window position (restored on next open)
    val popupLastOffsetX: Float = 0f,
    val popupLastOffsetY: Float = 0f,

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
    val aiProviderName: String = "groq",
    val hybridAutoSelectBest: Boolean = true,
    val hybridShowComparison: Boolean = true,
    val onDeviceModelId: String = "gemma-2b-tiny",

    // Word of the Day
    val wotdMode: String = "global",
    val wotdUserLevel: Int = 2,
    val wotdNotificationEnabled: Boolean = true,
    val wotdNotificationHour: Int = 9
)
