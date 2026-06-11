package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.UserNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserNoteDao {

    @Query("SELECT * FROM user_notes WHERE word = :word ORDER BY created_at DESC")
    fun getNotesForWord(word: String): Flow<List<UserNoteEntity>>

    @Query("SELECT * FROM user_notes ORDER BY updated_at DESC")
    fun getAllNotes(): Flow<List<UserNoteEntity>>

    @Query("SELECT COUNT(*) FROM user_notes")
    suspend fun getNoteCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: UserNoteEntity): Long

    @Update
    suspend fun updateNote(note: UserNoteEntity)

    @Query("DELETE FROM user_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM user_notes WHERE word = :word")
    suspend fun deleteNotesForWord(word: String)
}
