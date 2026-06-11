package com.lexipopup.domain.repositories

import com.lexipopup.domain.models.WordEntry
import kotlinx.coroutines.flow.Flow

interface DictionaryRepository {
    suspend fun lookupWord(word: String): WordEntry?
    suspend fun searchSuggestions(query: String, limit: Int = 5): List<String>
    suspend fun searchWords(query: String, limit: Int = 60): List<WordEntry>
    suspend fun saveToCache(entry: WordEntry)
    suspend fun toggleFavorite(word: String)
    suspend fun saveNote(word: String, note: String)
    fun getFavorites(): Flow<List<WordEntry>>
    fun getRecentWords(limit: Int = 20): Flow<List<WordEntry>>
    suspend fun getWordsByLetter(
        letter: String,
        limit: Int = 60,
        offset: Int = 0,
        sortBy: String = "alpha",
        pos: String = ""
    ): List<WordEntry>
    suspend fun countByLetter(letter: String): Int
    suspend fun getWordOfDay(): WordEntry?
}
