package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {

    @Query("SELECT * FROM flashcards WHERE next_review_date <= :now ORDER BY next_review_date ASC")
    fun getDueCards(now: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards ORDER BY next_review_date ASC")
    fun getAllCards(): Flow<List<FlashcardEntity>>

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

    @Query("SELECT COUNT(*) FROM flashcards WHERE next_review_date <= :now")
    fun getDueCount(now: Long = System.currentTimeMillis()): Flow<Int>

    /** Suspend (one-shot) variant for WorkManager workers that cannot collect a Flow. */
    @Query("SELECT COUNT(*) FROM flashcards WHERE next_review_date <= :now")
    suspend fun getDueCountOnce(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM flashcards WHERE review_level >= 5")
    fun getMasteredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards")
    fun getTotalCount(): Flow<Int>
}
