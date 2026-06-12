package com.lexipopup.presentation.biosettings

import androidx.lifecycle.ViewModel
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BioSettingsViewModel @Inject constructor(
    val settingsDataStore: SettingsDataStore
) : ViewModel()
