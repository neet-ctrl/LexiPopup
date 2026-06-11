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
    val userNote: String = ""
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
        else -> 0xFF546E7A
    }
}
