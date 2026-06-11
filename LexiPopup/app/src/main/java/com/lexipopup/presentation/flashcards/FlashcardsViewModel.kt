package com.lexipopup.presentation.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.Flashcard
import com.lexipopup.domain.repositories.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlashcardStats(val due: Int = 0, val learning: Int = 0, val mastered: Int = 0)

@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository
) : ViewModel() {

    val dueCards: StateFlow<List<Flashcard>> = vocabularyRepository.getDueFlashcards()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val stats: StateFlow<FlashcardStats> = vocabularyRepository.getAllFlashcards()
        .combine(dueCards) { all, due ->
            FlashcardStats(
                due = due.size,
                learning = all.count { it.reviewLevel in 1..4 },
                mastered = all.count { it.reviewLevel >= 5 }
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, FlashcardStats())

    fun reviewCard(id: Long, quality: Int) {
        viewModelScope.launch {
            vocabularyRepository.reviewFlashcard(id, quality)
        }
    }

    fun skipCard(id: Long) {
        // Soft skip: quality 3 = "remembered with effort" — advances to next card
        // with only a small interval increase, effectively deferring the card.
        reviewCard(id, 3)
    }
}
