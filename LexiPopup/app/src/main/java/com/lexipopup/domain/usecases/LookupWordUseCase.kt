package com.lexipopup.domain.usecases

import android.util.LruCache
import com.lexipopup.domain.models.LAYER_CACHE
import com.lexipopup.domain.models.LAYER_GROQ_AI
import com.lexipopup.domain.models.LAYER_OFFLINE_DB
import com.lexipopup.domain.models.LAYER_ON_DEVICE
import com.lexipopup.domain.models.LAYER_ONLINE_API
import com.lexipopup.domain.models.LAYER_OPENAI
import com.lexipopup.domain.models.LAYER_RULE_BASED
import com.lexipopup.domain.models.RuleBasedLayerConfig
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.utils.SettingsDataStore
import com.lexipopup.utils.ai.AiProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LookupWordUseCase @Inject constructor(
    private val repository: DictionaryRepository,
    private val aiProviderManager: AiProviderManager,
    private val settingsDataStore: SettingsDataStore
) {
    @Volatile private var memoryCache = LruCache<String, WordEntry>(200)

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    suspend operator fun invoke(rawWord: String): Result<WordEntry> = withContext(Dispatchers.IO) {
        val word = normalizeWord(rawWord)
        if (word.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty word"))

        val layerConfig = settingsDataStore.layerSystemConfig.first()
        val appSettings  = settingsDataStore.settings.first()
        val activeLayers = layerConfig.activeLayers()

        if (activeLayers.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("All lookup layers are disabled. Go to Settings → Lookup Layers to re-enable them.")
            )
        }

        for (layerId in activeLayers) {
            val entry: WordEntry? = when (layerId) {

                LAYER_CACHE -> memoryCache.get(word)

                LAYER_OFFLINE_DB -> try {
                    repository.lookupLocal(word)
                } catch (_: Exception) { null }

                LAYER_ONLINE_API -> try {
                    repository.lookupOnline(word)
                } catch (_: Exception) { null }

                LAYER_GROQ_AI -> {
                    val key = appSettings.groqApiKey
                    if (key.isNotBlank()) {
                        try { aiProviderManager.groqProvider.explainWord(word, key) } catch (_: Exception) { null }
                    } else null
                }

                LAYER_OPENAI -> {
                    val key = appSettings.openAiApiKey
                    if (key.isNotBlank()) {
                        try { aiProviderManager.explainWithOpenAI(word, key) } catch (_: Exception) { null }
                    } else null
                }

                LAYER_ON_DEVICE -> try {
                    aiProviderManager.onDeviceProvider.explainWord(word)
                } catch (_: Exception) { null }

                LAYER_RULE_BASED -> generateRuleBasedEntry(word, layerConfig.ruleBasedConfig)

                else -> null
            }

            if (entry != null) {
                memoryCache.put(word, entry)
                // Persist network / AI results so future offline lookups succeed
                if (layerId in setOf(LAYER_ONLINE_API, LAYER_GROQ_AI, LAYER_OPENAI, LAYER_ON_DEVICE)) {
                    try { repository.saveToCache(entry) } catch (_: Exception) {}
                    try { repository.markAccessed(word) } catch (_: Exception) {}
                }
                return@withContext Result.success(entry)
            }
        }

        Result.failure(NoSuchElementException("\"$word\" was not found in any active lookup layer."))
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        repository.searchSuggestions(normalizeWord(query))
    }

    // ── Rule-based fallback ───────────────────────────────────────────────────

    private fun generateRuleBasedEntry(word: String, config: RuleBasedLayerConfig): WordEntry? {
        if (config.mode == "off") return null
        val badge = if (config.showAiBadge) " [rule-based]" else ""
        val (pos, meaning) = when {
            word.endsWith("tion") || word.endsWith("sion") ->
                "noun" to "The act or process of ${word.dropLast(if (word.endsWith("tion")) 4 else 4)}ing$badge"
            word.endsWith("ness") ->
                "noun" to "The state or quality of being ${word.dropLast(4)}$badge"
            word.endsWith("ment") ->
                "noun" to "The result or product of ${word.dropLast(4)}ing$badge"
            word.endsWith("able") || word.endsWith("ible") ->
                "adjective" to "Capable of being ${word.dropLast(4)}ed$badge"
            word.endsWith("less") ->
                "adjective" to "Without ${word.dropLast(4)}$badge"
            word.endsWith("ful") ->
                "adjective" to "Full of or characterized by ${word.dropLast(3)}$badge"
            word.endsWith("ous") || word.endsWith("ious") ->
                "adjective" to "Relating to or having the quality of ${word.dropLast(if (word.endsWith("ious")) 4 else 3)}$badge"
            word.endsWith("er") && word.length > 4 ->
                "noun" to "One who ${word.dropLast(2)}s$badge"
            word.endsWith("or") && word.length > 4 ->
                "noun" to "One who ${word.dropLast(2)}s$badge"
            word.endsWith("ly") && word.length > 3 ->
                "adverb" to "In a ${word.dropLast(2)} manner$badge"
            word.endsWith("ing") && word.length > 4 ->
                "verb" to "Present participle of ${word.dropLast(3)}$badge"
            word.endsWith("ed") && word.length > 3 ->
                "verb" to "Past tense of ${word.dropLast(2)}$badge"
            config.mode == "enhanced" ->
                "" to "A word used to express or describe $word$badge"
            else -> return null
        }
        return WordEntry(
            word = word,
            partOfSpeech = pos,
            shortMeaning = meaning,
            detailedMeaning = meaning,
            source = "rule_based"
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun normalizeWord(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("[^a-z'-]"), "")
            .trimStart('\'', '-')
            .trimEnd('\'', '-')
}
