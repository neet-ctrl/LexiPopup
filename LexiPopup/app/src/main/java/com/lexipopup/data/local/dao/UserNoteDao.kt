package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.UserNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserNoteDao {

    // ── Mode-aware queries (preferred) ────────────────────────────────────────

    @Query("SELECT * FROM user_notes WHERE word = :word AND mode = :mode ORDER BY created_at DESC")
    fun getNotesForWordByMode(word: String, mode: String): Flow<List<UserNoteEntity>>

    @Query("SELECT * FROM user_notes WHERE mode = :mode ORDER BY updated_at DESC")
    fun getAllNotesByMode(mode: String): Flow<List<UserNoteEntity>>

    @Query("SELECT COUNT(*) FROM user_notes WHERE mode = :mode")
    suspend fun getNoteCountByMode(mode: String): Int

    @Query("DELETE FROM user_notes WHERE word = :word AND mode = :mode")
    suspend fun deleteNotesForWordByMode(word: String, mode: String)

    // ── Legacy mode-agnostic queries (kept for backward compatibility) ─────────

    @Query("SELECT * FROM user_notes WHERE word = :word ORDER BY created_at DESC")
    fun getNotesForWord(word: String): Flow<List<UserNoteEntity>>

    @Query("SELECT * FROM user_notes ORDER BY updated_at DESC")
    fun getAllNotes(): Flow<List<UserNoteEntity>>

    @Query("SELECT COUNT(*) FROM user_notes")
    suspend fun getNoteCount(): Int

    @Query("DELETE FROM user_notes WHERE word = :word")
    suspend fun deleteNotesForWord(word: String)

    // ── Write operations ───────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: UserNoteEntity): Long

    @Update
    suspend fun updateNote(note: UserNoteEntity)

    @Query("DELETE FROM user_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)
}
