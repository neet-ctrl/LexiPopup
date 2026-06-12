package com.lexipopup.utils.ai

import android.content.Context
import com.google.gson.Gson
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
 * The active provider and keys are read live from [SettingsDataStore] so settings changes
 * take effect on the next word lookup without restarting.
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

    fun clearHybridResult() {
        _lastHybridResult.value = null
    }
}
