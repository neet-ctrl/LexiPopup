package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id")       val sessionId: Long,
    @ColumnInfo(name = "role")             val role: String,           // "user" | "assistant"
    @ColumnInfo(name = "content")          val content: String,
    @ColumnInfo(name = "timestamp")        val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "provider")         val provider: String = "",
    @ColumnInfo(name = "underlined_words") val underlinedWords: String = "[]",  // JSON array of word strings
    @ColumnInfo(name = "is_error")         val isError: Boolean = false,
    @ColumnInfo(name = "tokens_used")      val tokensUsed: Int = 0
)
