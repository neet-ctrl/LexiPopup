package com.lexipopup.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lexipopup.domain.models.AppMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModeManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val ACTIVE_MODE_KEY = stringPreferencesKey("active_app_mode")
    }

    private val _currentMode = MutableStateFlow(AppMode.ENGLISH)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                _currentMode.value = AppMode.fromId(prefs[ACTIVE_MODE_KEY] ?: AppMode.ENGLISH.id)
            }
        }
    }

    fun setMode(mode: AppMode) {
        _currentMode.value = mode
        scope.launch {
            dataStore.edit { prefs -> prefs[ACTIVE_MODE_KEY] = mode.id }
        }
    }
}
