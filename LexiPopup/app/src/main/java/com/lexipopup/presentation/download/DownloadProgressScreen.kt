package com.lexipopup.presentation.download

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.data.download.DatabasePack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadProgressScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val packStates by viewModel.packStates.collectAsState()
    val installedCount by viewModel.installedCount.collectAsState()
    val states = packStates.values.toList()
    val anyActive = states.any {
        it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING,
            DownloadStatus.VERIFYING, DownloadStatus.IMPORTING)
    }
    val allInstalled = states.all { it.status == DownloadStatus.INSTALLED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dictionary Packs", fontWeight = FontWeight.Bold)
                        Text(
                            "$installedCount of 3 installed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (!allInstalled) {
                Surface(shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!anyActive) {
                            Button(
                                onClick = viewModel::downloadAll,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Download All Packs (${DatabasePack.entries.sumOf { it.sizeMb }}MB total)")
                            }
                        }
                        Text(
                            "Wiktionary (CC BY-SA 3.0) · WordNet (Princeton) · Hindi WordNet (CFILT IIT Bombay, GNU FDL — non-commercial use only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // First-run banner when nothing installed
            if (installedCount == 0 && !anyActive) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoStories, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp))
                            Column {
                                Text("No dictionary installed",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall)
                                Text("Choose a pack below. Downloads once, works 100% offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }

            items(DatabasePack.entries) { pack ->
                val state = packStates[pack] ?: PackDownloadUiState(pack)
                PackDownloadCard(
                    state = state,
                    onDownload = { viewModel.startDownload(pack) },
                    onCancel   = { viewModel.cancelDownload(pack) },
                    onDelete   = { viewModel.deletePack(pack) }
                )
            }
        }
    }
}

@Composable
private fun PackDownloadCard(
    state: PackDownloadUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val progressAnim by animateFloatAsState(
        targetValue = state.progress / 100f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "pack_progress"
    )

    val (statusColor, statusLabel, statusIcon) = when (state.status) {
        DownloadStatus.NOT_INSTALLED -> Triple(Color(0xFF9E9E9E), "Not installed", Icons.Default.CloudOff)
        DownloadStatus.QUEUED        -> Triple(Color(0xFFFF9800), "Queued…",       Icons.Default.HourglassEmpty)
        DownloadStatus.DOWNLOADING   -> Triple(Color(0xFF2196F3), "Downloading",   Icons.Default.CloudDownload)
        DownloadStatus.VERIFYING     -> Triple(Color(0xFF9C27B0), "Verifying…",    Icons.Default.VerifiedUser)
        DownloadStatus.IMPORTING     -> Triple(Color(0xFF009688), "Importing…",    Icons.Default.Storage)
        DownloadStatus.INSTALLED     -> Triple(Color(0xFF4CAF50), "Installed ✓",   Icons.Default.CheckCircle)
        DownloadStatus.FAILED        -> Triple(Color(0xFFF44336), "Failed",        Icons.Default.Error)
    }

    val packAccent = when (state.pack) {
        DatabasePack.MINIMAL  -> Color(0xFF4CAF50)
        DatabasePack.STANDARD -> Color(0xFF2196F3)
        DatabasePack.FULL     -> Color(0xFF9C27B0)
    }

    // Log tray expanded state — auto-expand when downloading starts
    var logExpanded by remember { mutableStateOf(false) }
    val hasLog = state.downloadLog.isNotBlank()
    val isActive = state.status in listOf(
        DownloadStatus.DOWNLOADING, DownloadStatus.VERIFYING, DownloadStatus.IMPORTING
    )
    LaunchedEffect(isActive) {
        if (isActive) logExpanded = true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── Header row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.pack.displayName, fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = packAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                state.pack.wordCount,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = packAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(state.pack.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${state.pack.sizeMb}MB",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(statusIcon, null, modifier = Modifier.size(12.dp), tint = statusColor)
                            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                        }
                    }
                }
            }

            // ── Progress bar (active states only) ───────────────────────────
            AnimatedVisibility(
                visible = state.status in listOf(
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING,
                    DownloadStatus.VERIFYING, DownloadStatus.IMPORTING
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            when (state.status) {
                                DownloadStatus.QUEUED      -> "Queued — waiting for other download…"
                                DownloadStatus.DOWNLOADING -> buildDownloadLabel(state)
                                DownloadStatus.VERIFYING   -> "Verifying checksum…"
                                DownloadStatus.IMPORTING   -> "Importing ${formatNum(state.wordsImported)} words…"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("${state.progress}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor)
                    }

                    if (state.status == DownloadStatus.QUEUED) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            color = statusColor
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progressAnim },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            color = statusColor,
                            trackColor = statusColor.copy(alpha = 0.15f)
                        )
                    }

                    if (state.status == DownloadStatus.DOWNLOADING && state.speedKbps > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatSpeed(state.speedKbps),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.etaSeconds > 0) {
                                Text(
                                    "~${formatEta(state.etaSeconds)} remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Installed word count ─────────────────────────────────────────
            if (state.status == DownloadStatus.INSTALLED && state.installedWordCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.LibraryBooks, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text("${formatNum(state.installedWordCount)} words in database",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── Error message ────────────────────────────────────────────────
            if (state.status == DownloadStatus.FAILED && !state.error.isNullOrBlank()) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ── Action button ────────────────────────────────────────────────
            when (state.status) {
                DownloadStatus.NOT_INSTALLED -> Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download ${state.pack.displayName} (${state.pack.sizeMb}MB)")
                }

                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING,
                DownloadStatus.VERIFYING, DownloadStatus.IMPORTING -> OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel")
                }

                DownloadStatus.FAILED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry")
                    }
                }

                DownloadStatus.INSTALLED -> TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove Pack")
                }
            }

            // ── Advanced Download Log tray ───────────────────────────────────
            if (hasLog || isActive) {
                DownloadLogTray(
                    log = state.downloadLog,
                    expanded = logExpanded,
                    onToggle = { logExpanded = !logExpanded }
                )
            }
        }
    }
}

@Composable
private fun DownloadLogTray(
    log: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    // Tray header / toggle row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Download Log",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded && log.isNotBlank()) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(log)) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy log",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Collapsible log content
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit  = shrinkVertically() + fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            val scrollState = rememberScrollState()

            // Auto-scroll to bottom when log updates
            LaunchedEffect(log) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 240.dp)
                    .verticalScroll(scrollState)
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                if (log.isBlank()) {
                    Text(
                        "Waiting for download to start…",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        log,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun buildDownloadLabel(state: PackDownloadUiState): String {
    val dlMb = state.bytesDownloaded / 1_048_576.0
    val totalMb = state.totalBytes / 1_048_576.0
    return if (state.totalBytes > 0)
        "%.1fMB / %.1fMB".format(dlMb, totalMb)
    else "%.1fMB downloaded".format(dlMb)
}

private fun formatSpeed(kbps: Int): String = when {
    kbps >= 1024 -> "%.1f MB/s".format(kbps / 1024.0)
    else -> "$kbps KB/s"
}

private fun formatEta(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    seconds >= 60   -> "${seconds / 60}m ${seconds % 60}s"
    else            -> "${seconds}s"
}

private fun formatNum(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}
