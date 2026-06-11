package com.lexipopup.presentation.dashboard

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.presentation.about.AboutScreen
import com.lexipopup.presentation.dictionary.DictionaryBrowserScreen
import com.lexipopup.presentation.dictionary.DictionaryBrowserViewModel
import com.lexipopup.presentation.dictionary.WordDetailScreen
import com.lexipopup.presentation.flashcards.FlashcardsScreen
import com.lexipopup.presentation.flashcards.FlashcardsViewModel
import com.lexipopup.presentation.download.DownloadProgressScreen
import com.lexipopup.utils.ExportFormat
import com.lexipopup.utils.SettingsDataStore
import java.time.LocalDate

// ── Navigation destinations ──────────────────────────────────────────────────
sealed class AppDestination {
    object Home : AppDestination()
    object Dictionary : AppDestination()
    object Flashcards : AppDestination()
    object Stats : AppDestination()
    object Settings : AppDestination()
    data class WordDetail(val word: String) : AppDestination()
    object About : AppDestination()
    object DownloadPacks : AppDestination()
}

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

    val browserVm: DictionaryBrowserViewModel = hiltViewModel()
    val wordOfDay by browserVm.wordOfDay.collectAsState()

    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Home) }
    val context = LocalContext.current

    // Word-of-day click → open detail
    val onWordSelected: (String) -> Unit = { word -> destination = AppDestination.WordDetail(word) }

    // Back logic
    val onBack: () -> Unit = {
        destination = when (destination) {
            is AppDestination.WordDetail  -> AppDestination.Home
            AppDestination.About         -> AppDestination.Settings
            AppDestination.DownloadPacks -> AppDestination.Settings
            else -> AppDestination.Home
        }
    }

    // Overlays that cover full screen
    when (val d = destination) {
        is AppDestination.WordDetail -> {
            WordDetailScreen(
                word = d.word,
                onBack = { destination = AppDestination.Home },
                onSynonymClick = { onWordSelected(it) }
            )
            return
        }
        AppDestination.About -> {
            AboutScreen(onBack = { destination = AppDestination.Settings })
            return
        }
        AppDestination.DownloadPacks -> {
            DownloadProgressScreen(onBack = { destination = AppDestination.Settings })
            return
        }
        else -> Unit
    }

    val bottomNavItems = listOf(
        Triple(AppDestination.Home, Icons.Default.Home, "Home"),
        Triple(AppDestination.Dictionary, Icons.Default.MenuBook, "Dictionary"),
        Triple(AppDestination.Flashcards, Icons.Default.School, "Flashcards"),
        Triple(AppDestination.Stats, Icons.Default.BarChart, "Stats"),
        Triple(AppDestination.Settings, Icons.Default.Settings, "Settings")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (destination) {
                            AppDestination.Home -> "LexiPopup"
                            AppDestination.Dictionary -> "Dictionary"
                            AppDestination.Flashcards -> "Flashcards"
                            AppDestination.Stats -> "Statistics"
                            AppDestination.Settings -> "Settings"
                            else -> "LexiPopup"
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                actions = {
                    if (!hasOverlayPermission) {
                        IconButton(onClick = onRequestOverlayPermission) {
                            Icon(Icons.Default.Warning, "Need overlay permission", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (destination == AppDestination.Settings) {
                        IconButton(onClick = { destination = AppDestination.About }) {
                            Icon(Icons.Default.Info, "About & Licenses")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { (dest, icon, label) ->
                    NavigationBarItem(
                        selected = destination == dest,
                        onClick = { destination = dest },
                        icon = { Icon(icon, label) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                AppDestination.Home -> HomeScreen(
                    todayCount = todayCount,
                    recentWords = recentWords,
                    favorites = favorites,
                    weeklyStats = weeklyStats,
                    wordOfDay = wordOfDay,
                    settings = settings,
                    onWordSelected = onWordSelected,
                    onNavigateToDictionary = { destination = AppDestination.Dictionary },
                    onSearchVoice = { query -> onWordSelected(query) }
                )
                AppDestination.Dictionary -> DictionaryBrowserScreen(
                    viewModel = browserVm,
                    onWordSelected = onWordSelected
                )
                AppDestination.Flashcards -> {
                    val flashVm: FlashcardsViewModel = hiltViewModel()
                    FlashcardsScreen(viewModel = flashVm)
                }
                AppDestination.Stats -> StatsScreen(
                    todayCount = todayCount,
                    mostSearched = mostSearched,
                    weeklyStats = weeklyStats,
                    activityData = activityData
                )
                AppDestination.Settings -> SettingsScreen(
                    settings = settings,
                    recentWords = recentWords,
                    viewModel = viewModel,
                    context = context,
                    onManagePacks = { destination = AppDestination.DownloadPacks }
                )
                else -> Unit
            }
        }
    }
}

// ── HOME SCREEN ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    todayCount: Int,
    recentWords: List<WordEntry>,
    favorites: List<WordEntry>,
    weeklyStats: List<Pair<String, Int>>,
    wordOfDay: WordEntry?,
    settings: AppSettings,
    onWordSelected: (String) -> Unit,
    onNavigateToDictionary: () -> Unit,
    onSearchVoice: (String) -> Unit
) {
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { onSearchVoice(it) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Search bar (tap → goes to Dictionary tab) ──────────────────────
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(onClick = onNavigateToDictionary),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.7f)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Search any word or phrase…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a word to look up…")
                            }
                            speechLauncher.launch(intent)
                        }
                    ) {
                        Icon(Icons.Default.Mic, "Voice search", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // ── Stat cards row ─────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatMiniCard(modifier = Modifier.weight(1f), label = "Today", value = "$todayCount", icon = Icons.Default.Today, color = MaterialTheme.colorScheme.primary)
                StatMiniCard(modifier = Modifier.weight(1f), label = "Favorites", value = "${favorites.size}", icon = Icons.Default.Star, color = Color(0xFFFFC107))
                StatMiniCard(modifier = Modifier.weight(1f), label = "Total", value = "${recentWords.size}", icon = Icons.Default.LibraryBooks, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        // ── History disabled warning ─────────────────────────────────────
        if (!settings.saveSearchHistory) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HistoryToggleOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text("History disabled — enable in Settings → Vocabulary Tracking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // ── 🔥 Trending / Recent Searches ──────────────────────────────────
        if (recentWords.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔥  Recent Searches", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentWords.take(10)) { word ->
                            SuggestionChip(
                                onClick = { onWordSelected(word.word) },
                                label = { Text(word.word, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }

        // ── 📖 Word of the Day card ─────────────────────────────────────────
        if (wordOfDay != null) {
            item {
                WotDHomeCard(entry = wordOfDay!!, onClick = { onWordSelected(wordOfDay!!.word) })
            }
        }

        // ── 📊 Weekly chart ────────────────────────────────────────────────
        if (weeklyStats.any { it.second > 0 }) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📚  YOUR VOCABULARY  (Last 7 days)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        WeeklyBarChart(weeklyStats)
                    }
                }
            }
        }

        // ── ⭐ Favorites quick access ───────────────────────────────────────
        if (favorites.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐  FAVORITE WORDS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = onNavigateToDictionary) { Text("Browse all") }
                        }
                        favorites.take(5).forEach { fav ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onWordSelected(fav.word) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(fav.word, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        if (fav.partOfSpeech.isNotBlank()) {
                                            Text(fav.partOfSpeech, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (fav.shortMeaning.isNotBlank()) {
                                        Text("\"${fav.shortMeaning.take(70)}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                            }
                        }
                    }
                }
            }
        }

        // ── Empty state ────────────────────────────────────────────────────
        if (recentWords.isEmpty() && favorites.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                        Text("Your dictionary is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Long-press any word in Moon+ Reader → Dictionary → LexiPopup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        FilledTonalButton(onClick = onNavigateToDictionary) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Browse Dictionary")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatMiniCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WotDHomeCard(entry: WordEntry, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = primary.copy(0.08f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                    Text("WORD OF THE DAY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = primary)
                }
                Icon(Icons.Default.ArrowForward, null, tint = primary, modifier = Modifier.size(16.dp))
            }
            Text(entry.word.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (entry.partOfSpeech.isNotBlank()) Text(entry.partOfSpeech, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.pronunciation.isNotBlank()) Text(entry.pronunciation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
            Text(entry.shortMeaning.take(120), style = MaterialTheme.typography.bodyMedium)
            if (entry.hindiMeaning.isNotBlank()) {
                Text(entry.hindiMeaning.take(60), style = MaterialTheme.typography.bodySmall, color = primary.copy(0.8f))
            }
        }
    }
}

// ── STATS SCREEN (full page, replaces old StatsTab) ──────────────────────────
@Composable
fun StatsScreen(
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📅  Activity Heatmap (12 weeks)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                CalendarHeatmap(data = activityData)
            }
        }

        // Weekly bar chart
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊  Weekly Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                WeeklyBarChart(weeklyStats)
            }
        }

        // Most searched
        if (mostSearched.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔥  Most Searched", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        // Difficulty breakdown pie
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎯  Difficulty Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                DifficultyPieChart()
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── SETTINGS SCREEN (replaces old CustomizeTab) ───────────────────────────────
@Composable
fun SettingsScreen(
    settings: AppSettings,
    recentWords: List<WordEntry>,
    viewModel: DashboardViewModel,
    context: android.content.Context,
    onManagePacks: () -> Unit = {}
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings?") },
            text = { Text("This will restore all toggle settings to factory defaults.") },
            confirmButton = { TextButton(onClick = { viewModel.resetSettings(); showResetDialog = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                viewModel.exportVocabulary(recentWords, format, context)
                showExportDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { SectionHeader("🎨 Popup Layout") }
        item { ToggleRow("Pronunciation (IPA)", settings.showPronunciation) { viewModel.updateSetting(SettingsDataStore.SHOW_PRONUNCIATION, it) } }
        item { ToggleRow("Part of Speech chip", settings.showPartOfSpeech) { viewModel.updateSetting(SettingsDataStore.SHOW_POS, it) } }
        item { ToggleRow("Detailed meaning", settings.showDetailedMeaning) { viewModel.updateSetting(SettingsDataStore.SHOW_DETAILED, it) } }
        item { ToggleRow("Hindi meaning (हिंदी)", settings.showHindiMeaning) { viewModel.updateSetting(SettingsDataStore.SHOW_HINDI, it) } }
        item { ToggleRow("Example sentence", settings.showExampleSentence) { viewModel.updateSetting(SettingsDataStore.SHOW_EXAMPLE, it) } }
        item { ToggleRow("Synonyms row", settings.showSynonyms) { viewModel.updateSetting(SettingsDataStore.SHOW_SYNONYMS, it) } }
        item { ToggleRow("Antonyms row", settings.showAntonyms) { viewModel.updateSetting(SettingsDataStore.SHOW_ANTONYMS, it) } }
        item { ToggleRow("Word origin (Etymology)", settings.showEtymology) { viewModel.updateSetting(SettingsDataStore.SHOW_ETYMOLOGY, it) } }
        item { ToggleRow("Difficulty badge", settings.showDifficultyBadge) { viewModel.updateSetting(SettingsDataStore.SHOW_DIFFICULTY, it) } }
        item { ToggleRow("Frequency meter", settings.showFrequencyMeter) { viewModel.updateSetting(SettingsDataStore.SHOW_FREQUENCY, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🔘 Action Buttons") }
        item { ToggleRow("Copy button", settings.showCopyButton) { viewModel.updateSetting(SettingsDataStore.SHOW_COPY, it) } }
        item { ToggleRow("Speak word button", settings.showSpeakWordButton) { viewModel.updateSetting(SettingsDataStore.SHOW_SPEAK_WORD, it) } }
        item { ToggleRow("Speak meaning button", settings.showSpeakMeaningButton) { viewModel.updateSetting(SettingsDataStore.SHOW_SPEAK_MEANING, it) } }
        item { ToggleRow("Translate button", settings.showTranslateButton) { viewModel.updateSetting(SettingsDataStore.SHOW_TRANSLATE, it) } }
        item { ToggleRow("Share button", settings.showShareButton) { viewModel.updateSetting(SettingsDataStore.SHOW_SHARE, it) } }
        item { ToggleRow("Save note button", settings.showSaveNoteButton) { viewModel.updateSetting(SettingsDataStore.SHOW_SAVE_NOTE, it) } }
        item { ToggleRow("Favorite button", settings.showFavoriteButton) { viewModel.updateSetting(SettingsDataStore.SHOW_FAVORITE, it) } }
        item { ToggleRow("Full details button", settings.showFullDetailsButton) { viewModel.updateSetting(SettingsDataStore.SHOW_FULL_DETAILS, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🧩 Popup Behavior") }
        item { ToggleRow("Enable dragging", settings.enableDragging) { viewModel.updateSetting(SettingsDataStore.ENABLE_DRAGGING, it) } }
        item { ToggleRow("Enable resizing (bottom-right handle)", settings.enableResizing) { viewModel.updateSetting(SettingsDataStore.ENABLE_RESIZING, it) } }
        item { ToggleRow("Collapse to bubble mode", settings.enableCollapseTooBubble) { viewModel.updateSetting(SettingsDataStore.ENABLE_BUBBLE, it) } }
        item { ToggleRow("Auto-close after 5 seconds", settings.autoCloseSeconds > 0) { viewModel.updateSetting(SettingsDataStore.AUTO_CLOSE, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🔔 Notification") }
        item { ToggleRow("Persistent notification (quick launch)", settings.showPersistentNotification) { viewModel.updateSetting(SettingsDataStore.SHOW_NOTIFICATION, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("📚 Vocabulary Tracking") }
        item { ToggleRow("Save search history", settings.saveSearchHistory) { viewModel.updateSetting(SettingsDataStore.SAVE_HISTORY, it) } }
        item { ToggleRow("Auto-generate flashcards", settings.autoGenerateFlashcards) { viewModel.updateSetting(SettingsDataStore.AUTO_FLASHCARDS, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("📖 Dictionary Data") }
        item {
            OutlinedButton(
                onClick = onManagePacks,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Dictionary Packs (Wiktionary · WordNet · Hindi)")
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("📤 Export") }
        item {
            OutlinedButton(onClick = { showExportDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("Export Vocabulary (CSV / JSON / Anki)")
            }
        }
        item {
            OutlinedButton(
                onClick = {
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
                },
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

// ── Shared composables ────────────────────────────────────────────────────────

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
                    OutlinedButton(onClick = { onExport(format) }, modifier = Modifier.fillMaxWidth()) {
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
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val count = stats.find { it.first == days.indexOf(day).toString() }?.second ?: 0
                if (count > 0) Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
            }
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
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
