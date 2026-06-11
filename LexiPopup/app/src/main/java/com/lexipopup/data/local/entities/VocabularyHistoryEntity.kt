package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vocabulary_history",
    indices = [Index(value = ["search_timestamp"])]
)
data class VocabularyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "search_timestamp") val searchTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "source_app") val sourceApp: String = "LexiPopup",
    @ColumnInfo(name = "time_spent_ms") val timeSpentMs: Long = 0
)
