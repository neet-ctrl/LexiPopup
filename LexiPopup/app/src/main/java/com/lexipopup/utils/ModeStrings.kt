package com.lexipopup.utils

import com.lexipopup.domain.models.AppMode

object ModeStrings {

    // ── Bottom nav & TopAppBar ────────────────────────────────────────────────
    fun navDictionary(mode: AppMode) = if (mode == AppMode.BIOLOGY) "Bio Database" else "Dictionary"
    fun navAiChat(mode: AppMode)     = if (mode == AppMode.BIOLOGY) "Bio Expert"   else "AI Chat"
    fun navFlashcards(mode: AppMode) = if (mode == AppMode.BIOLOGY) "Bio Cards"    else "Flashcards"

    fun titleDictionary(mode: AppMode) = if (mode == AppMode.BIOLOGY) "Bio Database" else "Dictionary"
    fun titleAiChat(mode: AppMode)     = if (mode == AppMode.BIOLOGY) "Biology Expert" else "AI Chat"
    fun titleFlashcards(mode: AppMode) = if (mode == AppMode.BIOLOGY) "Biology Cards" else "Flashcards"

    // ── Home screen ───────────────────────────────────────────────────────────
    fun searchPlaceholder(mode: AppMode)  = if (mode == AppMode.BIOLOGY) "Identify any biology term…" else "Search any word or phrase…"
    fun voicePrompt(mode: AppMode)        = if (mode == AppMode.BIOLOGY) "Say a biology term to look up…" else "Say a word to look up…"
    fun statFavorites(mode: AppMode)      = if (mode == AppMode.BIOLOGY) "Saved" else "Favorites"
    fun statDatabase(mode: AppMode)       = if (mode == AppMode.BIOLOGY) "Taxa" else "In DB"
    fun recentSection(mode: AppMode)      = if (mode == AppMode.BIOLOGY) "🔬  Recent Terms" else "🔥  Recent Searches"
    fun vocabSection(mode: AppMode)       = if (mode == AppMode.BIOLOGY) "🧬  YOUR SPECIMENS  (Last 7 days)" else "📚  YOUR VOCABULARY  (Last 7 days)"
    fun favSection(mode: AppMode)         = if (mode == AppMode.BIOLOGY) "⭐  SAVED SPECIES" else "⭐  FAVORITE WORDS"
    fun emptyDictMsg(mode: AppMode)       = if (mode == AppMode.BIOLOGY) "Your biology database is empty" else "Your dictionary is empty"
    fun emptyDictAction(mode: AppMode)    = if (mode == AppMode.BIOLOGY) "Browse Database" else "Browse Dictionary"
    fun wotdSectionLabel(mode: AppMode)   = if (mode == AppMode.BIOLOGY) "📖  TERM OF THE DAY" else "📖  WORD OF THE DAY"

    // ── Dictionary browser ────────────────────────────────────────────────────
    fun browserSearchPlaceholder(mode: AppMode) = if (mode == AppMode.BIOLOGY) "Search any biology term…" else "Search any word or phrase…"
    fun browserSayWord(mode: AppMode)           = if (mode == AppMode.BIOLOGY) "Say a biology term…" else "Say a word…"
    fun wotdCardTitle(mode: AppMode)            = if (mode == AppMode.BIOLOGY) "TERM OF THE DAY" else "WORD OF THE DAY"
    fun alphabetLabel(mode: AppMode)            = if (mode == AppMode.BIOLOGY) "🔤  ALPHABETICAL INDEX" else "🔤  ALPHABETICAL BROWSE"
    fun startingWithLabel(mode: AppMode, letter: String) =
        if (mode == AppMode.BIOLOGY) "📋  TERMS STARTING WITH '$letter'" else "📋  WORDS STARTING WITH '$letter'"
    fun letterCountLabel(mode: AppMode, count: Int) =
        if (mode == AppMode.BIOLOGY) "$count term${if (count != 1) "s" else ""}" else "$count word${if (count != 1) "s" else ""}"
    fun noEntriesForLetter(mode: AppMode, letter: String) =
        if (mode == AppMode.BIOLOGY)
            "No biology terms for '$letter' yet\nLook up terms with Moon+ Reader to populate"
        else
            "No words for '$letter' yet\nLook up words with Moon+ Reader to populate"
    fun seedBannerTitle(mode: AppMode) = if (mode == AppMode.BIOLOGY) "Built-in Biology Terms" else "Browse 1,000 Built-in Words"

    // ── Word / Term history ───────────────────────────────────────────────────
    fun historyTitle(mode: AppMode)    = if (mode == AppMode.BIOLOGY) "Term History" else "Word History"
    fun historySubtitle(mode: AppMode, count: Int) =
        if (mode == AppMode.BIOLOGY) "$count terms saved offline" else "$count words saved offline"
    fun historySearchHint(mode: AppMode) = if (mode == AppMode.BIOLOGY) "Search term history…" else "Search history…"
    fun historyEmpty(mode: AppMode)    =
        if (mode == AppMode.BIOLOGY)
            "No history yet.\nLook up a biology term to start building your observation log."
        else
            "No history yet.\nLook up a word to start building your vocabulary log."

    // ── Source labels (adds biology variants) ─────────────────────────────────
    fun sourceLabel(source: String): Pair<String, String> = when (source) {
        "online"       -> "🌐" to "Online API"
        "groq"         -> "🤖" to "Groq AI"
        "groq_bio"     -> "🤖" to "Groq (Bio)"
        "openai"       -> "🤖" to "OpenAI"
        "openai_bio"   -> "🤖" to "OpenAI (Bio)"
        "on_device"    -> "📱" to "On-Device AI"
        "on_device_bio"-> "📱" to "On-Device (Bio)"
        "seed"         -> "🌱" to "Built-in"
        "minimal"      -> "📦" to "Minimal Pack"
        "standard"     -> "📦" to "Standard Pack"
        "full"         -> "📦" to "Full Pack"
        "local"        -> "✏️" to "Manual"
        else           -> "📖" to source
    }

    // ── Flashcards ────────────────────────────────────────────────────────────
    fun flashcardsEmpty(mode: AppMode) =
        if (mode == AppMode.BIOLOGY) "No biology cards yet.\nAdd terms from the popup." else "No flashcards yet.\nAdd words from the popup."
}
