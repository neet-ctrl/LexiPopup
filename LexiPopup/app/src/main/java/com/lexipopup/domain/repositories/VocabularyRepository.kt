package com.lexipopup.domain.repositories

import com.lexipopup.domain.models.Flashcard
import com.lexipopup.domain.models.VocabularyHistory
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    suspend fun recordSearch(word: String, sourceApp: String, timeSpentMs: Long = 0)
    fun getHistory(limit: Int = 100): Flow<List<VocabularyHistory>>
    fun getTodayCount(): Flow<Int>
    fun getWeeklyStats(): Flow<List<Pair<String, Int>>>
    fun getDueFlashcards(): Flow<List<Flashcard>>
    fun getAllFlashcards(): Flow<List<Flashcard>>
    suspend fun reviewFlashcard(id: Long, quality: Int)
    suspend fun createFlashcard(word: String, front: String, back: String)
    suspend fun deleteFlashcard(id: Long)
    fun getMostSearchedWords(limit: Int = 10): Flow<List<Pair<String, Int>>>
}
