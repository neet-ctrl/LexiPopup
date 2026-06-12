package com.lexipopup.data.download

/**
 * Dictionary pack tiers available for in-app download.
 *
 * Sizes are approximate compressed (.db.gz) sizes.
 * Actual imported word counts come from the downloaded database at runtime.
 */
enum class DatabasePack(
    val displayName: String,
    val wordCount: String,
    val description: String,
    val sizeMb: Int
) {
    MINIMAL(
        displayName = "Minimal",
        wordCount   = "~10,000",
        description = "Most common English words + Hindi meanings. Great for everyday reading.",
        sizeMb      = 15
    ),
    STANDARD(
        displayName = "Standard",
        wordCount   = "~155,000",
        description = "Full WordNet — every English lemma with synonyms, antonyms & Hindi meanings. Recommended.",
        sizeMb      = 80
    ),
    FULL(
        displayName = "Full",
        wordCount   = "~700,000+",
        description = "Wiktionary English + WordNet + Hindi WordNet. Maximum coverage for rare & technical words.",
        sizeMb      = 200
    )
}
