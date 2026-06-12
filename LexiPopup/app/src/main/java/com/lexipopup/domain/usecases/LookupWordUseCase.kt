package com.lexipopup.domain.usecases

import android.util.LruCache
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.LAYER_CACHE
import com.lexipopup.domain.models.LAYER_GROQ_AI
import com.lexipopup.domain.models.LAYER_HISTORY
import com.lexipopup.domain.models.LAYER_OFFLINE_DB
import com.lexipopup.domain.models.LAYER_ON_DEVICE
import com.lexipopup.domain.models.LAYER_ONLINE_API
import com.lexipopup.domain.models.LAYER_OPENAI
import com.lexipopup.domain.models.LAYER_RULE_BASED
import com.lexipopup.domain.models.LayerSystemConfig
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
    /** Separate caches per mode to avoid cross-contamination. */
    @Volatile private var englishCache = LruCache<String, WordEntry>(200)
    @Volatile private var biologyCache = LruCache<String, WordEntry>(100)

    private fun cacheFor(mode: AppMode) = if (mode == AppMode.BIOLOGY) biologyCache else englishCache

    fun clearMemoryCache() {
        englishCache.evictAll()
        biologyCache.evictAll()
    }

    suspend operator fun invoke(rawWord: String, mode: AppMode = AppMode.ENGLISH): Result<WordEntry> = withContext(Dispatchers.IO) {
        val word = normalizeWord(rawWord)
        if (word.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty word"))

        val layerConfig = settingsDataStore.layerSystemConfig.first()
        val appSettings = settingsDataStore.settings.first()
        val activeLayers = layerConfig.activeLayers()

        if (activeLayers.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("All lookup layers are disabled. Go to Settings → Lookup Layers to re-enable them.")
            )
        }

        for (layerId in activeLayers) {
            val entry = tryLayer(layerId, word, mode, layerConfig, appSettings.groqApiKey, appSettings.openAiApiKey)
            if (entry != null) {
                cacheFor(mode).put(word, entry)
                if (layerId in setOf(LAYER_ONLINE_API, LAYER_GROQ_AI, LAYER_OPENAI, LAYER_ON_DEVICE)) {
                    try { repository.saveToCache(entry) } catch (_: Exception) {}
                    try { repository.markAccessed(word, mode) } catch (_: Exception) {}
                }
                return@withContext Result.success(entry)
            }
        }

        Result.failure(NoSuchElementException("\"$word\" was not found in any active lookup layer."))
    }

    suspend fun invokeWithLayer(rawWord: String, layerId: String, mode: AppMode = AppMode.ENGLISH): Result<WordEntry> = withContext(Dispatchers.IO) {
        val word = normalizeWord(rawWord)
        if (word.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty word"))
        val layerConfig = settingsDataStore.layerSystemConfig.first()
        val appSettings = settingsDataStore.settings.first()
        val entry = tryLayer(layerId, word, mode, layerConfig, appSettings.groqApiKey, appSettings.openAiApiKey)
        if (entry != null) {
            cacheFor(mode).put(word, entry)
            if (layerId in setOf(LAYER_ONLINE_API, LAYER_GROQ_AI, LAYER_OPENAI, LAYER_ON_DEVICE)) {
                try { repository.saveToCache(entry) } catch (_: Exception) {}
                try { repository.markAccessed(word, mode) } catch (_: Exception) {}
            }
            return@withContext Result.success(entry)
        }
        Result.failure(NoSuchElementException("\"$word\" not found via $layerId."))
    }

    private suspend fun tryLayer(
        layerId: String,
        word: String,
        mode: AppMode,
        layerConfig: LayerSystemConfig,
        groqKey: String,
        openAiKey: String
    ): WordEntry? = when (layerId) {

        LAYER_CACHE -> cacheFor(mode).get(word)

        LAYER_HISTORY -> try {
            val cfg = layerConfig.historyConfig
            repository.lookupFromHistory(word, mode, cfg.minAccessCount, cfg.includeAiSourced)
        } catch (_: Exception) { null }

        LAYER_OFFLINE_DB -> try {
            repository.lookupLocal(word, mode)
        } catch (_: Exception) { null }

        LAYER_ONLINE_API -> if (mode == AppMode.ENGLISH) {
            try { repository.lookupOnline(word) } catch (_: Exception) { null }
        } else null  // Biology only via AI, not generic dictionary APIs

        LAYER_GROQ_AI -> {
            if (groqKey.isNotBlank()) {
                try {
                    if (mode == AppMode.BIOLOGY) {
                        aiProviderManager.groqProvider.explainBiologyTerm(word, groqKey)
                    } else {
                        aiProviderManager.groqProvider.explainWord(word, groqKey)
                    }
                } catch (_: Exception) { null }
            } else null
        }

        LAYER_OPENAI -> {
            if (openAiKey.isNotBlank()) {
                try {
                    if (mode == AppMode.BIOLOGY) {
                        aiProviderManager.explainBiologyWithOpenAI(word, openAiKey)
                    } else {
                        aiProviderManager.explainWithOpenAI(word, openAiKey)
                    }
                } catch (_: Exception) { null }
            } else null
        }

        LAYER_ON_DEVICE -> try {
            if (mode == AppMode.BIOLOGY) {
                aiProviderManager.onDeviceProvider.explainBiologyTerm(word)
            } else {
                aiProviderManager.onDeviceProvider.explainWord(word)
            }
        } catch (_: Exception) { null }

        LAYER_RULE_BASED -> if (mode == AppMode.ENGLISH) {
            generateRuleBasedEntry(word, layerConfig.ruleBasedConfig)
        } else null  // Rule-based doesn't apply to biology terms

        else -> null
    }

    suspend fun suggestions(query: String, mode: AppMode = AppMode.ENGLISH): List<String> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        repository.searchSuggestions(normalizeWord(query), mode = mode)
    }

    // ── Rule-based fallback (English only) ───────────────────────────────────

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
        return WordEntry(word = word, partOfSpeech = pos, shortMeaning = meaning, detailedMeaning = meaning, source = "rule_based")
    }

    private fun normalizeWord(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("[^a-z'\\-]"), "")
            .trimStart('\'', '-')
            .trimEnd('\'', '-')
}
