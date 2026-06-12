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
    fun getTotalWordCount(): Flow<Int>
    suspend fun getWordsByLetter(
        letter: String,
        limit: Int = 60,
        offset: Int = 0,
        sortBy: String = "alpha",
        pos: String = ""
    ): List<WordEntry>
    suspend fun countByLetter(letter: String): Int
    suspend fun getWordOfDay(mode: String = "global", userLevel: Int = 2): WordEntry?
    suspend fun getDifficultyDistribution(): Map<Int, Int>
    suspend fun deletePackWords(source: String)
    fun getWordHistory(): Flow<List<WordEntry>>
    fun getWordHistoryCount(): Flow<Int>
    suspend fun markAccessed(word: String)
    suspend fun getSeedWords(query: String = "", limit: Int = 1000): List<WordEntry>
    suspend fun getSeedWordCount(): Int
}
