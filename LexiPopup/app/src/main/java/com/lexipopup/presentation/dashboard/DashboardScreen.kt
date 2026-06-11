package com.lexipopup.presentation.dashboard

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.presentation.about.AboutScreen
import com.lexipopup.utils.ExportFormat
import com.lexipopup.utils.SettingsDataStore
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onRequestOverlayPermission: () -> Unit,
    hasOverlayPermission: Boolean
) {
    val settings by viewModel.settings.collectAsState()
    val todayCount by viewModel.todayCount.collectAsState()
    val recentWords by viewModel.recentWords.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val mostSearched by viewModel.mostSearched.collectAsState()
    val weeklyStats by viewModel.weeklyStats.collectAsState()
    val activityData by viewModel.activityHeatmapData.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    val tabs = listOf("Today", "Vocabulary", "Customize", "Stats")
    val context = LocalContext.current

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LexiPopup", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    if (!hasOverlayPermission) {
                        IconButton(onClick = onRequestOverlayPermission) {
                            Icon(Icons.Default.Warning, "Need overlay permission", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, "About & Licenses")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                indicator = { tabPositions ->
                    SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]))
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            when (selectedTab) {
                0 -> TodayTab(todayCount, recentWords, settings)
                1 -> VocabularyTab(recentWords, favorites, settings, viewModel, context)
                2 -> CustomizeTab(settings,
                    onSettingToggle = { key, value -> viewModel.updateSetting(key, value) },
                    onReset = viewModel::resetSettings,
                    onExportSettings = {
                        val uri = viewModel.exportSettingsUri()
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "LexiPopup Settings")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export settings"))
                        }
                    }
                )
                3 -> StatsTab(todayCount, mostSearched, weeklyStats, activityData)
            }
        }
    }
}

@Composable
fun TodayTab(todayCount: Int, recentWords: List<WordEntry>, settings: AppSettings) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Words Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("$todayCount", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                }
            }
        }

        if (!settings.saveSearchHistory) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HistoryToggleOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(
                            "History disabled — enable in Customize → Vocabulary Tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (recentWords.isNotEmpty()) {
            item { Text("Recently Looked Up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(recentWords.take(10)) { word -> WordListItem(word) }
        } else {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        Text("No words yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Long-press any word in Moon+ Reader → Dictionary → LexiPopup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Or tap the persistent notification to search any word", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun VocabularyTab(
    recentWords: List<WordEntry>,
    favorites: List<WordEntry>,
    settings: AppSettings,
    viewModel: DashboardViewModel,
    context: android.content.Context
) {
    var showFavorites by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val displayWords = if (showFavorites) favorites else recentWords

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                viewModel.exportVocabulary(recentWords, format, context)
                showExportDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showFavorites, onClick = { showFavorites = false }, label = { Text("All") })
                FilterChip(selected = showFavorites, onClick = { showFavorites = true }, label = { Text("Favorites ⭐") })
            }
            IconButton(onClick = { showExportDialog = true }) {
                Icon(Icons.Default.FileDownload, "Export", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (displayWords.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No words here yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayWords) { word -> WordListItem(word) }
            }
        }
    }
}

@Composable
fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportFormat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Vocabulary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose export format:", style = MaterialTheme.typography.bodyMedium)
                ExportFormat.entries.forEach { format ->
                    val (label, desc) = when (format) {
                        ExportFormat.CSV -> "CSV" to "Open in Excel, Google Sheets"
                        ExportFormat.JSON -> "JSON" to "For developers and backups"
                        ExportFormat.ANKI_TSV -> "Anki Deck (.txt)" to "Import into Anki flashcard app"
                    }
                    OutlinedButton(
                        onClick = { onExport(format) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(label, fontWeight = FontWeight.Bold)
                            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun WordListItem(entry: WordEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.word, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(entry.shortMeaning.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.hindiMeaning.isNotBlank()) {
                    Text(entry.hindiMeaning.take(50), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
            }
            if (entry.isFavorite) Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun CustomizeTab(
    settings: AppSettings,
    onSettingToggle: (androidx.datastore.preferences.core.Preferences.Key<Boolean>, Boolean) -> Unit,
    onReset: () -> Unit,
    onExportSettings: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings?") },
            text = { Text("This will restore all toggle settings to factory defaults.") },
            confirmButton = { TextButton(onClick = { onReset(); showResetDialog = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { SectionHeader("🎨 Popup Layout") }
        item { ToggleRow("Pronunciation (IPA)", settings.showPronunciation) { onSettingToggle(SettingsDataStore.SHOW_PRONUNCIATION, it) } }
        item { ToggleRow("Part of Speech chip", settings.showPartOfSpeech) { onSettingToggle(SettingsDataStore.SHOW_POS, it) } }
        item { ToggleRow("Detailed meaning", settings.showDetailedMeaning) { onSettingToggle(SettingsDataStore.SHOW_DETAILED, it) } }
        item { ToggleRow("Hindi meaning (हिंदी)", settings.showHindiMeaning) { onSettingToggle(SettingsDataStore.SHOW_HINDI, it) } }
        item { ToggleRow("Example sentence", settings.showExampleSentence) { onSettingToggle(SettingsDataStore.SHOW_EXAMPLE, it) } }
        item { ToggleRow("Synonyms row", settings.showSynonyms) { onSettingToggle(SettingsDataStore.SHOW_SYNONYMS, it) } }
        item { ToggleRow("Antonyms row", settings.showAntonyms) { onSettingToggle(SettingsDataStore.SHOW_ANTONYMS, it) } }
        item { ToggleRow("Word origin (Etymology)", settings.showEtymology) { onSettingToggle(SettingsDataStore.SHOW_ETYMOLOGY, it) } }
        item { ToggleRow("Difficulty badge", settings.showDifficultyBadge) { onSettingToggle(SettingsDataStore.SHOW_DIFFICULTY, it) } }
        item { ToggleRow("Frequency meter", settings.showFrequencyMeter) { onSettingToggle(SettingsDataStore.SHOW_FREQUENCY, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🔘 Action Buttons") }
        item { ToggleRow("Copy button", settings.showCopyButton) { onSettingToggle(SettingsDataStore.SHOW_COPY, it) } }
        item { ToggleRow("Speak word button", settings.showSpeakWordButton) { onSettingToggle(SettingsDataStore.SHOW_SPEAK_WORD, it) } }
        item { ToggleRow("Speak meaning button", settings.showSpeakMeaningButton) { onSettingToggle(SettingsDataStore.SHOW_SPEAK_MEANING, it) } }
        item { ToggleRow("Translate button", settings.showTranslateButton) { onSettingToggle(SettingsDataStore.SHOW_TRANSLATE, it) } }
        item { ToggleRow("Share button", settings.showShareButton) { onSettingToggle(SettingsDataStore.SHOW_SHARE, it) } }
        item { ToggleRow("Save note button", settings.showSaveNoteButton) { onSettingToggle(SettingsDataStore.SHOW_SAVE_NOTE, it) } }
        item { ToggleRow("Favorite button", settings.showFavoriteButton) { onSettingToggle(SettingsDataStore.SHOW_FAVORITE, it) } }
        item { ToggleRow("Full details button", settings.showFullDetailsButton) { onSettingToggle(SettingsDataStore.SHOW_FULL_DETAILS, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🧩 Popup Behavior") }
        item { ToggleRow("Enable dragging", settings.enableDragging) { onSettingToggle(SettingsDataStore.ENABLE_DRAGGING, it) } }
        item { ToggleRow("Enable resizing (bottom-right handle)", settings.enableResizing) { onSettingToggle(SettingsDataStore.ENABLE_RESIZING, it) } }
        item { ToggleRow("Collapse to bubble mode", settings.enableCollapseTooBubble) { onSettingToggle(SettingsDataStore.ENABLE_BUBBLE, it) } }
        item { ToggleRow("Auto-close after 5 seconds", settings.autoCloseSeconds > 0) { onSettingToggle(SettingsDataStore.AUTO_CLOSE, it) } }
        item { ToggleRow("Show on top of all apps (overlay)", true) { /* requires permission */ } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🔔 Notification Feature") }
        item { ToggleRow("Persistent notification (Quick-launch from anywhere)", settings.showPersistentNotification) { onSettingToggle(SettingsDataStore.SHOW_NOTIFICATION, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("📚 Vocabulary Tracking") }
        item { ToggleRow("Save search history", settings.saveSearchHistory) { onSettingToggle(SettingsDataStore.SAVE_HISTORY, it) } }
        item { ToggleRow("Track daily words", settings.saveSearchHistory) { onSettingToggle(SettingsDataStore.SAVE_HISTORY, it) } }
        item { ToggleRow("Auto-generate flashcards", settings.autoGenerateFlashcards) { onSettingToggle(SettingsDataStore.AUTO_FLASHCARDS, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            OutlinedButton(
                onClick = onExportSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Export Settings as JSON")
            }
        }
        item {
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(Icons.Default.RestartAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Reset to Defaults", color = MaterialTheme.colorScheme.error)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StatsTab(
    todayCount: Int,
    mostSearched: List<Pair<String, Int>>,
    weeklyStats: List<Pair<String, Int>>,
    activityData: Map<LocalDate, Int>
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Calendar heatmap
        Card(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(16.dp)) {
                CalendarHeatmap(data = activityData)
            }
        }

        // Weekly bar chart
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Weekly Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                WeeklyBarChart(weeklyStats)
            }
        }

        // Most searched
        if (mostSearched.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Most Searched", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val maxCount = mostSearched.maxOf { it.second }.coerceAtLeast(1)
                    mostSearched.forEach { (word, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(word, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            LinearProgressIndicator(
                                progress = { count.toFloat() / maxCount },
                                modifier = Modifier.weight(1f).height(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // Difficulty pie
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Difficulty Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                DifficultyPieChart()
            }
        }
    }
}

@Composable
fun WeeklyBarChart(stats: List<Pair<String, Int>>) {
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val maxVal = stats.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val barWidth = size.width / days.size
        val maxHeight = size.height * 0.8f
        days.forEachIndexed { i, _ ->
            val count = stats.find { it.first == i.toString() }?.second ?: 0
            val barHeight = (count.toFloat() / maxVal) * maxHeight
            val x = i * barWidth + barWidth * 0.15f
            drawRect(color = surface, topLeft = Offset(x, 0f), size = Size(barWidth * 0.7f, size.height))
            if (barHeight > 0f) {
                drawRect(color = primary, topLeft = Offset(x, size.height - barHeight), size = Size(barWidth * 0.7f, barHeight))
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        days.forEach { day ->
            Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DifficultyPieChart() {
    val colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFf44336))
    val labels = listOf("Beginner", "Intermediate", "Advanced", "Expert")
    val values = listOf(40f, 30f, 20f, 10f)

    Canvas(modifier = Modifier.size(140.dp)) {
        var startAngle = -90f
        values.forEachIndexed { i, v ->
            val sweep = v / 100f * 360f
            drawArc(color = colors[i], startAngle = startAngle, sweepAngle = sweep, useCenter = true, size = Size(size.width, size.height))
            startAngle += sweep
        }
        drawCircle(color = Color.White, radius = size.minDimension * 0.3f)
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.zip(labels).forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
