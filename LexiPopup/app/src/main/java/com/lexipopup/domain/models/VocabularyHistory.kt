package com.lexipopup.domain.models

import java.time.LocalDateTime

data class VocabularyHistory(
    val id: Long = 0,
    val word: String,
    val searchTimestamp: LocalDateTime = LocalDateTime.now(),
    val sourceApp: String = "LexiPopup",
    val timeSpentMs: Long = 0
)
