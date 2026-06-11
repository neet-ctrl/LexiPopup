package com.lexipopup

import com.lexipopup.domain.models.Flashcard
import com.lexipopup.domain.usecases.SpacedRepetitionUseCase
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class SpacedRepetitionTest {

    private val srs = SpacedRepetitionUseCase()

    private fun baseCard() = Flashcard(
        id = 1L, word = "ephemeral",
        frontText = "ephemeral", backText = "Lasting a very short time",
        reviewLevel = 0, nextReviewDate = LocalDateTime.now(),
        interval = 1, easeFactor = 2.5
    )

    @Test
    fun `quality below 3 resets review level`() {
        val card = baseCard().copy(reviewLevel = 3, interval = 6)
        val result = srs.calculateNextReview(card, quality = 2)
        assertEquals(0, result.reviewLevel)
        assertEquals(1, result.interval)
    }

    @Test
    fun `quality 5 increases interval and review level`() {
        val card = baseCard().copy(reviewLevel = 2, interval = 6)
        val result = srs.calculateNextReview(card, quality = 5)
        assertTrue(result.reviewLevel > card.reviewLevel)
        assertTrue(result.interval >= card.interval)
    }

    @Test
    fun `ease factor never drops below 1_3`() {
        var card = baseCard()
        repeat(10) { card = srs.calculateNextReview(card, quality = 0) }
        assertTrue(card.easeFactor >= 1.3)
    }

    @Test
    fun `next review date is in the future`() {
        val card = baseCard()
        val result = srs.calculateNextReview(card, quality = 4)
        assertTrue(result.nextReviewDate.isAfter(LocalDateTime.now()))
    }
}
