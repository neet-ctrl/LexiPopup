package com.lexipopup.domain.models

import java.time.LocalDateTime

data class Flashcard(
    val id: Long = 0,
    val word: String,
    val frontText: String,
    val backText: String,
    val reviewLevel: Int = 0,
    val nextReviewDate: LocalDateTime = LocalDateTime.now(),
    val lastReviewed: LocalDateTime? = null,
    val interval: Int = 1,
    val easeFactor: Double = 2.5
) {
    val isDue: Boolean get() = LocalDateTime.now() >= nextReviewDate

    val statusLabel: String get() = when (reviewLevel) {
        0 -> "New"
        in 1..2 -> "Learning"
        in 3..4 -> "Review"
        else -> "Mastered"
    }
}
