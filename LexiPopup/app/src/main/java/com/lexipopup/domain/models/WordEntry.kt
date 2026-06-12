package com.lexipopup.domain.models

data class WordEntry(
    val word: String,
    val pronunciation: String = "",
    val partOfSpeech: String = "",
    val shortMeaning: String = "",
    val detailedMeaning: String = "",
    val hindiMeaning: String = "",
    val hindiPronunciation: String = "",
    val exampleSentence: String = "",
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val etymology: String = "",
    val difficultyLevel: Int = 1,
    val frequencyRating: Int = 50,
    val source: String = "local",
    val isFavorite: Boolean = false,
    val userNote: String = "",
    /** Which mode produced this entry. "english" or "biology". */
    val mode: String = AppMode.ENGLISH.id,
    /** Biology extended data — JSON string (BiologyData). Empty for English mode. */
    val bioExtData: String = "{}"
) {
    val difficultyLabel: String get() = when (difficultyLevel) {
        1 -> "Beginner"
        2 -> "Intermediate"
        3 -> "Advanced"
        4 -> "Expert"
        else -> "Unknown"
    }

    val partOfSpeechColor: Long get() = when (partOfSpeech.lowercase()) {
        "noun" -> 0xFF1565C0
        "verb" -> 0xFF2E7D32
        "adjective", "adj" -> 0xFFE65100
        "adverb", "adv" -> 0xFF6A1B9A
        "preposition", "prep" -> 0xFF37474F
        "conjunction", "conj" -> 0xFF00695C
        "pronoun", "pron" -> 0xFFAD1457
        "interjection", "interj" -> 0xFF558B2F
        // Biology categories
        "organelle" -> 0xFF00838F
        "hormone" -> 0xFF6A1B9A
        "tissue" -> 0xFF558B2F
        "organ" -> 0xFF1565C0
        "process" -> 0xFFE65100
        "molecule" -> 0xFF2E7D32
        "cell" -> 0xFF00695C
        "system" -> 0xFF37474F
        "disease" -> 0xFFB71C1C
        else -> 0xFF546E7A
    }

    fun biologyData(): BiologyData = BiologyData.fromJson(bioExtData)
    fun isBiology(): Boolean = mode == AppMode.BIOLOGY.id
}
