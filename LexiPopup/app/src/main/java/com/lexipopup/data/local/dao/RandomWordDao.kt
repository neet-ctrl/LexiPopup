package com.lexipopup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lexipopup.data.local.entities.RandomWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RandomWordDao {

    @Query("SELECT * FROM random_words WHERE is_seen = 0 ORDER BY fetched_at ASC LIMIT 1")
    suspend fun getNextUnseen(): RandomWordEntity?

    @Query("SELECT * FROM random_words WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RandomWordEntity?

    @Query("SELECT * FROM random_words WHERE is_seen = 0 ORDER BY fetched_at ASC LIMIT :n")
    suspend fun getTopUnseen(n: Int): List<RandomWordEntity>

    @Query("SELECT COUNT(*) FROM random_words WHERE is_seen = 0")
    suspend fun getUnseenCount(): Int

    @Query("SELECT * FROM random_words WHERE is_seen = 1 ORDER BY fetched_at DESC")
    fun getDiscoveredWords(): Flow<List<RandomWordEntity>>

    @Query("SELECT COUNT(*) FROM random_words WHERE is_seen = 1")
    suspend fun getDiscoveredCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<RandomWordEntity>)

    @Query("UPDATE random_words SET is_seen = 1 WHERE id = :id")
    suspend fun markSeen(id: Long)

    @Query("SELECT word FROM random_words")
    suspend fun getAllWordNames(): List<String>

    @Query("DELETE FROM random_words WHERE is_seen = 1 AND fetched_at < :cutoff")
    suspend fun pruneOldDiscovered(cutoff: Long)

    @Query("DELETE FROM random_words WHERE is_seen = 0")
    suspend fun clearQueue()
}
