package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["mode"])]
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title")         val title: String = "New Chat",
    @ColumnInfo(name = "provider")      val provider: String = "groq",
    @ColumnInfo(name = "mode",          defaultValue = "english") val mode: String = "english",
    @ColumnInfo(name = "created_at")    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "message_count") val messageCount: Int = 0
)
