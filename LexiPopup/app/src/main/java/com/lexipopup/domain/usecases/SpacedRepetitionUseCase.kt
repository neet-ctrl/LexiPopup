package com.lexipopup.domain.usecases

import com.lexipopup.domain.models.Flashcard
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.max

class SpacedRepetitionUseCase @Inject constructor() {

    /**
     * SM-2 algorithm implementation.
     * @param quality 0-5: 0=complete blackout, 3=correct with difficulty, 5=perfect
     */
    fun calculateNextReview(card: Flashcard, quality: Int): Flashcard {
        val q = quality.coerceIn(0, 5)

        val newEaseFactor = max(1.3, card.easeFactor + 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        val newInterval = when {
            q < 3 -> 1
            card.reviewLevel == 0 -> 1
            card.reviewLevel == 1 -> 6
            else -> (card.interval * newEaseFactor).toInt()
        }
        val newReviewLevel = if (q < 3) 0 else card.reviewLevel + 1

        return card.copy(
            reviewLevel = newReviewLevel,
            interval = newInterval,
            easeFactor = newEaseFactor,
            nextReviewDate = LocalDateTime.now().plusDays(newInterval.toLong()),
            lastReviewed = LocalDateTime.now()
        )
    }
}
