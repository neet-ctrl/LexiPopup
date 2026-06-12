package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.ChatMessageEntity
import com.lexipopup.data.local.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ── Sessions ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): ChatSessionEntity?

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    @Query("""
        UPDATE chat_sessions
        SET updated_at = :ts, message_count = message_count + 1
        WHERE id = :sessionId
    """)
    suspend fun incrementMessageCount(sessionId: Long, ts: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, title: String)

    // ── Messages ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun clearSessionMessages(sessionId: Long)
}
