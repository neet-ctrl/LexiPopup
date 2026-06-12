package com.lexipopup.domain.models

enum class AppMode(val id: String, val displayName: String, val emoji: String) {
    ENGLISH("english", "English", "🔤"),
    BIOLOGY("biology", "Biology", "🧬");

    companion object {
        fun fromId(id: String): AppMode = values().find { it.id == id } ?: ENGLISH
    }
}
