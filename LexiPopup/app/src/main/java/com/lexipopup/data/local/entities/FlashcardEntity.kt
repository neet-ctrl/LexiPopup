package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcards",
    indices = [
        Index(value = ["word"], unique = true),
        Index(value = ["next_review_date"])
    ]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "front_text") val frontText: String,
    @ColumnInfo(name = "back_text") val backText: String,
    @ColumnInfo(name = "review_level") val reviewLevel: Int = 0,
    @ColumnInfo(name = "next_review_date") val nextReviewDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_reviewed") val lastReviewed: Long? = null,
    @ColumnInfo(name = "interval") val interval: Int = 1,
    @ColumnInfo(name = "ease_factor") val easeFactor: Double = 2.5
)
