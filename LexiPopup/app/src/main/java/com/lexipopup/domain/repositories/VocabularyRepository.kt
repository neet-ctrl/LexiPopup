package com.lexipopup.domain.repositories

import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.Flashcard
import com.lexipopup.domain.models.VocabularyHistory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface VocabularyRepository {
    // History
    suspend fun recordSearch(word: String, sourceApp: String, mode: AppMode = AppMode.ENGLISH, timeSpentMs: Long = 0)
    fun getHistory(limit: Int = 100, mode: AppMode = AppMode.ENGLISH): Flow<List<VocabularyHistory>>
    fun getTodayCount(mode: AppMode = AppMode.ENGLISH): Flow<Int>
    fun getWeeklyStats(mode: AppMode = AppMode.ENGLISH): Flow<List<Pair<String, Int>>>
    fun getMostSearchedWords(limit: Int = 10, mode: AppMode = AppMode.ENGLISH): Flow<List<Pair<String, Int>>>
    fun getActivityHeatmap(days: Int = 84, mode: AppMode = AppMode.ENGLISH): Flow<Map<LocalDate, Int>>

    // Favorites
    suspend fun toggleFavorite(word: String, mode: AppMode = AppMode.ENGLISH)
    suspend fun isFavorite(word: String, mode: AppMode = AppMode.ENGLISH): Boolean

    // Notes
    suspend fun saveNote(word: String, note: String, mode: AppMode = AppMode.ENGLISH)
    fun getNotesForWord(word: String): Flow<List<com.lexipopup.domain.models.UserNote>>

    // Flashcards
    fun getDueFlashcards(mode: String = "english"): Flow<List<Flashcard>>
    fun getAllFlashcards(mode: String = "english"): Flow<List<Flashcard>>
    /** Returns every flashcard across ALL modes — used by backup. */
    fun getAllFlashcardsAllModes(): Flow<List<Flashcard>>
    suspend fun reviewFlashcard(id: Long, quality: Int)
    suspend fun createFlashcard(word: String, front: String, back: String, mode: String = "english")
    suspend fun deleteFlashcard(id: Long)

    // Notes (mode-aware)
    fun getNotesForWordByMode(word: String, mode: AppMode = AppMode.ENGLISH): Flow<List<com.lexipopup.domain.models.UserNote>>
}
