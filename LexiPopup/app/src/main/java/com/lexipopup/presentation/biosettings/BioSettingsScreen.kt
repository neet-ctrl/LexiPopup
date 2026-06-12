package com.lexipopup.presentation.biosettings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.utils.SettingsDataStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioSettingsScreen(
    settingsDataStore: SettingsDataStore? = null,
    settings: AppSettings? = null,
    onBack: () -> Unit,
    vm: BioSettingsViewModel = hiltViewModel()
) {
    val dsStore = settingsDataStore ?: vm.settingsDataStore
    val appSettings by dsStore.settings.collectAsState(initial = AppSettings())
    val currentSettings = settings ?: appSettings
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🧬", style = MaterialTheme.typography.titleLarge)
                        Text("Biology Settings", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Reset button ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = { scope.launch { dsStore.resetBioSettingsToDefaults() } },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Biology Defaults")
            }

            // ── Term of Day ───────────────────────────────────────────────────
            BioSettingSection(title = "Term of the Day") {
                BioToggleRow(
                    label = "Enable TOTD notification",
                    icon = "🔔",
                    checked = currentSettings.totdNotificationEnabled,
                    onToggle = { v ->
                        scope.launch {
                            dsStore.update { prefs ->
                                prefs[SettingsDataStore.TOTD_NOTIFICATION_ENABLED] = v
                            }
                        }
                    }
                )
                if (currentSettings.totdNotificationEnabled) {
                    BioSliderRow(
                        label = "Notification time",
                        icon = "⏰",
                        value = currentSettings.totdNotificationHour.toFloat(),
                        valueRange = 5f..22f,
                        steps = 16,
                        display = { "${it.toInt()}:00" },
                        onValueChange = { v ->
                            scope.launch {
                                dsStore.update { prefs ->
                                    prefs[SettingsDataStore.TOTD_NOTIFICATION_HOUR] = v.toInt()
                                }
                            }
                        }
                    )
                }
            }

            // ── Popup Content ─────────────────────────────────────────────────
            BioSettingSection(title = "Popup Display") {
                BioToggleRow("Show pronunciation", "🔊", currentSettings.bioShowPronunciation) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_PRONUNCIATION] = v } }
                }
                BioToggleRow("Show category badge", "🏷️", currentSettings.bioShowCategory) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_CATEGORY] = v } }
                }
                BioToggleRow("Show definition", "📖", currentSettings.bioShowDefinition) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_DEFINITION] = v } }
                }
                BioToggleRow("Show Hindi name", "🇮🇳", currentSettings.bioShowHindi) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_HINDI] = v } }
                }
                BioToggleRow("Show example context", "💬", currentSettings.bioShowExample) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_EXAMPLE] = v } }
                }
                BioToggleRow("Show scientific classification", "🔬", currentSettings.bioShowClassification) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_CLASSIFICATION] = v } }
                }
                BioToggleRow("Show functions", "⚙️", currentSettings.bioShowFunctions) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_FUNCTIONS] = v } }
                }
                BioToggleRow("Show structure", "🏗️", currentSettings.bioShowStructure) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_STRUCTURE] = v } }
                }
                BioToggleRow("Show related terms", "🔗", currentSettings.bioShowRelatedTerms) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_RELATED_TERMS] = v } }
                }
                BioToggleRow("Show diseases / disorders", "🏥", currentSettings.bioShowDiseases) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_DISEASES] = v } }
                }
                BioToggleRow("Show etymology", "📜", currentSettings.bioShowEtymology) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_ETYMOLOGY] = v } }
                }
                BioToggleRow("Show difficulty badge", "🎯", currentSettings.bioShowDifficulty) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_DIFFICULTY] = v } }
                }
                BioToggleRow("Show frequency", "📊", currentSettings.bioShowFrequency) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_FREQUENCY] = v } }
                }
            }

            // ── Action Buttons ────────────────────────────────────────────────
            BioSettingSection(title = "Action Buttons") {
                BioToggleRow("Copy button", "📋", currentSettings.bioShowCopyButton) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_COPY_BTN] = v } }
                }
                BioToggleRow("Speak button", "🔊", currentSettings.bioShowSpeakButton) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_SPEAK_BTN] = v } }
                }
                BioToggleRow("Share button", "📤", currentSettings.bioShowShareButton) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_SHARE_BTN] = v } }
                }
                BioToggleRow("Favourite button", "⭐", currentSettings.bioShowFavoriteButton) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_FAVORITE_BTN] = v } }
                }
                BioToggleRow("Search web button", "🌐", currentSettings.bioShowSearchWebButton) { v ->
                    scope.launch { dsStore.update { it[SettingsDataStore.BIO_SHOW_SEARCH_WEB_BTN] = v } }
                }
            }

            // ── About Biology Mode ────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "🧬 About Biology Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Biology mode uses the same AI key (Groq or OpenAI) as English mode, but sends biology-specific prompts. It returns scientific classification, functions, structure, associated diseases, related terms, difficulty and frequency data for each term.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BioSettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun BioToggleRow(
    label: String,
    icon: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, style = MaterialTheme.typography.bodyMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun BioSliderRow(
    label: String,
    icon: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableStateOf(value) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(icon)
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            Text(display(sliderValue), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = valueRange,
            steps = steps
        )
    }
}
