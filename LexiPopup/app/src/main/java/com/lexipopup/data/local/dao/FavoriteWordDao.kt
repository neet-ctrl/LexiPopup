package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.FavoriteWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteWordDao {

    @Query("SELECT * FROM favorite_words WHERE mode = :mode ORDER BY added_at DESC")
    fun getAllFavorites(mode: String = "english"): Flow<List<FavoriteWordEntity>>

    @Query("SELECT word FROM favorite_words WHERE mode = :mode")
    suspend fun getAllFavoriteWords(mode: String = "english"): List<String>

    @Query("SELECT COUNT(*) FROM favorite_words WHERE mode = :mode")
    suspend fun getFavoriteCount(mode: String = "english"): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_words WHERE word = :word AND mode = :mode)")
    suspend fun isFavorite(word: String, mode: String = "english"): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteWordEntity)

    @Query("DELETE FROM favorite_words WHERE word = :word AND mode = :mode")
    suspend fun removeFavorite(word: String, mode: String = "english")

    @Query("UPDATE favorite_words SET notes = :note WHERE word = :word AND mode = :mode")
    suspend fun updateNote(word: String, note: String, mode: String = "english")

    @Query("DELETE FROM favorite_words WHERE mode = :mode")
    suspend fun clearAll(mode: String = "english")

    @Query("DELETE FROM favorite_words")
    suspend fun clearAllModes()
}
