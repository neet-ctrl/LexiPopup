package com.lexipopup.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "settings_json") val settingsJson: String = "{}",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
