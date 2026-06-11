package com.lexipopup.domain.repositories

import com.lexipopup.domain.models.WordEntry
import kotlinx.coroutines.flow.Flow

interface DictionaryRepository {
    suspend fun lookupWord(word: String): WordEntry?
    suspend fun searchSuggestions(query: String, limit: Int = 5): List<String>
    suspend fun saveToCache(entry: WordEntry)
    suspend fun toggleFavorite(word: String)
    suspend fun saveNote(word: String, note: String)
    fun getFavorites(): Flow<List<WordEntry>>
    fun getRecentWords(limit: Int = 20): Flow<List<WordEntry>>
}
