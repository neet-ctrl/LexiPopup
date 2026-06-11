package com.lexipopup.presentation.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.utils.SettingsDataStore
import kotlin.math.cos
import kotlin.math.sin

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

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Today", "Vocabulary", "Customize", "Stats")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("LexiPopup", fontWeight = FontWeight.ExtraBold)
                },
                actions = {
                    if (!hasOverlayPermission) {
                        IconButton(onClick = onRequestOverlayPermission) {
                            Icon(Icons.Default.Warning, "Need overlay permission", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> TodayTab(todayCount, recentWords)
                1 -> VocabularyTab(recentWords, favorites)
                2 -> CustomizeTab(settings, onSettingToggle = { key, value -> viewModel.updateSetting(key, value) },
                    onReset = viewModel::resetSettings)
                3 -> StatsTab(todayCount, mostSearched, weeklyStats)
            }
        }
    }
}

@Composable
fun TodayTab(todayCount: Int, recentWords: List<WordEntry>) {
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
                    Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                }
            }
        }

        if (recentWords.isNotEmpty()) {
            item { Text("Recently Looked Up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(recentWords.take(10)) { word ->
                WordListItem(word)
            }
        } else {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text("No words looked up yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Long-press any word in Moon+ Reader and tap Dictionary → LexiPopup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun VocabularyTab(recentWords: List<WordEntry>, favorites: List<WordEntry>) {
    var showFavorites by remember { mutableStateOf(false) }
    val displayWords = if (showFavorites) favorites else recentWords

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showFavorites, onClick = { showFavorites = false }, label = { Text("All") })
            FilterChip(selected = showFavorites, onClick = { showFavorites = true }, label = { Text("Favorites ⭐") })
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
fun WordListItem(entry: WordEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.word, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(entry.shortMeaning.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.isFavorite) Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun CustomizeTab(
    settings: AppSettings,
    onSettingToggle: (androidx.datastore.preferences.core.Preferences.Key<Boolean>, Boolean) -> Unit,
    onReset: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings?") },
            text = { Text("This will restore all toggle settings to their defaults.") },
            confirmButton = { TextButton(onClick = { onReset(); showResetDialog = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { Text("Popup Layout", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        item { ToggleRow("Show Pronunciation", settings.showPronunciation) { onSettingToggle(SettingsDataStore.SHOW_PRONUNCIATION, it) } }
        item { ToggleRow("Show Part of Speech", settings.showPartOfSpeech) { onSettingToggle(SettingsDataStore.SHOW_POS, it) } }
        item { ToggleRow("Show Detailed Meaning", settings.showDetailedMeaning) { onSettingToggle(SettingsDataStore.SHOW_DETAILED, it) } }
        item { ToggleRow("Show Hindi Meaning", settings.showHindiMeaning) { onSettingToggle(SettingsDataStore.SHOW_HINDI, it) } }
        item { ToggleRow("Show Example Sentence", settings.showExampleSentence) { onSettingToggle(SettingsDataStore.SHOW_EXAMPLE, it) } }
        item { ToggleRow("Show Synonyms", settings.showSynonyms) { onSettingToggle(SettingsDataStore.SHOW_SYNONYMS, it) } }
        item { ToggleRow("Show Antonyms", settings.showAntonyms) { onSettingToggle(SettingsDataStore.SHOW_ANTONYMS, it) } }
        item { ToggleRow("Show Etymology (Word Origin)", settings.showEtymology) { onSettingToggle(SettingsDataStore.SHOW_ETYMOLOGY, it) } }
        item { ToggleRow("Show Difficulty Badge", settings.showDifficultyBadge) { onSettingToggle(SettingsDataStore.SHOW_DIFFICULTY, it) } }
        item { ToggleRow("Show Frequency Meter", settings.showFrequencyMeter) { onSettingToggle(SettingsDataStore.SHOW_FREQUENCY, it) } }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Action Buttons", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { ToggleRow("Copy Button", settings.showCopyButton) { onSettingToggle(SettingsDataStore.SHOW_COPY, it) } }
        item { ToggleRow("Speak Word Button", settings.showSpeakWordButton) { onSettingToggle(SettingsDataStore.SHOW_SPEAK_WORD, it) } }
        item { ToggleRow("Speak Meaning Button", settings.showSpeakMeaningButton) { onSettingToggle(SettingsDataStore.SHOW_SPEAK_MEANING, it) } }
        item { ToggleRow("Translate Button", settings.showTranslateButton) { onSettingToggle(SettingsDataStore.SHOW_TRANSLATE, it) } }
        item { ToggleRow("Share Button", settings.showShareButton) { onSettingToggle(SettingsDataStore.SHOW_SHARE, it) } }
        item { ToggleRow("Save Note Button", settings.showSaveNoteButton) { onSettingToggle(SettingsDataStore.SHOW_SAVE_NOTE, it) } }
        item { ToggleRow("Favorite Button", settings.showFavoriteButton) { onSettingToggle(SettingsDataStore.SHOW_FAVORITE, it) } }
        item { ToggleRow("Full Details Button", settings.showFullDetailsButton) { onSettingToggle(SettingsDataStore.SHOW_FULL_DETAILS, it) } }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Popup Behavior", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { ToggleRow("Enable Dragging", settings.enableDragging) { onSettingToggle(SettingsDataStore.ENABLE_DRAGGING, it) } }
        item { ToggleRow("Enable Resizing", settings.enableResizing) { onSettingToggle(SettingsDataStore.ENABLE_RESIZING, it) } }
        item { ToggleRow("Collapse to Bubble", settings.enableCollapseTooBubble) { onSettingToggle(SettingsDataStore.ENABLE_BUBBLE, it) } }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Notification & Tracking", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { ToggleRow("Persistent Notification (Quick Search)", settings.showPersistentNotification) { onSettingToggle(SettingsDataStore.SHOW_NOTIFICATION, it) } }
        item { ToggleRow("Save Search History", settings.saveSearchHistory) { onSettingToggle(SettingsDataStore.SAVE_HISTORY, it) } }
        item { ToggleRow("Auto-Generate Flashcards", settings.autoGenerateFlashcards) { onSettingToggle(SettingsDataStore.AUTO_FLASHCARDS, it) } }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
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
fun StatsTab(todayCount: Int, mostSearched: List<Pair<String, Int>>, weeklyStats: List<Pair<String, Int>>) {
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Weekly Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                WeeklyBarChart(weeklyStats)
            }
        }

        if (mostSearched.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Most Searched", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val maxCount = mostSearched.maxOf { it.second }.coerceAtLeast(1)
                    mostSearched.forEach { (word, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(word, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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

        // Difficulty Pie Chart
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
            val y = size.height - barHeight
            drawRect(color = surface, topLeft = Offset(x, 0f), size = Size(barWidth * 0.7f, size.height))
            if (barHeight > 0f) {
                drawRect(color = primary, topLeft = Offset(x, y), size = Size(barWidth * 0.7f, barHeight))
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
    val values = listOf(40f, 30f, 20f, 10f) // demo values

    Canvas(modifier = Modifier.size(140.dp)) {
        var startAngle = -90f
        values.forEachIndexed { i, v ->
            val sweep = v / 100f * 360f
            drawArc(
                color = colors[i],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
        drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = size.minDimension * 0.3f)
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.zip(labels).forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
