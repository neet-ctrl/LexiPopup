package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_cache",
    indices = [
        Index(value = ["word"], unique = true),
        Index(value = ["frequency_rating"]),
        Index(value = ["difficulty_level"])
    ]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "pronunciation") val pronunciation: String = "",
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String = "",
    @ColumnInfo(name = "short_meaning") val shortMeaning: String = "",
    @ColumnInfo(name = "detailed_meaning") val detailedMeaning: String = "",
    @ColumnInfo(name = "hindi_meaning") val hindiMeaning: String = "",
    @ColumnInfo(name = "hindi_pronunciation") val hindiPronunciation: String = "",
    @ColumnInfo(name = "example_sentence") val exampleSentence: String = "",
    @ColumnInfo(name = "synonyms") val synonyms: String = "[]",
    @ColumnInfo(name = "antonyms") val antonyms: String = "[]",
    @ColumnInfo(name = "etymology") val etymology: String = "",
    @ColumnInfo(name = "difficulty_level") val difficultyLevel: Int = 1,
    @ColumnInfo(name = "frequency_rating") val frequencyRating: Int = 50,
    @ColumnInfo(name = "source") val source: String = "local",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_accessed") val lastAccessed: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "access_count") val accessCount: Int = 0,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "user_note") val userNote: String = "",
    @ColumnInfo(name = "last_reviewed") val lastReviewed: Long? = null
)
