package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {

    // ── Mode-aware queries (preferred) ────────────────────────────────────────

    @Query("SELECT * FROM flashcards WHERE mode = :mode AND next_review_date <= :now ORDER BY next_review_date ASC")
    fun getDueCardsByMode(mode: String, now: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE mode = :mode ORDER BY next_review_date ASC")
    fun getAllCardsByMode(mode: String): Flow<List<FlashcardEntity>>

    @Query("SELECT COUNT(*) FROM flashcards WHERE mode = :mode AND next_review_date <= :now")
    fun getDueCountByMode(mode: String, now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards WHERE mode = :mode AND next_review_date <= :now")
    suspend fun getDueCountOnceByMode(mode: String, now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM flashcards WHERE mode = :mode AND review_level >= 5")
    fun getMasteredCountByMode(mode: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards WHERE mode = :mode")
    fun getTotalCountByMode(mode: String): Flow<Int>

    // ── Legacy mode-agnostic queries (kept for WorkManager / widget workers) ──

    @Query("SELECT * FROM flashcards WHERE next_review_date <= :now ORDER BY next_review_date ASC")
    fun getDueCards(now: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards ORDER BY next_review_date ASC")
    fun getAllCards(): Flow<List<FlashcardEntity>>

    @Query("SELECT COUNT(*) FROM flashcards WHERE next_review_date <= :now")
    fun getDueCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards WHERE next_review_date <= :now")
    suspend fun getDueCountOnce(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM flashcards WHERE review_level >= 5")
    fun getMasteredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards")
    fun getTotalCount(): Flow<Int>

    // ── Write operations ───────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCard(card: FlashcardEntity)

    @Update
    suspend fun updateCard(card: FlashcardEntity)

    @Query("SELECT * FROM flashcards WHERE id = :id LIMIT 1")
    suspend fun getCard(id: Long): FlashcardEntity?

    @Delete
    suspend fun deleteCard(card: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteById(id: Long)
}
