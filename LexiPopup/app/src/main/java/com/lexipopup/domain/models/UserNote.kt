package com.lexipopup.domain.models

data class UserNote(
    val id: Long = 0,
    val word: String,
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
