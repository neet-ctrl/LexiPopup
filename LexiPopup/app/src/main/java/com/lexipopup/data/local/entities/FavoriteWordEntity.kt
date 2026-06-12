package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "favorite_words",
    primaryKeys = ["word", "mode"],
    indices = [Index(value = ["word", "mode"], unique = true)]
)
data class FavoriteWordEntity(
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "mode") val mode: String = "english",
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notes") val notes: String? = null
)
