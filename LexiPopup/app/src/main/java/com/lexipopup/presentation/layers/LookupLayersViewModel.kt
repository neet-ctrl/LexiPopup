package com.lexipopup.presentation.layers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.CacheLayerConfig
import com.lexipopup.domain.models.GlobalLayerConfig
import com.lexipopup.domain.models.GroqAiLayerConfig
import com.lexipopup.domain.models.LayerSystemConfig
import com.lexipopup.domain.models.LookupPreset
import com.lexipopup.domain.models.OfflineDbLayerConfig
import com.lexipopup.domain.models.OnDeviceLayerConfig
import com.lexipopup.domain.models.OnlineApiLayerConfig
import com.lexipopup.domain.models.OpenAiLayerConfig
import com.lexipopup.domain.models.RuleBasedLayerConfig
import com.lexipopup.domain.usecases.LookupWordUseCase
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LookupLayersViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val lookupWordUseCase: LookupWordUseCase
) : ViewModel() {

    val layerConfig: StateFlow<LayerSystemConfig> = settingsDataStore.layerSystemConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LayerSystemConfig())

    private fun update(block: LayerSystemConfig.() -> LayerSystemConfig) {
        viewModelScope.launch {
            settingsDataStore.updateLayerSystemConfig(layerConfig.value.block())
        }
    }

    fun toggleLayer(layerId: String, enabled: Boolean) = update {
        copy(layerEnabled = layerEnabled.toMutableMap().apply { set(layerId, enabled) })
    }

    fun reorderLayers(fromIndex: Int, toIndex: Int) = update {
        val newOrder = layerOrder.toMutableList()
        newOrder.add(toIndex, newOrder.removeAt(fromIndex))
        copy(layerOrder = newOrder)
    }

    fun applyPreset(preset: LookupPreset) = update {
        copy(layerOrder = preset.order, layerEnabled = preset.enabled)
    }

    fun updateCacheConfig(config: CacheLayerConfig) = update { copy(cacheConfig = config) }
    fun updateOfflineDbConfig(config: OfflineDbLayerConfig) = update { copy(offlineDbConfig = config) }
    fun updateOnlineApiConfig(config: OnlineApiLayerConfig) = update { copy(onlineApiConfig = config) }
    fun updateGroqAiConfig(config: GroqAiLayerConfig) = update { copy(groqAiConfig = config) }
    fun updateOpenAiConfig(config: OpenAiLayerConfig) = update { copy(openAiConfig = config) }
    fun updateOnDeviceConfig(config: OnDeviceLayerConfig) = update { copy(onDeviceConfig = config) }
    fun updateRuleBasedConfig(config: RuleBasedLayerConfig) = update { copy(ruleBasedConfig = config) }
    fun updateGlobalConfig(config: GlobalLayerConfig) = update { copy(globalConfig = config) }

    fun clearMemoryCache() {
        lookupWordUseCase.clearMemoryCache()
    }

    fun exportConfigJson(): String = settingsDataStore.exportLayerConfig(layerConfig.value)

    fun importConfigJson(json: String): Boolean {
        return try {
            val config = settingsDataStore.parseLayerConfig(json) ?: return false
            viewModelScope.launch { settingsDataStore.updateLayerSystemConfig(config) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun resetToDefaults() = update { LayerSystemConfig() }
}
