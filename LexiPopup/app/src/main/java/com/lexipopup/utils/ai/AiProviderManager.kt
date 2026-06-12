package com.lexipopup.utils.ai

import android.content.Context
import com.google.gson.Gson
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.utils.AiExplanationHelper
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates all AI explanation providers: Groq, OpenAI (legacy), On-Device, and Hybrid.
 *
 * Hybrid mode fires Groq and On-Device concurrently, exposes both results via
 * [lastHybridResult], and returns the best entry (Groq preferred) as the primary WordEntry.
 *
 * AI API keys are SHARED between English and Biology modes.
 */
@Singleton
class AiProviderManager @Inject constructor(
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient,
    gson: Gson,
    private val settingsDataStore: SettingsDataStore,
    private val openAiHelper: AiExplanationHelper
) {
    val groqProvider = GroqAiProvider(okHttpClient, gson)
    val onDeviceProvider = OnDeviceAiProvider(context, okHttpClient, gson)

    private val _lastHybridResult = MutableStateFlow<HybridAiResult?>(null)
    val lastHybridResult: StateFlow<HybridAiResult?> = _lastHybridResult.asStateFlow()

    // ── English word lookup ───────────────────────────────────────────────────

    suspend fun explain(word: String): WordEntry? {
        val settings = settingsDataStore.settings.first()
        val type = AiProviderType.fromId(settings.aiProviderName)
        return when (type) {
            AiProviderType.GROQ -> {
                _lastHybridResult.value = null
                groqProvider.explainWord(word, settings.groqApiKey)
            }
            AiProviderType.OPENAI -> {
                _lastHybridResult.value = null
                openAiHelper.explainWord(word, settings.openAiApiKey)
            }
            AiProviderType.ON_DEVICE -> {
                _lastHybridResult.value = null
                onDeviceProvider.explainWord(word)
            }
            AiProviderType.HYBRID -> coroutineScope {
                val groqDeferred = async { groqProvider.explainWord(word, settings.groqApiKey) }
                val localDeferred = async { onDeviceProvider.explainWord(word) }
                val groqEntry = groqDeferred.await()
                val localEntry = localDeferred.await()
                val hybrid = HybridAiResult(groqEntry, localEntry)
                _lastHybridResult.value = hybrid
                if (settings.hybridAutoSelectBest) hybrid.bestEntry else groqEntry
            }
        }
    }

    suspend fun explainWithOpenAI(word: String, apiKey: String): WordEntry? =
        openAiHelper.explainWord(word, apiKey)

    // ── Biology term lookup (shared API keys, biology-specific prompt) ─────────

    suspend fun explainBiology(term: String): WordEntry? {
        val settings = settingsDataStore.settings.first()
        val type = AiProviderType.fromId(settings.aiProviderName)
        return when (type) {
            AiProviderType.GROQ -> groqProvider.explainBiologyTerm(term, settings.groqApiKey)
            AiProviderType.OPENAI -> explainBiologyWithOpenAI(term, settings.openAiApiKey)
            AiProviderType.ON_DEVICE -> onDeviceProvider.explainBiologyTerm(term)
            AiProviderType.HYBRID -> coroutineScope {
                val groqDeferred = async { groqProvider.explainBiologyTerm(term, settings.groqApiKey) }
                val localDeferred = async { onDeviceProvider.explainBiologyTerm(term) }
                groqDeferred.await() ?: localDeferred.await()
            }
        }
    }

    suspend fun explainBiologyWithOpenAI(term: String, apiKey: String): WordEntry? =
        openAiHelper.explainBiologyTerm(term, apiKey)

    fun clearHybridResult() {
        _lastHybridResult.value = null
    }
}
