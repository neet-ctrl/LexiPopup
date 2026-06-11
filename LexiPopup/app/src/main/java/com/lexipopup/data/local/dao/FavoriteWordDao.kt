package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.FavoriteWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteWordDao {

    @Query("SELECT * FROM favorite_words ORDER BY added_at DESC")
    fun getAllFavorites(): Flow<List<FavoriteWordEntity>>

    @Query("SELECT word FROM favorite_words")
    suspend fun getAllFavoriteWords(): List<String>

    @Query("SELECT COUNT(*) FROM favorite_words")
    suspend fun getFavoriteCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_words WHERE word = :word)")
    suspend fun isFavorite(word: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteWordEntity)

    @Query("DELETE FROM favorite_words WHERE word = :word")
    suspend fun removeFavorite(word: String)

    @Query("UPDATE favorite_words SET notes = :note WHERE word = :word")
    suspend fun updateNote(word: String, note: String)

    @Query("DELETE FROM favorite_words")
    suspend fun clearAll()
}
