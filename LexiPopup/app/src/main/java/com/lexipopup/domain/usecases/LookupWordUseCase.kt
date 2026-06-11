package com.lexipopup.domain.usecases

import android.util.LruCache
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LookupWordUseCase @Inject constructor(
    private val repository: DictionaryRepository
) {
    private val memoryCache = LruCache<String, WordEntry>(200)

    suspend operator fun invoke(rawWord: String): Result<WordEntry> = withContext(Dispatchers.IO) {
        val word = normalizeWord(rawWord)
        if (word.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty word"))

        // 1. Memory cache (fastest, <1ms)
        memoryCache.get(word)?.let { return@withContext Result.success(it) }

        // 2. Room DB / offline sources
        val entry = repository.lookupWord(word)
        if (entry != null) {
            memoryCache.put(word, entry)
            return@withContext Result.success(entry)
        }

        Result.failure(NoSuchElementException("Word not found: $word"))
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        repository.searchSuggestions(normalizeWord(query))
    }

    private fun normalizeWord(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("[^a-z'-]"), "")
            .trimStart('\'', '-')
            .trimEnd('\'', '-')
}
