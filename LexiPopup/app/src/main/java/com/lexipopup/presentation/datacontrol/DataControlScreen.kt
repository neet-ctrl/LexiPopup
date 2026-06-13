package com.lexipopup.presentation.datacontrol

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

// ─── Entry point ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataControlScreen(
    onBack: () -> Unit,
    viewModel: DataControlViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Data & Storage", fontWeight = FontWeight.Bold)
                        Text(
                            "Manage everything stored on this device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronRight,
                            contentDescription = "Back",
                            modifier = Modifier.size(28.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh sizes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading && state.totalBytes == 0L) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Summary header ──────────────────────────────────────────────
            item { StorageSummaryCard(state) }

            // ── Offline Database ────────────────────────────────────────────
            item {
                SectionLabel("📦 Offline Database")
            }
            item {
                DataCategoryCard(
                    icon = Icons.Default.Storage,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Dictionary Lookup Cache",
                    subtitle = "${state.lookupCacheNonSeedCount} fetched definitions stored",
                    sizeLabel = null,
                    dangerLevel = DangerLevel.Caution,
                    actionLabel = "Clear Cache",
                    actionDescription = "Removes all definitions fetched from the internet or AI. The built-in 1,000 seed words remain. Future lookups will re-fetch automatically.",
                    onAction = { viewModel.clearLookupCache() },
                    emptyText = if (state.lookupCacheNonSeedCount == 0) "Cache is already empty" else null
                )
            }

            // Dict pack files (only if any are present on disk)
            if (state.dictPacks.isNotEmpty()) {
                item {
                    DictPacksCard(
                        packs = state.dictPacks,
                        onDeletePack = { packName -> viewModel.deleteDictPack(packName) }
                    )
                }
            }

            // ── Learning Data ───────────────────────────────────────────────
            item { SectionLabel("🎓 Learning Data") }
            item {
                DataCategoryCard(
                    icon = Icons.Default.History,
                    iconTint = Color(0xFF0288D1),
                    title = "Search History",
                    subtitle = "${state.searchHistoryCount} word lookups recorded",
                    sizeLabel = null,
                    dangerLevel = DangerLevel.Caution,
                    actionLabel = "Clear History",
                    actionDescription = "Deletes all recorded word searches. Your activity heatmap and stats will reset.",
                    onAction = { viewModel.clearSearchHistory() },
                    emptyText = if (state.searchHistoryCount == 0) "History is empty" else null
                )
            }
            item {
                DataCategoryCard(
                    icon = Icons.Default.School,
                    iconTint = Color(0xFF7B1FA2),
                    title = "Flashcard Deck",
                    subtitle = "${state.flashcardCount} cards with SM-2 progress",
                    sizeLabel = null,
                    dangerLevel = DangerLevel.Destructive,
                    actionLabel = "Delete All Cards",
                    actionDescription = "Permanently deletes all ${state.flashcardCount} flashcards and their spaced-repetition progress. This cannot be undone.",
                    onAction = { viewModel.clearFlashcards() },
                    emptyText = if (state.flashcardCount == 0) "No flashcards saved" else null
                )
            }
            item {
                DataCategoryCard(
                    icon = Icons.Default.Favorite,
                    iconTint = Color(0xFFE91E63),
                    title = "Favorites",
                    subtitle = "${state.favoritesCount} saved words",
                    sizeLabel = null,
                    dangerLevel = DangerLevel.Destructive,
                    actionLabel = "Remove All Favorites",
                    actionDescription = "Clears your entire favorites list (${state.favoritesCount} words). Cannot be undone.",
                    onAction = { viewModel.clearFavorites() },
                    emptyText = if (state.favoritesCount == 0) "No favorites saved" else null
                )
            }
            item {
                DataCategoryCard(
                    icon = Icons.Default.Note,
                    iconTint = Color(0xFF00796B),
                    title = "Personal Notes",
                    subtitle = "${state.notesCount} notes written",
                    sizeLabel = null,
                    dangerLevel = DangerLevel.Destructive,
                    actionLabel = "Delete All Notes",
                    actionDescription = "Permanently removes all ${state.notesCount} personal notes attached to words.",
                    onAction = { viewModel.clearNotes() },
                    emptyText = if (state.notesCount == 0) "No notes written" else null
                )
            }
            item {
                DataCategoryCard(
                    icon = Icons.Default.AutoAwesome,
                    iconTint = Color(0xFFF57C00),
                    title = "Random Word Queue",
                    subtitle = "${state.randomWordCount} AI-generated words",
                    sizeLabel = null,
                    dangerLevel = DangerLevel.Caution,
                    actionLabel = "Clear Queue",
                    actionDescription = "Removes all AI-generated random words. The widget will fetch new ones automatically.",
                    onAction = { viewModel.clearRandomWords() },
                    emptyText = if (state.randomWordCount == 0) "Queue is empty" else null
                )
            }

            // ── AI & Chat ───────────────────────────────────────────────────
            item { SectionLabel("🤖 AI & Chat") }
            item {
                DataCategoryCard(
                    icon = Icons.Default.Chat,
                    iconTint = Color(0xFF1565C0),
                    title = "Chat History",
                    subtitle = "${state.chatSessionCount} sessions · ${state.chatMessageCount} messages",
                    sizeLabel = null,
                    dangerLevel = DangerLevel.Destructive,
                    actionLabel = "Delete All Chats",
                    actionDescription = "Permanently deletes all ${state.chatSessionCount} chat sessions and ${state.chatMessageCount} messages.",
                    onAction = { viewModel.clearChatHistory() },
                    emptyText = if (state.chatSessionCount == 0) "No chat history" else null
                )
            }

            // On-device AI model files
            item {
                AiModelsCard(
                    models = state.aiModelFiles,
                    onDeleteModel = { fileName -> viewModel.deleteAiModel(fileName) }
                )
            }

            // ── Cache ───────────────────────────────────────────────────────
            item { SectionLabel("🗂 Cache & Temp Files") }
            item {
                DataCategoryCard(
                    icon = Icons.Default.FolderOpen,
                    iconTint = Color(0xFF455A64),
                    title = "App Cache",
                    subtitle = "Temporary files, image thumbnails, network buffers",
                    sizeLabel = formatBytes(state.appCacheBytes),
                    dangerLevel = DangerLevel.Safe,
                    actionLabel = "Clear Cache",
                    actionDescription = "Deletes all temporary cached files (${formatBytes(state.appCacheBytes)}). No user data is lost — the cache rebuilds automatically.",
                    onAction = { viewModel.clearAppCache() },
                    emptyText = if (state.appCacheBytes == 0L) "Cache is empty" else null
                )
            }

            // ── Danger zone ─────────────────────────────────────────────────
            item { SectionLabel("⚠️ Danger Zone") }
            item { NuclearCard(onWipe = { viewModel.wipeAllUserData() }) }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─── Storage summary ──────────────────────────────────────────────────────────

@Composable
private fun StorageSummaryCard(state: DataControlUiState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Dashboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            "Total Storage Used",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            formatBytes(state.totalBytes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                val segments = buildList {
                    if (state.dbBytes > 0) add("Database" to state.dbBytes)
                    if (state.aiModelFiles.isNotEmpty()) add("AI Models" to state.aiModelFiles.sumOf { it.bytes })
                    if (state.dictPacks.isNotEmpty()) add("Dict Packs" to state.dictPacks.sumOf { it.bytes })
                    if (state.appCacheBytes > 0) add("Cache" to state.appCacheBytes)
                }
                val total = segments.sumOf { it.second }.coerceAtLeast(1)

                val segmentColors = listOf(
                    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF7C00), Color(0xFF78909C)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    segments.forEachIndexed { i, (label, bytes) ->
                        StorageSegmentRow(
                            label = label,
                            bytes = bytes,
                            fraction = bytes.toFloat() / total,
                            color = segmentColors[i % segmentColors.size]
                        )
                    }
                    if (segments.isEmpty()) {
                        Text(
                            "No significant data stored yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSegmentRow(label: String, bytes: Long, fraction: Float, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.width(90.dp)
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round
        )
        Text(
            formatBytes(bytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        )
    }
}

// ─── Section label ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 2.dp)
    )
}

// ─── Danger levels ────────────────────────────────────────────────────────────

enum class DangerLevel { Safe, Caution, Destructive }

// ─── Generic data category card ───────────────────────────────────────────────

@Composable
private fun DataCategoryCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    sizeLabel: String?,
    dangerLevel: DangerLevel,
    actionLabel: String,
    actionDescription: String,
    onAction: () -> Unit,
    emptyText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        ConfirmDeleteDialog(
            title = actionLabel,
            description = actionDescription,
            dangerLevel = dangerLevel,
            onConfirm = { showDialog = false; onAction() },
            onDismiss = { showDialog = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconTint.copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sizeLabel != null) {
                        Text(
                            sizeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable action panel
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            actionDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (emptyText != null) {
                            Text(
                                emptyText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically).weight(1f)
                            )
                        }
                        val btnColor = when (dangerLevel) {
                            DangerLevel.Safe -> MaterialTheme.colorScheme.secondaryContainer
                            DangerLevel.Caution -> Color(0xFFFF8F00).copy(alpha = 0.15f)
                            DangerLevel.Destructive -> MaterialTheme.colorScheme.errorContainer
                        }
                        val btnTextColor = when (dangerLevel) {
                            DangerLevel.Safe -> MaterialTheme.colorScheme.onSecondaryContainer
                            DangerLevel.Caution -> Color(0xFFE65100)
                            DangerLevel.Destructive -> MaterialTheme.colorScheme.error
                        }
                        Button(
                            onClick = {
                                if (dangerLevel == DangerLevel.Safe && emptyText == null) onAction()
                                else if (emptyText == null) showDialog = true
                            },
                            enabled = emptyText == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = btnColor,
                                contentColor = btnTextColor,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

// ─── Dict packs card ──────────────────────────────────────────────────────────

@Composable
private fun DictPacksCard(
    packs: List<DictPackInfo>,
    onDeletePack: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DictPackInfo?>(null) }

    pendingDelete?.let { pack ->
        ConfirmDeleteDialog(
            title = "Remove ${pack.displayName}?",
            description = "This removes the downloaded dictionary pack file (${formatBytes(pack.bytes)}). It was a partial/in-progress download. You can re-download it anytime.",
            dangerLevel = DangerLevel.Caution,
            onConfirm = { onDeletePack(pack.packName); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CloudOff, contentDescription = null,
                            tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Dictionary Pack Files", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${packs.size} partial/in-progress file${if (packs.size > 1) "s" else ""} on disk · ${formatBytes(packs.sumOf { it.bytes })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    packs.forEachIndexed { i, pack ->
                        if (i > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pack.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        formatBytes(pack.bytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (pack.isPartial) {
                                        Text(
                                            "· partial download",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }
                            }
                            FilledTonalButton(
                                onClick = { pendingDelete = pack },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// ─── AI models card ───────────────────────────────────────────────────────────

@Composable
private fun AiModelsCard(
    models: List<AiModelFileInfo>,
    onDeleteModel: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AiModelFileInfo?>(null) }

    pendingDelete?.let { model ->
        ConfirmDeleteDialog(
            title = "Delete ${model.displayName}?",
            description = "This permanently deletes the on-device AI model file (${formatBytes(model.bytes)}). You can re-import it anytime via AI Settings → Import .gguf.",
            dangerLevel = DangerLevel.Caution,
            onConfirm = { onDeleteModel(model.fileName); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF4A148C).copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SmartToy, contentDescription = null,
                            tint = Color(0xFF6A1B9A), modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("On-Device AI Models", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (models.isEmpty()) {
                        Text(
                            "No local model installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "${models.size} model${if (models.size > 1) "s" else ""} · ${formatBytes(models.sumOf { it.bytes })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (models.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && models.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    models.forEachIndexed { i, model ->
                        if (i > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFF6A1B9A),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "${model.fileName} · ${formatBytes(model.bytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { pendingDelete = model },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// ─── Nuclear wipe card ────────────────────────────────────────────────────────

@Composable
private fun NuclearCard(onWipe: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Wipe All User Data?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will permanently delete:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val items = listOf(
                        "All fetched word definitions (AI + internet)",
                        "Complete search history",
                        "All flashcards & SM-2 progress",
                        "All favorites",
                        "All personal notes",
                        "All AI chat sessions",
                        "Random word queue",
                        "App cache",
                        "Downloaded dictionary pack files",
                        "On-device AI model files"
                    )
                    items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.error)
                            Text(item, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "The built-in 1,000 seed words and all app settings are kept. This action cannot be undone.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onWipe(); showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Yes, Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Wipe All User Data",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "History · flashcards · favorites · notes · chats · AI model · packs · cache",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            OutlinedButton(
                onClick = { showDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Wipe All", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─── Confirmation dialog ──────────────────────────────────────────────────────

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    description: String,
    dangerLevel: DangerLevel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            val icon = when (dangerLevel) {
                DangerLevel.Safe -> Icons.Default.DeleteSweep
                DangerLevel.Caution -> Icons.Default.Delete
                DangerLevel.Destructive -> Icons.Default.Warning
            }
            val tint = when (dangerLevel) {
                DangerLevel.Safe -> MaterialTheme.colorScheme.primary
                DangerLevel.Caution -> Color(0xFFE65100)
                DangerLevel.Destructive -> MaterialTheme.colorScheme.error
            }
            Icon(icon, contentDescription = null, tint = tint)
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(description, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            val btnColor = when (dangerLevel) {
                DangerLevel.Safe -> MaterialTheme.colorScheme.primary
                DangerLevel.Caution -> Color(0xFFE65100)
                DangerLevel.Destructive -> MaterialTheme.colorScheme.error
            }
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = btnColor)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Utilities ────────────────────────────────────────────────────────────────

internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0L       -> "0 B"
    bytes < 1_024L    -> "$bytes B"
    bytes < 1_048_576L -> "${(bytes / 1024.0).roundToInt()} KB"
    bytes < 1_073_741_824L -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    else              -> "${"%.2f".format(bytes / 1_073_741_824.0)} GB"
}
