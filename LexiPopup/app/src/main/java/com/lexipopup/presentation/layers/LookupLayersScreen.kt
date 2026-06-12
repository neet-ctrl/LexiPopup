package com.lexipopup.presentation.layers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.domain.models.*
import kotlin.math.roundToInt

// ── Layer display metadata ────────────────────────────────────────────────────

private data class LayerMeta(
    val emoji: String,
    val name: String,
    val subtitle: String
)

private val LAYER_META: Map<String, LayerMeta> = mapOf(
    LAYER_CACHE      to LayerMeta("⚡", "Memory Cache",                "Sub-millisecond – LRU in-process store"),
    LAYER_OFFLINE_DB to LayerMeta("📚", "Offline Database",            "Room DB – seed words + downloaded packs"),
    LAYER_ONLINE_API to LayerMeta("🌐", "Online API",                  "FreeDictionaryAPI – free, no key needed"),
    LAYER_GROQ_AI    to LayerMeta("🤖", "Groq AI",                     "Free cloud AI – llama-3.3-70b"),
    LAYER_OPENAI     to LayerMeta("💼", "OpenAI",                       "GPT-4o-mini / GPT-4o – paid, optional"),
    LAYER_ON_DEVICE  to LayerMeta("📱", "On-Device AI",                "Runs offline – Gemma 2B / Phi-2"),
    LAYER_RULE_BASED to LayerMeta("🔧", "Rule-Based Fallback",         "Morphology analysis – always available")
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupLayersScreen(
    viewModel: LookupLayersViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val config by viewModel.layerConfig.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showImportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Layer Config?") },
            text = { Text("This restores all layers to their factory defaults. Your API keys are not affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetToDefaults(); showResetDialog = false }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Config") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste a previously exported config JSON below:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        label = { Text("Config JSON") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = viewModel.importConfigJson(importText)
                    if (ok) { importText = ""; showImportDialog = false }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; importText = "" }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("🔍 Lookup Layers") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, "Reset to defaults")
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        // Hoist drag state here so we can disable outer scroll during drag
        var draggingIndex by remember { mutableIntStateOf(-1) }
        var dragOffsetY by remember { mutableFloatStateOf(0f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState, enabled = draggingIndex == -1),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Presets ───────────────────────────────────────────────────────
            PresetsSection(
                onPresetSelected = { viewModel.applyPreset(it) }
            )

            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // ── Drag hint ─────────────────────────────────────────────────────
            Text(
                "☰  drag to reorder  ·  toggle to enable/disable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // ── Layer Cards ───────────────────────────────────────────────────
            val layerOrder = config.layerOrder
            val cardHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
            val targetIndex by remember {
                derivedStateOf {
                    if (draggingIndex < 0) -1
                    else (draggingIndex + (dragOffsetY / cardHeightPx).roundToInt())
                        .coerceIn(0, layerOrder.lastIndex)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                layerOrder.forEachIndexed { index, layerId ->
                    val isDragging = index == draggingIndex
                    val slideOffset = when {
                        draggingIndex < 0  -> 0f
                        isDragging         -> dragOffsetY
                        draggingIndex < index && index <= targetIndex -> -cardHeightPx
                        draggingIndex > index && index >= targetIndex ->  cardHeightPx
                        else -> 0f
                    }
                    val dragHandleModifier = Modifier.pointerInput(index) {
                        detectDragGestures(
                            onDragStart = { draggingIndex = index; dragOffsetY = 0f },
                            onDrag = { _, delta -> dragOffsetY += delta.y },
                            onDragEnd = {
                                val to = targetIndex
                                if (to >= 0 && to != draggingIndex) viewModel.reorderLayers(draggingIndex, to)
                                draggingIndex = -1; dragOffsetY = 0f
                            },
                            onDragCancel = { draggingIndex = -1; dragOffsetY = 0f }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 2f else 1f)
                            .graphicsLayer { translationY = slideOffset }
                    ) {
                        LayerCard(
                            index = index + 1,
                            layerId = layerId,
                            config = config,
                            dragHandleModifier = dragHandleModifier,
                            onToggle = { viewModel.toggleLayer(layerId, it) },
                            onCacheConfig = { viewModel.updateCacheConfig(it) },
                            onOfflineDbConfig = { viewModel.updateOfflineDbConfig(it) },
                            onOnlineApiConfig = { viewModel.updateOnlineApiConfig(it) },
                            onGroqAiConfig = { viewModel.updateGroqAiConfig(it) },
                            onOpenAiConfig = { viewModel.updateOpenAiConfig(it) },
                            onOnDeviceConfig = { viewModel.updateOnDeviceConfig(it) },
                            onRuleBasedConfig = { viewModel.updateRuleBasedConfig(it) },
                            onClearCache = { viewModel.clearMemoryCache() }
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // ── Global Settings ───────────────────────────────────────────────
            GlobalSettingsCard(
                globalConfig = config.globalConfig,
                onUpdate = { viewModel.updateGlobalConfig(it) }
            )

            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // ── Export / Import / Reset ───────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚙️ Config Management", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val json = viewModel.exportConfigJson()
                            clipboard.setText(AnnotatedString(json))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import")
                    }
                }
                Text(
                    "Export copies your full layer config as JSON. Import restores it on any device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Presets row ───────────────────────────────────────────────────────────────

@Composable
private fun PresetsSection(onPresetSelected: (LookupPreset) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Quick Presets", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LookupPreset.entries.take(3).forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = { onPresetSelected(preset) },
                    label = { Text("${preset.emoji} ${preset.displayName}", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LookupPreset.entries.drop(3).forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = { onPresetSelected(preset) },
                    label = { Text("${preset.emoji} ${preset.displayName}", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Individual Layer Card ─────────────────────────────────────────────────────

@Composable
private fun LayerCard(
    index: Int,
    layerId: String,
    config: LayerSystemConfig,
    dragHandleModifier: Modifier,
    onToggle: (Boolean) -> Unit,
    onCacheConfig: (CacheLayerConfig) -> Unit,
    onOfflineDbConfig: (OfflineDbLayerConfig) -> Unit,
    onOnlineApiConfig: (OnlineApiLayerConfig) -> Unit,
    onGroqAiConfig: (GroqAiLayerConfig) -> Unit,
    onOpenAiConfig: (OpenAiLayerConfig) -> Unit,
    onOnDeviceConfig: (OnDeviceLayerConfig) -> Unit,
    onRuleBasedConfig: (RuleBasedLayerConfig) -> Unit,
    onClearCache: () -> Unit
) {
    val meta = LAYER_META[layerId] ?: return
    val enabled = config.layerEnabled[layerId] == true
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Drag handle
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.padding(4.dp)
                )

                // Position badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "$index",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Name + subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${meta.emoji}  ${meta.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (enabled) "✅ Active" else "○ Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Toggle switch
                Switch(checked = enabled, onCheckedChange = onToggle)

                // Configure expand button
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Configure"
                    )
                }
            }

            // Subtitle (always shown)
            Text(
                meta.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, end = 12.dp, bottom = 8.dp)
            )

            // Expandable config
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    when (layerId) {
                        LAYER_CACHE      -> CacheConfigSection(config.cacheConfig, onCacheConfig, onClearCache)
                        LAYER_OFFLINE_DB -> OfflineDbConfigSection(config.offlineDbConfig, onOfflineDbConfig)
                        LAYER_ONLINE_API -> OnlineApiConfigSection(config.onlineApiConfig, onOnlineApiConfig)
                        LAYER_GROQ_AI    -> GroqAiConfigSection(config.groqAiConfig, onGroqAiConfig)
                        LAYER_OPENAI     -> OpenAiConfigSection(config.openAiConfig, onOpenAiConfig)
                        LAYER_ON_DEVICE  -> OnDeviceConfigSection(config.onDeviceConfig, onOnDeviceConfig)
                        LAYER_RULE_BASED -> RuleBasedConfigSection(config.ruleBasedConfig, onRuleBasedConfig)
                    }
                }
            }
        }
    }
}

// ── Per-layer config sections ─────────────────────────────────────────────────

@Composable
private fun CacheConfigSection(
    cfg: CacheLayerConfig,
    onUpdate: (CacheLayerConfig) -> Unit,
    onClearCache: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConfigLabel("Cache Size: ${cfg.maxSize} words")
        Slider(
            value = cfg.maxSize.toFloat(),
            onValueChange = { onUpdate(cfg.copy(maxSize = it.roundToInt())) },
            valueRange = 50f..1000f,
            steps = 18
        )
        ConfigLabel("TTL (how long entries live)")
        listOf("session" to "Session only", "1h" to "1 hour", "1d" to "1 day", "forever" to "Forever").forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = cfg.ttlMode == key, onClick = { onUpdate(cfg.copy(ttlMode = key)) })
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear Cache Now")
        }
    }
}

@Composable
private fun OfflineDbConfigSection(cfg: OfflineDbLayerConfig, onUpdate: (OfflineDbLayerConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfigSwitch("Fuzzy match (partial spelling)", cfg.fuzzyMatchEnabled) {
            onUpdate(cfg.copy(fuzzyMatchEnabled = it))
        }
        if (cfg.fuzzyMatchEnabled) {
            ConfigLabel("Fuzzy threshold: ${cfg.fuzzyMatchThreshold}%")
            Slider(
                value = cfg.fuzzyMatchThreshold.toFloat(),
                onValueChange = { onUpdate(cfg.copy(fuzzyMatchThreshold = it.roundToInt())) },
                valueRange = 50f..100f,
                steps = 9
            )
        }
        ConfigSwitch("Include etymology", cfg.includeEtymology) {
            onUpdate(cfg.copy(includeEtymology = it))
        }
        ConfigLabel("Max examples per word: ${cfg.maxExamples}")
        Slider(
            value = cfg.maxExamples.toFloat(),
            onValueChange = { onUpdate(cfg.copy(maxExamples = it.roundToInt())) },
            valueRange = 1f..5f,
            steps = 3
        )
    }
}

@Composable
private fun OnlineApiConfigSection(cfg: OnlineApiLayerConfig, onUpdate: (OnlineApiLayerConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfigLabel("Timeout: ${cfg.timeoutSeconds}s")
        Slider(
            value = cfg.timeoutSeconds.toFloat(),
            onValueChange = { onUpdate(cfg.copy(timeoutSeconds = it.roundToInt())) },
            valueRange = 1f..15f,
            steps = 13
        )
        ConfigLabel("Retry attempts: ${cfg.retryCount}")
        Slider(
            value = cfg.retryCount.toFloat(),
            onValueChange = { onUpdate(cfg.copy(retryCount = it.roundToInt())) },
            valueRange = 0f..5f,
            steps = 4
        )
        ConfigSwitch("Cache results to offline DB", cfg.cacheResults) {
            onUpdate(cfg.copy(cacheResults = it))
        }
    }
}

@Composable
private fun GroqAiConfigSection(cfg: GroqAiLayerConfig, onUpdate: (GroqAiLayerConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfigLabel("Model")
        listOf(
            "llama-3.3-70b-versatile" to "Llama 3.3 70B (best, default)",
            "mixtral-8x7b-32768"      to "Mixtral 8x7B (fast)",
            "llama3-8b-8192"          to "Llama 3 8B (fastest)"
        ).forEach { (id, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = cfg.model == id, onClick = { onUpdate(cfg.copy(model = id)) })
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
        ConfigLabel("Explanation sentences: ${cfg.explanationSentences}")
        Slider(
            value = cfg.explanationSentences.toFloat(),
            onValueChange = { onUpdate(cfg.copy(explanationSentences = it.roundToInt())) },
            valueRange = 1f..5f,
            steps = 3
        )
        ConfigSwitch("Include example sentence", cfg.includeExample) {
            onUpdate(cfg.copy(includeExample = it))
        }
        ConfigLabel("Timeout: ${cfg.timeoutSeconds}s")
        Slider(
            value = cfg.timeoutSeconds.toFloat(),
            onValueChange = { onUpdate(cfg.copy(timeoutSeconds = it.roundToInt())) },
            valueRange = 1f..15f,
            steps = 13
        )
    }
}

@Composable
private fun OpenAiConfigSection(cfg: OpenAiLayerConfig, onUpdate: (OpenAiLayerConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfigLabel("Model")
        listOf(
            "gpt-4o-mini" to "GPT-4o mini (cheap, fast)",
            "gpt-4o"      to "GPT-4o (best quality)",
            "gpt-3.5-turbo" to "GPT-3.5 Turbo (legacy)"
        ).forEach { (id, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = cfg.model == id, onClick = { onUpdate(cfg.copy(model = id)) })
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
        ConfigLabel("Timeout: ${cfg.timeoutSeconds}s")
        Slider(
            value = cfg.timeoutSeconds.toFloat(),
            onValueChange = { onUpdate(cfg.copy(timeoutSeconds = it.roundToInt())) },
            valueRange = 1f..20f,
            steps = 18
        )
        Text(
            "API key is configured in AI Settings. OpenAI requires a paid account.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OnDeviceConfigSection(cfg: OnDeviceLayerConfig, onUpdate: (OnDeviceLayerConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfigLabel("Model")
        listOf(
            "gemma-2b-tiny"   to "Gemma 2B-IT (1.4 GB) — 70% ChatGPT quality",
            "phi2-standard"   to "Phi-2 (1.6 GB) — 80% ChatGPT quality"
        ).forEach { (id, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = cfg.modelType == id, onClick = { onUpdate(cfg.copy(modelType = id)) })
                Column {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            "Download models in AI Settings → On-Device AI.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RuleBasedConfigSection(cfg: RuleBasedLayerConfig, onUpdate: (RuleBasedLayerConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfigLabel("Mode")
        listOf(
            "enhanced" to "Enhanced — detects suffixes, prefixes, roots",
            "basic"    to "Basic — simple templates only",
            "off"      to "Off — disable entirely"
        ).forEach { (id, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = cfg.mode == id, onClick = { onUpdate(cfg.copy(mode = id)) })
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
        ConfigSwitch("Show [rule-based] badge in definition", cfg.showAiBadge) {
            onUpdate(cfg.copy(showAiBadge = it))
        }
    }
}

// ── Global Settings Card ──────────────────────────────────────────────────────

@Composable
private fun GlobalSettingsCard(globalConfig: GlobalLayerConfig, onUpdate: (GlobalLayerConfig) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚙️ Global Settings", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            ConfigLabel("Max total lookup time: ${globalConfig.maxTotalLookupMs / 1000}s")
            Slider(
                value = (globalConfig.maxTotalLookupMs / 1000).toFloat(),
                onValueChange = { onUpdate(globalConfig.copy(maxTotalLookupMs = (it * 1000).roundToInt())) },
                valueRange = 1f..15f,
                steps = 13
            )
            ConfigSwitch("Show loading skeleton", globalConfig.showLoadingSkeleton) {
                onUpdate(globalConfig.copy(showLoadingSkeleton = it))
            }
            ConfigSwitch("Parallel lookup (check multiple layers at once)", globalConfig.parallelLookup) {
                onUpdate(globalConfig.copy(parallelLookup = it))
            }
            if (globalConfig.parallelLookup) {
                Text(
                    "⚠️ Parallel mode uses more data and battery. Best for max speed when multiple network layers are enabled.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun ConfigLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
}

@Composable
private fun ConfigSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
