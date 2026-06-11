package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.WordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT * FROM dictionary_cache WHERE word = :word LIMIT 1")
    suspend fun findWord(word: String): WordEntity?

    @Query("SELECT word FROM dictionary_cache WHERE word LIKE :prefix || '%' ORDER BY frequency_rating DESC LIMIT :limit")
    suspend fun getSuggestions(prefix: String, limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity)

    @Query("UPDATE dictionary_cache SET is_favorite = NOT is_favorite WHERE word = :word")
    suspend fun toggleFavorite(word: String)

    @Query("UPDATE dictionary_cache SET user_note = :note WHERE word = :word")
    suspend fun updateNote(word: String, note: String)

    @Query("UPDATE dictionary_cache SET last_accessed = :ts, access_count = access_count + 1 WHERE word = :word")
    suspend fun updateAccess(word: String, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM dictionary_cache WHERE is_favorite = 1 ORDER BY last_accessed DESC")
    fun getFavorites(): Flow<List<WordEntity>>

    @Query("SELECT * FROM dictionary_cache ORDER BY last_accessed DESC LIMIT :limit")
    fun getRecentWords(limit: Int): Flow<List<WordEntity>>

    @Query("SELECT COUNT(*) FROM dictionary_cache")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM dictionary_cache WHERE source = 'local' AND word = :word")
    suspend fun deleteWord(word: String)
}
