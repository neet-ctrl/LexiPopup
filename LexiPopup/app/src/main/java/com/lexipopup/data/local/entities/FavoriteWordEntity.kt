package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_words",
    indices = [Index(value = ["word"], unique = true)]
)
data class FavoriteWordEntity(
    @PrimaryKey val word: String,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notes") val notes: String? = null
)
