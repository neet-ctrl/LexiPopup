package com.lexipopup.domain.repositories

import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.WordEntry
import kotlinx.coroutines.flow.Flow

interface DictionaryRepository {
    suspend fun lookupWord(word: String, mode: AppMode = AppMode.ENGLISH): WordEntry?
    suspend fun lookupLocal(word: String, mode: AppMode = AppMode.ENGLISH): WordEntry?
    suspend fun lookupFromHistory(word: String, mode: AppMode = AppMode.ENGLISH, minAccessCount: Int = 1, includeAiSourced: Boolean = true): WordEntry?
    suspend fun lookupOnline(word: String): WordEntry?
    suspend fun searchSuggestions(query: String, limit: Int = 5, mode: AppMode = AppMode.ENGLISH): List<String>
    suspend fun searchWords(query: String, limit: Int = 60, mode: AppMode = AppMode.ENGLISH): List<WordEntry>
    suspend fun saveToCache(entry: WordEntry)
    suspend fun toggleFavorite(word: String, mode: AppMode = AppMode.ENGLISH)
    suspend fun saveNote(word: String, note: String, mode: AppMode = AppMode.ENGLISH)
    fun getFavorites(mode: AppMode = AppMode.ENGLISH): Flow<List<WordEntry>>
    fun getRecentWords(limit: Int = 20, mode: AppMode = AppMode.ENGLISH): Flow<List<WordEntry>>
    fun getTotalWordCount(mode: AppMode = AppMode.ENGLISH): Flow<Int>
    suspend fun getWordsByLetter(
        letter: String,
        limit: Int = 60,
        offset: Int = 0,
        sortBy: String = "alpha",
        pos: String = "",
        mode: AppMode = AppMode.ENGLISH
    ): List<WordEntry>
    suspend fun countByLetter(letter: String, mode: AppMode = AppMode.ENGLISH): Int
    suspend fun getWordOfDay(mode: String = "global", userLevel: Int = 2): WordEntry?
    suspend fun getBiologyTermOfDay(): WordEntry?
    suspend fun getDifficultyDistribution(mode: AppMode = AppMode.ENGLISH): Map<Int, Int>
    suspend fun deletePackWords(source: String)
    fun getWordHistory(mode: AppMode = AppMode.ENGLISH): Flow<List<WordEntry>>
    fun getWordHistoryCount(mode: AppMode = AppMode.ENGLISH): Flow<Int>
    suspend fun markAccessed(word: String, mode: AppMode = AppMode.ENGLISH)
    suspend fun getSeedWords(query: String = "", limit: Int = 1000, mode: AppMode = AppMode.ENGLISH): List<WordEntry>
    suspend fun getSeedWordCount(mode: AppMode = AppMode.ENGLISH): Int
}
