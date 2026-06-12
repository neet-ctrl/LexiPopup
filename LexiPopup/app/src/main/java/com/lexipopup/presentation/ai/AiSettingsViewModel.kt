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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private var currentDownloadJob: Job? = null

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
        }
    }

    /** Cancels an in-progress download. The partial file is kept for resumption. */
    fun cancelDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null
    }

    /** Imports a model file the user picked via the system file picker (SAF). */
    fun importModelFromUri(uri: Uri, model: OnDeviceModel = aiProviderManager.onDeviceProvider.selectedModel) {
        viewModelScope.launch {
            aiProviderManager.onDeviceProvider.importFromUri(uri, model)
        }
    }

    fun deleteModel() {
        aiProviderManager.onDeviceProvider.deleteModel()
    }
}
