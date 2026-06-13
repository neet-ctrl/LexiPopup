package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "random_words")
data class RandomWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val teaser: String = "",
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String = "",
    val pronunciation: String = "",
    val definition: String = "",
    val example: String = "",
    val etymology: String = "",
    val difficulty: String = "advanced",
    val topic: String = "general",
    val provider: String = "groq",
    @ColumnInfo(name = "is_seen") val isSeen: Boolean = false,
    @ColumnInfo(name = "fetched_at", defaultValue = "0") val fetchedAt: Long = System.currentTimeMillis()
)
