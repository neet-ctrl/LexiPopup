package com.lexipopup.domain.usecases

import android.util.LruCache
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.utils.ai.AiProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LookupWordUseCase @Inject constructor(
    private val repository: DictionaryRepository,
    private val aiProviderManager: AiProviderManager
) {
    private val memoryCache = LruCache<String, WordEntry>(200)

    suspend operator fun invoke(rawWord: String): Result<WordEntry> = withContext(Dispatchers.IO) {
        val word = normalizeWord(rawWord)
        if (word.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty word"))

        // Layer 1: Memory cache (<1 ms)
        memoryCache.get(word)?.let { return@withContext Result.success(it) }

        // Layers 2 & 3: Room DB (offline packs + seed) + FreeDictionaryAPI online fallback
        val entry = repository.lookupWord(word)
        if (entry != null) {
            memoryCache.put(word, entry)
            return@withContext Result.success(entry)
        }

        // Layer 4: AI explanation — provider selected by user (Groq/OpenAI/On-Device/Hybrid)
        val aiEntry = aiProviderManager.explain(word)
        if (aiEntry != null) {
            repository.saveToCache(aiEntry)
            memoryCache.put(word, aiEntry)
            return@withContext Result.success(aiEntry)
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
