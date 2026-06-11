package com.lexipopup.domain.repositories

import com.lexipopup.domain.models.Flashcard
import com.lexipopup.domain.models.VocabularyHistory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface VocabularyRepository {
    // History
    suspend fun recordSearch(word: String, sourceApp: String, timeSpentMs: Long = 0)
    fun getHistory(limit: Int = 100): Flow<List<VocabularyHistory>>
    fun getTodayCount(): Flow<Int>
    fun getWeeklyStats(): Flow<List<Pair<String, Int>>>
    fun getMostSearchedWords(limit: Int = 10): Flow<List<Pair<String, Int>>>
    fun getActivityHeatmap(days: Int = 84): Flow<Map<LocalDate, Int>>

    // Favorites
    suspend fun toggleFavorite(word: String)
    suspend fun isFavorite(word: String): Boolean

    // Notes
    suspend fun saveNote(word: String, note: String)
    fun getNotesForWord(word: String): Flow<List<com.lexipopup.domain.models.UserNote>>

    // Flashcards
    fun getDueFlashcards(): Flow<List<Flashcard>>
    fun getAllFlashcards(): Flow<List<Flashcard>>
    suspend fun reviewFlashcard(id: Long, quality: Int)
    suspend fun createFlashcard(word: String, front: String, back: String)
    suspend fun deleteFlashcard(id: Long)
}
