package com.lexipopup.domain.models

data class AppSettings(
    // Popup Layout — English mode
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
    val enableEdgeCollapse: Boolean = true,
    val enableCollapseTooBubble: Boolean = true,
    val autoCloseSeconds: Int = 0,
    val showOnTopOfAllApps: Boolean = true,

    // Window size
    val popupWidthFraction: Float = 0.88f,
    val popupHeightFraction: Float = 0.65f,

    // Window transparency
    val popupBgAlpha: Float = 1.0f,

    // Last window position
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

    // AI — legacy OpenAI key (still supported)
    val openAiApiKey: String = "",

    // Dual AI System (SHARED between modes)
    val groqApiKey: String = "",
    val aiProviderName: String = "groq",
    val hybridAutoSelectBest: Boolean = true,
    val hybridShowComparison: Boolean = true,
    val onDeviceModelId: String = "gemma-2b-tiny",

    // Word of the Day (English)
    val wotdMode: String = "global",
    val wotdUserLevel: Int = 2,
    val wotdNotificationEnabled: Boolean = true,
    val wotdNotificationHour: Int = 9,

    // ── Mode Enable / Disable ─────────────────────────────────────────────────
    val englishModeEnabled: Boolean = true,
    val biologyModeEnabled: Boolean = true,

    // ── Biology Popup Display Settings ────────────────────────────────────────
    val bioShowPronunciation: Boolean = true,
    val bioShowCategory: Boolean = true,       // maps to partOfSpeech in bio context
    val bioShowDefinition: Boolean = true,
    val bioShowHindi: Boolean = true,
    val bioShowExample: Boolean = true,
    val bioShowClassification: Boolean = true,
    val bioShowFunctions: Boolean = true,
    val bioShowStructure: Boolean = true,
    val bioShowRelatedTerms: Boolean = true,
    val bioShowDiseases: Boolean = true,
    val bioShowEtymology: Boolean = true,
    val bioShowDifficulty: Boolean = true,
    val bioShowFrequency: Boolean = true,
    val bioShowCopyButton: Boolean = true,
    val bioShowSpeakButton: Boolean = true,
    val bioShowShareButton: Boolean = true,
    val bioShowFavoriteButton: Boolean = true,
    val bioShowSearchWebButton: Boolean = true,

    // Biology Term of Day
    val totdNotificationEnabled: Boolean = true,
    val totdNotificationHour: Int = 9,

    // ── Biology Flashcard Card-Display Settings ────────────────────────────────
    /** Show [Category] tag on the front of new biology flashcards */
    val bioCardShowCategory: Boolean = true,
    /** Include example sentence on the back of new biology flashcards */
    val bioCardShowExample: Boolean = true,
    /** Include primary function on the back of new biology flashcards */
    val bioCardShowFunction: Boolean = true,
    /** Include Hindi meaning on the back of new biology flashcards */
    val bioCardShowHindi: Boolean = false
)
