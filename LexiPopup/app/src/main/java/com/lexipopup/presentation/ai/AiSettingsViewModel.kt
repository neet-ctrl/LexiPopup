package com.lexipopup.presentation.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.utils.SettingsDataStore
import com.lexipopup.utils.ai.AiProviderManager
import com.lexipopup.utils.ai.AiProviderType
import com.lexipopup.utils.ai.HybridAiResult
import com.lexipopup.utils.ai.OnDeviceModel
import com.lexipopup.utils.ai.OnDeviceModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Inference test result ─────────────────────────────────────────────────────

sealed class InferenceTestResult {
    object Testing : InferenceTestResult()
    data class Success(val word: String, val meaning: String, val durationMs: Long) : InferenceTestResult()
    data class Failure(val error: String) : InferenceTestResult()
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    val aiProviderManager: AiProviderManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val onDeviceStatus: StateFlow<OnDeviceModelStatus> =
        aiProviderManager.onDeviceProvider.modelStatus

    val downloadLogs: StateFlow<List<String>> =
        aiProviderManager.onDeviceProvider.downloadLogs

    val hybridResult: StateFlow<HybridAiResult?> = aiProviderManager.lastHybridResult

    private val _inferenceTestResult = MutableStateFlow<InferenceTestResult?>(null)
    val inferenceTestResult: StateFlow<InferenceTestResult?> = _inferenceTestResult.asStateFlow()

    private var currentDownloadJob: Job? = null

    init {
        // If the model is already on disk when the app starts, load it into
        // native memory now so the very first inference call is fast.
        viewModelScope.launch {
            aiProviderManager.onDeviceProvider.warmUpModel()
        }
    }

    // ── Provider selection ──────────────────────────────────────────────────

    fun selectProvider(type: AiProviderType) {
        viewModelScope.launch {
            settingsDataStore.update { it[SettingsDataStore.AI_PROVIDER] = type.id }
        }
    }

    // ── API keys ────────────────────────────────────────────────────────────

    fun updateGroqKey(key: String) {
        viewModelScope.launch {
            settingsDataStore.update { it[SettingsDataStore.GROQ_API_KEY] = key }
        }
    }

    fun updateOpenAiKey(key: String) {
        viewModelScope.launch {
            settingsDataStore.update { it[SettingsDataStore.OPEN_AI_KEY] = key }
        }
    }

    // ── AI Chat auto-speak ───────────────────────────────────────────────────

    fun setAutoSpeakAiResponse(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.update { it[SettingsDataStore.AUTO_SPEAK_AI_RESPONSE] = value }
        }
    }

    // ── Hybrid options ──────────────────────────────────────────────────────

    fun setHybridAutoSelect(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.update { it[SettingsDataStore.HYBRID_AUTO_SELECT] = value }
        }
    }

    fun setHybridShowComparison(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.update { it[SettingsDataStore.HYBRID_SHOW_COMPARISON] = value }
        }
    }

    // ── On-device model management ──────────────────────────────────────────

    fun selectOnDeviceModel(model: OnDeviceModel) {
        aiProviderManager.onDeviceProvider.selectModel(model)
        viewModelScope.launch {
            settingsDataStore.update { it[SettingsDataStore.ON_DEVICE_MODEL_ID] = model.id }
        }
    }

    fun downloadModel(model: OnDeviceModel = aiProviderManager.onDeviceProvider.selectedModel) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            aiProviderManager.onDeviceProvider.downloadModel(model)
            // Warm up immediately after download so the first inference is fast
            aiProviderManager.onDeviceProvider.warmUpModel()
        }
    }

    /** Cancels an in-progress download. The partial file is kept for resumption. */
    fun cancelDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null
    }

    /** Imports a model file the user picked via the system file picker (SAF).
     *  Bug fix: assigned to currentDownloadJob so cancelDownload() works during import too. */
    fun importModelFromUri(uri: Uri, model: OnDeviceModel = aiProviderManager.onDeviceProvider.selectedModel) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            aiProviderManager.onDeviceProvider.importFromUri(uri, model)
            // Warm up after import too
            aiProviderManager.onDeviceProvider.warmUpModel()
        }
    }

    fun deleteModel() {
        aiProviderManager.onDeviceProvider.deleteModel()
        _inferenceTestResult.value = null
    }

    // ── Inference test ───────────────────────────────────────────────────────

    /**
     * Runs a quick single-word inference test against the downloaded model.
     * Shows the result (or the real error) in the AI Settings card so the user
     * can confirm on-device AI is actually working before using it in popups.
     */
    fun testInference(testWord: String = "ephemeral") {
        _inferenceTestResult.value = InferenceTestResult.Testing
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val entry = try {
                aiProviderManager.onDeviceProvider.explainWord(testWord)
            } catch (e: Exception) {
                null
            }
            val durationMs = System.currentTimeMillis() - start
            _inferenceTestResult.value = if (entry != null && entry.shortMeaning.isNotBlank()) {
                InferenceTestResult.Success(testWord, entry.shortMeaning, durationMs)
            } else {
                val errStatus = aiProviderManager.onDeviceProvider.modelStatus.value
                val errMsg = (errStatus as? OnDeviceModelStatus.Error)?.message
                    ?: "Model returned empty output — check the log below."
                InferenceTestResult.Failure(errMsg)
            }
        }
    }

    fun clearInferenceTest() { _inferenceTestResult.value = null }

    fun clearDownloadLogs() { aiProviderManager.onDeviceProvider.clearLogs() }
}
