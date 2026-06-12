package com.lexipopup.presentation.dashboard

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.presentation.about.AboutScreen
import com.lexipopup.presentation.dictionary.DictionaryBrowserScreen
import com.lexipopup.presentation.dictionary.DictionaryBrowserViewModel
import com.lexipopup.presentation.dictionary.SeedWordListScreen
import com.lexipopup.presentation.dictionary.SeedWordListViewModel
import com.lexipopup.presentation.dictionary.WordDetailScreen
import com.lexipopup.presentation.flashcards.FlashcardsScreen
import com.lexipopup.presentation.flashcards.FlashcardsViewModel
import com.lexipopup.presentation.history.WordHistoryScreen
import com.lexipopup.presentation.history.WordHistoryViewModel
import com.lexipopup.presentation.ai.AiSettingsScreen
import com.lexipopup.presentation.ai.AiSettingsViewModel
import com.lexipopup.presentation.download.DownloadProgressScreen
import com.lexipopup.utils.ExportFormat
import com.lexipopup.utils.SettingsDataStore
import com.lexipopup.workers.WotdNotificationWorker
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
    object AiSettings : AppDestination()
    object Backup : AppDestination()
    object WordHistory : AppDestination()
    object SeedWordList : AppDestination()
    object LookupLayers : AppDestination()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onRequestOverlayPermission: () -> Unit,
    hasOverlayPermission: Boolean,
    startWord: String? = null
) {
    val settings by viewModel.settings.collectAsState()
    val todayCount by viewModel.todayCount.collectAsState()
    val totalWordCount by viewModel.totalWordCount.collectAsState()
    val recentWords by viewModel.recentWords.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val mostSearched by viewModel.mostSearched.collectAsState()
    val weeklyStats by viewModel.weeklyStats.collectAsState()
    val activityData by viewModel.activityHeatmapData.collectAsState()
    val difficultyDistribution by viewModel.difficultyDistribution.collectAsState()
    val isHindiDisclaimerShown by viewModel.isHindiDisclaimerShown.collectAsState()

    val browserVm: DictionaryBrowserViewModel = hiltViewModel()
    val wordOfDay by browserVm.wordOfDay.collectAsState()

    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Home) }
    var showWotdDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Navigate to word detail when launched via "Full Details" from PopupActivity
    LaunchedEffect(startWord) {
        if (!startWord.isNullOrBlank()) destination = AppDestination.WordDetail(startWord)
    }

    // Hindi WordNet first-run non-commercial licensing acknowledgment (shown once)
    if (!isHindiDisclaimerShown) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("⚖️ Data Licensing Notice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LexiPopup uses these open data sources:", style = MaterialTheme.typography.bodyMedium)
                    Text("• Wiktionary — CC BY-SA 3.0 (free)", style = MaterialTheme.typography.bodySmall)
                    Text("• WordNet 3.1 — Princeton (free for all use)", style = MaterialTheme.typography.bodySmall)
                    Text("• Hindi WordNet — IIT Bombay CFILT, GNU FDL", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Hindi WordNet is licensed for non-commercial personal use only. By continuing you agree to use LexiPopup for personal, non-commercial purposes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.markHindiDisclaimerShown() }) {
                    Text("I Understand — Personal Use")
                }
            }
        )
    }

    // Word-of-day click → open detail
    val onWordSelected: (String) -> Unit = { word -> destination = AppDestination.WordDetail(word) }

    // Back logic
    val onBack: () -> Unit = {
        destination = when (destination) {
            is AppDestination.WordDetail  -> AppDestination.Home
            AppDestination.About         -> AppDestination.Settings
            AppDestination.DownloadPacks -> AppDestination.Settings
            AppDestination.AiSettings   -> AppDestination.Settings
            AppDestination.Backup        -> AppDestination.Settings
            AppDestination.WordHistory   -> AppDestination.Flashcards
            AppDestination.SeedWordList  -> AppDestination.Dictionary
            AppDestination.LookupLayers -> AppDestination.Settings
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
        AppDestination.AiSettings -> {
            val aiVm: AiSettingsViewModel = hiltViewModel()
            AiSettingsScreen(viewModel = aiVm, onBack = { destination = AppDestination.Settings })
            return
        }
        AppDestination.Backup -> {
            val backupVm: com.lexipopup.presentation.backup.BackupViewModel = hiltViewModel()
            com.lexipopup.presentation.backup.BackupRestoreScreen(viewModel = backupVm, onBack = { destination = AppDestination.Settings })
            return
        }
        AppDestination.WordHistory -> {
            val historyVm: WordHistoryViewModel = hiltViewModel()
            WordHistoryScreen(
                onBack = { destination = AppDestination.Flashcards },
                onWordSelected = { word -> destination = AppDestination.WordDetail(word) },
                viewModel = historyVm
            )
            return
        }
        AppDestination.SeedWordList -> {
            val seedVm: SeedWordListViewModel = hiltViewModel()
            SeedWordListScreen(
                viewModel = seedVm,
                onWordSelected = { word -> destination = AppDestination.WordDetail(word) },
                onBack = { destination = AppDestination.Dictionary }
            )
            return
        }
        AppDestination.LookupLayers -> {
            val layersVm: com.lexipopup.presentation.layers.LookupLayersViewModel = hiltViewModel()
            com.lexipopup.presentation.layers.LookupLayersScreen(
                viewModel = layersVm,
                onBack = { destination = AppDestination.Settings }
            )
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
                    totalWordCount = totalWordCount,
                    recentWords = recentWords,
                    favorites = favorites,
                    weeklyStats = weeklyStats,
                    wordOfDay = wordOfDay,
                    settings = settings,
                    onWordSelected = onWordSelected,
                    onNavigateToDictionary = { destination = AppDestination.Dictionary },
                    onSearchVoice = { query -> onWordSelected(query) },
                    onToggleWotdFavorite = { word -> viewModel.toggleWotdWordFavorite(word) },
                    onOpenWotdSettings = { showWotdDialog = true }
                )
                AppDestination.Dictionary -> DictionaryBrowserScreen(
                    viewModel = browserVm,
                    onWordSelected = onWordSelected,
                    onOpenSeedList = { destination = AppDestination.SeedWordList }
                )
                AppDestination.Flashcards -> {
                    val flashVm: FlashcardsViewModel = hiltViewModel()
                    val historyVm: WordHistoryViewModel = hiltViewModel()
                    val historyCount by historyVm.historyCount.collectAsState()
                    FlashcardsScreen(
                        viewModel = flashVm,
                        historyCount = historyCount,
                        onNavigateToHistory = { destination = AppDestination.WordHistory }
                    )
                }
                AppDestination.Stats -> StatsScreen(
                    todayCount = todayCount,
                    mostSearched = mostSearched,
                    weeklyStats = weeklyStats,
                    activityData = activityData,
                    difficultyDistribution = difficultyDistribution
                )
                AppDestination.Settings -> SettingsScreen(
                    settings = settings,
                    recentWords = recentWords,
                    viewModel = viewModel,
                    onManagePacks = { destination = AppDestination.DownloadPacks },
                    onOpenAiSettings = { destination = AppDestination.AiSettings },
                    onOpenBackup = { destination = AppDestination.Backup },
                    onOpenLookupLayers = { destination = AppDestination.LookupLayers }
                )
                else -> Unit
            }
        }
    }

    if (showWotdDialog) {
        WotdSettingsDialog(
            currentMode = settings.wotdMode,
            currentLevel = settings.wotdUserLevel,
            notifEnabled = settings.wotdNotificationEnabled,
            notifHour = settings.wotdNotificationHour,
            onDismiss = { showWotdDialog = false },
            onSave = { mode, level, notifEnabled, hour ->
                viewModel.updateWotdSettings(mode, level, notifEnabled, hour)
                if (notifEnabled) WotdNotificationWorker.reschedule(context, hour)
                else WotdNotificationWorker.cancel(context)
                showWotdDialog = false
            }
        )
    }
}

// ── HOME SCREEN ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    todayCount: Int,
    totalWordCount: Int,
    recentWords: List<WordEntry>,
    favorites: List<WordEntry>,
    weeklyStats: List<Pair<String, Int>>,
    wordOfDay: WordEntry?,
    settings: AppSettings,
    onWordSelected: (String) -> Unit,
    onNavigateToDictionary: () -> Unit,
    onSearchVoice: (String) -> Unit,
    onToggleWotdFavorite: (String) -> Unit = {},
    onOpenWotdSettings: () -> Unit = {}
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
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "In DB",
                    value = when {
                        totalWordCount >= 1_000_000 -> "%.1fM".format(totalWordCount / 1_000_000.0)
                        totalWordCount >= 1_000     -> "%.1fK".format(totalWordCount / 1_000.0)
                        else                        -> "$totalWordCount"
                    },
                    icon = Icons.Default.LibraryBooks,
                    color = MaterialTheme.colorScheme.tertiary
                )
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
                WotDHomeCard(
                    entry = wordOfDay!!,
                    onClick = { onWordSelected(wordOfDay!!.word) },
                    onAddToFavorites = { onToggleWotdFavorite(wordOfDay!!.word) },
                    onOpenSettings = onOpenWotdSettings
                )
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
private fun WotDHomeCard(
    entry: WordEntry,
    onClick: () -> Unit,
    onAddToFavorites: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val primary = MaterialTheme.colorScheme.primary
    val dateStr = remember {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
        java.time.LocalDate.now().format(fmt)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = primary.copy(0.07f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                    Column {
                        Text(
                            "WORD OF THE DAY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = primary,
                            letterSpacing = 1.sp
                        )
                        Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "WOTD Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = primary.copy(0.15f))

            // ── Word ──────────────────────────────────────────────────────────
            Text(
                entry.word.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ── POS chip + IPA ────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (entry.partOfSpeech.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(entry.partOfSpeechColor).copy(0.15f)) {
                        Text(
                            entry.partOfSpeech,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(entry.partOfSpeechColor),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (entry.pronunciation.isNotBlank()) {
                    Text(
                        entry.pronunciation,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Short meaning ─────────────────────────────────────────────────
            if (entry.shortMeaning.isNotBlank()) {
                Text(
                    "\"${entry.shortMeaning.take(120)}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            // ── Hindi meaning ─────────────────────────────────────────────────
            if (entry.hindiMeaning.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("\uD83C\uDDEE\uD83C\uDDF3", style = MaterialTheme.typography.bodySmall)
                    Column {
                        Text(entry.hindiMeaning.take(60), style = MaterialTheme.typography.bodySmall, color = primary.copy(0.85f), fontWeight = FontWeight.SemiBold)
                        if (entry.hindiPronunciation.isNotBlank()) {
                            Text("(${entry.hindiPronunciation.take(40)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Example sentence ──────────────────────────────────────────────
            if (entry.exampleSentence.isNotBlank()) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
                    Text(
                        "\"${entry.exampleSentence.take(160)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // ── "Why this word?" meta line ────────────────────────────────────
            Text(
                buildString {
                    append("Why this word? ")
                    if (entry.frequencyRating in 30..70) append("Frequency ${entry.frequencyRating}%")
                    else append("Freq ${entry.frequencyRating}%")
                    if (entry.difficultyLevel in 1..4) append(" · ${entry.difficultyLabel}")
                    append(" · ${entry.word.length} letters")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f)
            )

            // ── Action buttons ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Learn More", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onAddToFavorites,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = if (entry.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (entry.isFavorite) "Favorited" else "Favorite",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

// ── WOTD SETTINGS DIALOG ─────────────────────────────────────────────────────
@Composable
private fun WotdSettingsDialog(
    currentMode: String,
    currentLevel: Int,
    notifEnabled: Boolean,
    notifHour: Int,
    onDismiss: () -> Unit,
    onSave: (mode: String, level: Int, notifEnabled: Boolean, hour: Int) -> Unit
) {
    var mode by remember { mutableStateOf(currentMode) }
    var level by remember { mutableIntStateOf(currentLevel) }
    var enabled by remember { mutableStateOf(notifEnabled) }
    var hour by remember { mutableIntStateOf(notifHour) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\uD83D\uDCC5 Word of the Day Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                listOf(
                    "global"       to "Global (same word for everyone)",
                    "personalized" to "Personalized (by my level)",
                    "random"       to "Random (new on each refresh)"
                ).forEach { (m, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { mode = m }
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }

                AnimatedVisibility(visible = mode == "personalized") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("My Level", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        listOf(1 to "Beginner", 2 to "Intermediate", 3 to "Advanced", 4 to "Expert").forEach { (l, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { level = l }
                            ) {
                                RadioButton(selected = level == l, onClick = { level = l })
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily notification", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                AnimatedVisibility(visible = enabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("At", style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { if (hour > 0) hour-- }) {
                            Icon(Icons.Default.Remove, "Decrease hour")
                        }
                        Text(
                            "${hour.toString().padStart(2, '0')}:00",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { if (hour < 23) hour++ }) {
                            Icon(Icons.Default.Add, "Increase hour")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(mode, level, enabled, hour) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── STATS SCREEN (full page, replaces old StatsTab) ──────────────────────────
@Composable
fun StatsScreen(
    todayCount: Int,
    mostSearched: List<Pair<String, Int>>,
    weeklyStats: List<Pair<String, Int>>,
    activityData: Map<LocalDate, Int>,
    difficultyDistribution: Map<Int, Int> = emptyMap()
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
                DifficultyPieChart(distribution = difficultyDistribution)
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
    onManagePacks: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenLookupLayers: () -> Unit = {}
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }

    val ts = remember { System.currentTimeMillis() }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportVocabularyToUri(recentWords, ExportFormat.CSV, it) } }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportVocabularyToUri(recentWords, ExportFormat.JSON, it) } }

    val ankiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.exportVocabularyToUri(recentWords, ExportFormat.ANKI_TSV, it) } }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportSettingsToUri(it) } }

    LaunchedEffect(pendingExportFormat) {
        when (pendingExportFormat) {
            ExportFormat.CSV      -> { csvLauncher.launch("lexipopup_vocab_$ts.csv");   pendingExportFormat = null }
            ExportFormat.JSON     -> { jsonLauncher.launch("lexipopup_vocab_$ts.json");  pendingExportFormat = null }
            ExportFormat.ANKI_TSV -> { ankiLauncher.launch("lexipopup_anki_$ts.txt");   pendingExportFormat = null }
            null -> {}
        }
    }

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
                showExportDialog = false
                pendingExportFormat = format
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
        item { ToggleRow("Favorite ★ (header button)", settings.showFavoriteButton) { viewModel.updateSetting(SettingsDataStore.SHOW_FAVORITE, it) } }
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(
                    "Grid buttons — drag ⠿ to reorder, toggle to show/hide",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ButtonReorderPanel(settings = settings, viewModel = viewModel)
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🪟 Floating Window") }
        item { ToggleRow("Enable dragging",                     settings.enableDragging)      { viewModel.updateSetting(SettingsDataStore.ENABLE_DRAGGING,     it) } }
        item { ToggleRow("Enable resize handle (corner drag)",  settings.enableResizing)      { viewModel.updateSetting(SettingsDataStore.ENABLE_RESIZING,     it) } }
        item { ToggleRow("Edge-collapse (YouTube-style tab)",   settings.enableEdgeCollapse)  { viewModel.updateSetting(SettingsDataStore.ENABLE_EDGE_COLLAPSE, it) } }
        item { ToggleRow("Show collapse / minimize button",     settings.enableCollapseTooBubble) { viewModel.updateSetting(SettingsDataStore.ENABLE_BUBBLE,   it) } }
        item {
            // Default size presets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Default popup size",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Width ${(settings.popupWidthFraction * 100).toInt()}% · Height ${(settings.popupHeightFraction * 100).toInt()}% · Drag the corner ◢ handle on the popup to resize",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sizes = listOf(
                        "Compact" to (0.72f to 0.55f),
                        "Standard" to (0.88f to 0.65f),
                        "Large" to (0.94f to 0.80f)
                    )
                    sizes.forEach { (label, wh) ->
                        val (w, h) = wh
                        val active = (settings.popupWidthFraction - w).let { kotlin.math.abs(it) } < 0.05f
                        FilterChip(
                            selected  = active,
                            onClick   = {
                                viewModel.updateFloatSetting(SettingsDataStore.POPUP_WIDTH_FRACTION,  w)
                                viewModel.updateFloatSetting(SettingsDataStore.POPUP_HEIGHT_FRACTION, h)
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            ToggleRow(
                label = "Solid background (no transparency)",
                checked = settings.popupBgAlpha >= 1.0f,
                onCheckedChange = { solid ->
                    viewModel.updateFloatSetting(
                        SettingsDataStore.POPUP_BG_ALPHA,
                        if (solid) 1.0f else 0.5f
                    )
                }
            )
        }
        item { ToggleRow("Auto-close after 5 seconds", settings.autoCloseSeconds > 0) { viewModel.updateSetting(SettingsDataStore.AUTO_CLOSE, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🔔 Notification") }
        item { ToggleRow("Persistent notification (quick launch)", settings.showPersistentNotification) { viewModel.updateSetting(SettingsDataStore.SHOW_NOTIFICATION, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("📚 Vocabulary Tracking") }
        item { ToggleRow("Save search history", settings.saveSearchHistory) { viewModel.updateSetting(SettingsDataStore.SAVE_HISTORY, it) } }
        item { ToggleRow("Auto-generate flashcards", settings.autoGenerateFlashcards) { viewModel.updateSetting(SettingsDataStore.AUTO_FLASHCARDS, it) } }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("🔍 Lookup Layers") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Layer Control Dashboard",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Reorder · enable/disable · configure each lookup layer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = onOpenLookupLayers) {
                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Configure")
                    }
                }
            }
        }

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
        item { SectionHeader("🤖 AI Assistant") }
        item {
            val providerLabel = when (settings.aiProviderName) {
                "groq"      -> "Groq Cloud"
                "openai"    -> "OpenAI"
                "on_device" -> "On-Device AI"
                "hybrid"    -> "Hybrid (Both)"
                else        -> "Groq Cloud"
            }
            val isReady = when (settings.aiProviderName) {
                "groq"   -> settings.groqApiKey.isNotBlank()
                "openai" -> settings.openAiApiKey.isNotBlank()
                else     -> true
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Provider: $providerLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (isReady) "✅ Ready — AI fallback active" else "⚠️ API key required",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isReady) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    FilledTonalButton(onClick = onOpenAiSettings) {
                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Configure")
                    }
                }
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
                    settingsLauncher.launch("lexipopup_settings_${System.currentTimeMillis()}.json")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.SaveAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Export Settings as JSON")
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("💾 Backup & Restore") }
        item {
            OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Backup & Restore Vocabulary Data")
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
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
fun DifficultyPieChart(distribution: Map<Int, Int> = emptyMap()) {
    val colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFf44336))
    val labels = listOf("Beginner", "Intermediate", "Advanced", "Expert")

    val rawValues = (1..4).map { level -> (distribution[level] ?: 0).toFloat() }
    val total = rawValues.sum()
    val values = if (total > 0f) rawValues else listOf(25f, 25f, 25f, 25f)
    val displayTotal = if (total > 0f) total else 100f

    Canvas(modifier = Modifier.size(140.dp)) {
        var startAngle = -90f
        values.forEachIndexed { i, v ->
            val sweep = (v / displayTotal) * 360f
            drawArc(color = colors[i], startAngle = startAngle, sweepAngle = sweep, useCenter = true, size = Size(size.width, size.height))
            startAngle += sweep
        }
        drawCircle(color = Color.White, radius = size.minDimension * 0.3f)
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.zip(labels).forEachIndexed { i, (color, label) ->
            val count = rawValues[i].toInt()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Text(
                    if (count > 0) "$label ($count)" else label,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ── Button Reorder Panel ───────────────────────────────────────────────────────
// Drag-and-drop panel shown in Settings > Action Buttons.
// Long-press the ⠿ handle and drag up/down to reorder.
// Slot 1–5 = Row 1, slot 6–9 = Row 2, slot 10 = "More ⋯" overflow.
// The toggle switch shows/hides each button in the popup grid.

@Composable
private fun ButtonReorderPanel(
    settings: AppSettings,
    viewModel: DashboardViewModel
) {
    // ── Static metadata for each button ─────────────────────────────────────
    data class BtnMeta(
        val id: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val label: String,
        val settingsKey: androidx.datastore.preferences.core.Preferences.Key<Boolean>
    )

    fun isEnabled(id: String): Boolean = when (id) {
        "copy"      -> settings.showCopyButton
        "speak"     -> settings.showSpeakWordButton
        "meaning"   -> settings.showSpeakMeaningButton
        "translate" -> settings.showTranslateButton
        "share"     -> settings.showShareButton
        "note"      -> settings.showSaveNoteButton
        "details"   -> settings.showFullDetailsButton
        "web"       -> settings.showSearchWebButton
        "flashcard" -> settings.showFlashcardButton
        "browser"   -> settings.showBrowserButton
        else        -> false
    }

    val metaList = listOf(
        BtnMeta("copy",      Icons.Default.ContentCopy,     "Copy",            SettingsDataStore.SHOW_COPY),
        BtnMeta("speak",     Icons.Default.VolumeUp,        "Speak Word",      SettingsDataStore.SHOW_SPEAK_WORD),
        BtnMeta("meaning",   Icons.Default.RecordVoiceOver, "Speak Meaning",   SettingsDataStore.SHOW_SPEAK_MEANING),
        BtnMeta("translate", Icons.Default.Translate,       "Translate",       SettingsDataStore.SHOW_TRANSLATE),
        BtnMeta("share",     Icons.Default.Share,           "Share",           SettingsDataStore.SHOW_SHARE),
        BtnMeta("note",      Icons.Default.Edit,            "Save Note",       SettingsDataStore.SHOW_SAVE_NOTE),
        BtnMeta("details",   Icons.Default.MenuBook,        "Full Details",    SettingsDataStore.SHOW_FULL_DETAILS),
        BtnMeta("web",       Icons.Default.Language,        "Search Web",      SettingsDataStore.SHOW_SEARCH_WEB),
        BtnMeta("flashcard", Icons.Default.Style,           "Add Flashcard",   SettingsDataStore.SHOW_FLASHCARD_BTN),
        BtnMeta("browser",   Icons.Default.OpenInBrowser,   "Open in Browser", SettingsDataStore.SHOW_BROWSER_BTN)
    )
    val metaById = metaList.associateBy { it.id }

    // ── Order state — only tracks IDs; enabled state always read fresh ───────
    val defaultIds = listOf("copy","speak","meaning","translate","share","note","details","web","flashcard","browser")

    fun parseOrder(raw: String): List<String> {
        val saved = raw.split(",").map { it.trim() }.filter { it in metaById }
        return saved + (defaultIds - saved.toSet())
    }

    // Initialised once from settings; updated by drag; synced via LaunchedEffect on resets
    var orderedIds by remember { mutableStateOf(parseOrder(settings.buttonOrder)) }

    // Keep local state in sync when settings change externally (e.g. factory reset)
    LaunchedEffect(settings.buttonOrder) {
        val fresh = parseOrder(settings.buttonOrder)
        if (fresh != orderedIds) orderedIds = fresh
    }

    // ── Drag state ───────────────────────────────────────────────────────────
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY  by remember { mutableStateOf(0f) }
    val haptic     = LocalHapticFeedback.current
    val density    = LocalDensity.current
    val itemHeightPx = with(density) { 60.dp.toPx() }

    // ── Rendering ────────────────────────────────────────────────────────────
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Live mini-preview — NO pointerInput here, so its height doesn't
            //    confuse the drag index calculation below ──────────────────────
            ActionGridPreview(
                orderedIds = orderedIds,
                isEnabled  = ::isEnabled,
                getIcon    = { id -> metaById[id]?.icon ?: Icons.Default.GridView },
                getLabel   = { id -> metaById[id]?.label ?: id }
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // ── Draggable rows — pointerInput is scoped to ONLY this Column so
            //    offset.y == 0 at the very first row, giving correct indices ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(orderedIds) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val idx = (offset.y / itemHeightPx).toInt()
                                    .coerceIn(0, orderedIds.size - 1)
                                draggedIndex = idx
                                dragOffsetY  = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { _, dragAmount ->
                                val di = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                dragOffsetY += dragAmount.y
                                val target = (di + (dragOffsetY / itemHeightPx).roundToInt())
                                    .coerceIn(0, orderedIds.size - 1)
                                if (target != di) {
                                    orderedIds = orderedIds.toMutableList().also { list ->
                                        val removed = list.removeAt(di)
                                        list.add(target, removed)
                                    }
                                    dragOffsetY -= (target - di) * itemHeightPx
                                    draggedIndex = target
                                }
                            },
                            onDragEnd = {
                                draggedIndex = null
                                dragOffsetY  = 0f
                                viewModel.updateButtonOrder(orderedIds)
                            },
                            onDragCancel = {
                                draggedIndex = null
                                dragOffsetY  = 0f
                            }
                        )
                    }
            ) {

            orderedIds.forEachIndexed { index, id ->
                val meta      = metaById[id] ?: return@forEachIndexed
                val enabled   = isEnabled(id)
                val isDragging = draggedIndex == index

                // Row label: "Row 1" (slots 1-5), "Row 2" (slots 6-9), "More" (slot 10)
                val slotLabel = when {
                    index < 5 -> "Row 1"
                    index < 9 -> "Row 2"
                    else      -> "More"
                }
                val slotColor = when {
                    index < 5 -> MaterialTheme.colorScheme.primary
                    index < 9 -> MaterialTheme.colorScheme.secondary
                    else      -> MaterialTheme.colorScheme.tertiary
                }

                val rowModifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .zIndex(if (isDragging) 2f else 0f)
                    .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                    .then(
                        if (isDragging)
                            Modifier.shadow(8.dp, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .background(
                        if (isDragging)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )

                Row(
                    modifier  = rowModifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Drag handle
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint  = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (isDragging) 0.9f else 0.45f
                        ),
                        modifier = Modifier.size(22.dp)
                    )

                    // Slot badge
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = slotColor.copy(alpha = 0.13f)
                    ) {
                        Text(
                            slotLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = slotColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Button icon preview
                    Icon(
                        meta.icon,
                        contentDescription = null,
                        tint  = if (enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )

                    // Button label
                    Text(
                        meta.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Enable/disable switch
                    Switch(
                        checked  = enabled,
                        onCheckedChange = { viewModel.updateSetting(meta.settingsKey, it) },
                        modifier = Modifier.scale(0.80f)
                    )
                }

                // Thin divider between rows (not after last)
                if (index < orderedIds.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier  = Modifier.padding(start = 48.dp)
                    )
                }
            }
            } // close inner pointerInput Column
        }     // close outer Column
    }         // close Surface
}

// ── Action Grid Live Preview ───────────────────────────────────────────────────
// Renders a scaled-down, real-time thumbnail of exactly how the popup's
// 2-row button grid will appear with the current order and enabled states.

@Composable
private fun ActionGridPreview(
    orderedIds: List<String>,
    isEnabled: (String) -> Boolean,
    getIcon: (String) -> androidx.compose.ui.graphics.vector.ImageVector,
    getLabel: (String) -> String
) {
    // Compute which buttons land where — mirrors PopupActionGrid logic exactly
    val enabled  = orderedIds.filter { isEnabled(it) }
    val row1Ids  = enabled.take(5)
    val row2Ids  = enabled.drop(5).take(4)
    val hasMore  = enabled.size > 9
    val row1Count = row1Ids.size
    val row2Count = row2Ids.size + (if (hasMore) 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header: label + live stats badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    tint  = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Live Preview",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // Stats: "Row 1: 5  Row 2: 4  More: ●"
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewStatChip("R1", row1Count, 5, MaterialTheme.colorScheme.primary)
                PreviewStatChip("R2", row2Count, 5, MaterialTheme.colorScheme.secondary)
                if (hasMore) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "⋯ More",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Mini grid card — visually matches the real popup toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(10.dp),
            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row1Ids.forEach { id ->
                        MiniPreviewBtn(
                            icon    = getIcon(id),
                            label   = getLabel(id).replace("\n", " "),
                            enabled = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Empty slots (greyed dashes)
                    repeat(5 - row1Ids.size) { _ ->
                        MiniEmptySlot(modifier = Modifier.weight(1f))
                    }
                }

                // Row 2 (only if there are row-2 buttons or a More button)
                if (row2Ids.isNotEmpty() || hasMore) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row2Ids.forEach { id ->
                            MiniPreviewBtn(
                                icon    = getIcon(id),
                                label   = getLabel(id).replace("\n", " "),
                                enabled = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (hasMore) {
                            MiniPreviewBtn(
                                icon    = Icons.Default.MoreHoriz,
                                label   = "More",
                                enabled = true,
                                isMore  = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val filled = row2Ids.size + (if (hasMore) 1 else 0)
                        repeat(5 - filled) { _ ->
                            MiniEmptySlot(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Page dots — matches real popup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotActive   = MaterialTheme.colorScheme.primary
                    val dotInactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 2.dp)
                                .size(if (i == 0) 5.dp else 3.dp)
                                .clip(CircleShape)
                                .background(if (i == 0) dotActive else dotInactive)
                        )
                    }
                }
            }
        }

        // Caption: disabled buttons not shown in popup
        val disabledCount = orderedIds.count { !isEnabled(it) }
        if (disabledCount > 0) {
            Text(
                "$disabledCount button${if (disabledCount > 1) "s" else ""} hidden (toggle on to show)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PreviewStatChip(
    label: String,
    count: Int,
    max: Int,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.13f)
    ) {
        Text(
            "$label: $count/$max",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MiniPreviewBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isMore: Boolean = false
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isMore) secondary.copy(alpha = 0.12f)
                    else primary.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector  = icon,
                contentDescription = null,
                tint    = if (isMore) secondary else primary,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.8f else 0.35f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MiniEmptySlot(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                )
        )
        Text(
            "—",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            textAlign = TextAlign.Center
        )
    }
}
