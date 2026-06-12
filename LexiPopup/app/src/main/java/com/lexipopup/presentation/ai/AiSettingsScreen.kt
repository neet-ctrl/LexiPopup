@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lexipopup.presentation.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexipopup.utils.ai.AiProviderType
import com.lexipopup.utils.ai.OnDeviceModel
import com.lexipopup.utils.ai.OnDeviceModelStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    viewModel: AiSettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val onDeviceStatus by viewModel.onDeviceStatus.collectAsState()
    val context = LocalContext.current

    val selectedProvider = AiProviderType.fromId(settings.aiProviderName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 AI Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Provider selection card ──────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AI Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Choose how unknown words are explained when not found in the offline dictionary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    AiProviderType.values().forEach { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProvider == type,
                                onClick = { viewModel.selectProvider(type) }
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(type.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    when (type) {
                                        AiProviderType.GROQ -> "Free cloud AI — fast & high quality. Requires a free API key."
                                        AiProviderType.OPENAI -> "GPT-4o-mini — premium quality. Requires a paid API key."
                                        AiProviderType.ON_DEVICE -> "Offline AI on your device. Requires a one-time model download."
                                        AiProviderType.HYBRID -> "Runs both Groq + On-Device in parallel. Shows the best result."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Groq settings ────────────────────────────────────────────
            AnimatedVisibility(
                visible = selectedProvider == AiProviderType.GROQ || selectedProvider == AiProviderType.HYBRID,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                GroqSettingsCard(
                    apiKey = settings.groqApiKey,
                    onKeyChange = viewModel::updateGroqKey,
                    onOpenSignup = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com")))
                    }
                )
            }

            // ── OpenAI settings ───────────────────────────────────────────
            AnimatedVisibility(
                visible = selectedProvider == AiProviderType.OPENAI,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                OpenAiSettingsCard(
                    apiKey = settings.openAiApiKey,
                    onKeyChange = viewModel::updateOpenAiKey
                )
            }

            // ── On-Device settings ────────────────────────────────────────
            AnimatedVisibility(
                visible = selectedProvider == AiProviderType.ON_DEVICE || selectedProvider == AiProviderType.HYBRID,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                OnDeviceSettingsCard(
                    status = onDeviceStatus,
                    selectedModel = viewModel.aiProviderManager.onDeviceProvider.selectedModel,
                    onSelectModel = viewModel::selectOnDeviceModel,
                    onDownload = { viewModel.downloadModel() },
                    onDelete = viewModel::deleteModel
                )
            }

            // ── Hybrid settings ───────────────────────────────────────────
            AnimatedVisibility(
                visible = selectedProvider == AiProviderType.HYBRID,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                HybridSettingsCard(
                    autoSelectBest = settings.hybridAutoSelectBest,
                    showComparison = settings.hybridShowComparison,
                    onAutoSelectChange = viewModel::setHybridAutoSelect,
                    onShowComparisonChange = viewModel::setHybridShowComparison
                )
            }

            // ── Info card ─────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ℹ️ How it works", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "AI is used only as a last resort — LexiPopup first checks its offline database (100k+ words) and the online FreeDictionary API. AI explains words that are too new, rare, or domain-specific to appear there.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Groq settings card ────────────────────────────────────────────────────────

@Composable
private fun GroqSettingsCard(
    apiKey: String,
    onKeyChange: (String) -> Unit,
    onOpenSignup: () -> Unit
) {
    var keyVisible by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Groq Cloud Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (apiKey.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("API key set — Groq is ready", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Setup: 2 minutes, completely free", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        Text("1. Open console.groq.com\n2. Sign up (no credit card)\n3. API Keys → Create → Copy → paste below", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onOpenSignup, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Get Free API Key at console.groq.com")
                        }
                    }
                }
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = onKeyChange,
                label = { Text("Groq API Key") },
                placeholder = { Text("gsk_…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (keyVisible) "Hide" else "Show"
                        )
                    }
                }
            )
            Text(
                "Free tier: 1,000 requests/day • 30 req/min • Model: ${com.lexipopup.utils.ai.GroqAiProvider.MODEL}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── OpenAI settings card ──────────────────────────────────────────────────────

@Composable
private fun OpenAiSettingsCard(
    apiKey: String,
    onKeyChange: (String) -> Unit
) {
    var keyVisible by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("OpenAI Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = onKeyChange,
                label = { Text("OpenAI API Key") },
                placeholder = { Text("sk-…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (keyVisible) "Hide" else "Show"
                        )
                    }
                }
            )
            Text(
                "Uses GPT-4o-mini. Requires a paid OpenAI account at platform.openai.com",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── On-Device settings card ───────────────────────────────────────────────────

@Composable
private fun OnDeviceSettingsCard(
    status: OnDeviceModelStatus,
    selectedModel: OnDeviceModel,
    onSelectModel: (OnDeviceModel) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("On-Device AI Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            // Model selection
            ExposedDropdownMenuBox(
                expanded = modelMenuExpanded,
                onExpandedChange = { modelMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedModel.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                    OnDeviceModel.ALL.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.displayName, fontWeight = FontWeight.Medium)
                                    Text(
                                        "RAM: ${model.ramRequiredGb}GB · Quality: ${model.qualityPercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = { onSelectModel(model); modelMenuExpanded = false }
                        )
                    }
                }
            }

            // Status + action
            when (status) {
                is OnDeviceModelStatus.NotDownloaded -> {
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download Model (${selectedModel.sizeGb} GB)")
                    }
                    Text(
                        "Requires ${selectedModel.ramRequiredGb} GB RAM. Download once, works forever offline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is OnDeviceModelStatus.Downloading -> {
                    Text("Downloading… ${(status.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                    val mb = status.bytesReceived / (1024 * 1024)
                    val totalMb = if (status.totalBytes > 0) status.totalBytes / (1024 * 1024) else "?"
                    Text("${mb} MB / $totalMb MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is OnDeviceModelStatus.Downloaded, is OnDeviceModelStatus.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Model ready — works offline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Model (free up storage)")
                    }
                }
                is OnDeviceModelStatus.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading model…", style = MaterialTheme.typography.labelMedium)
                    }
                }
                is OnDeviceModelStatus.Error -> {
                    Text(
                        "Error: ${status.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry Download")
                    }
                }
            }
        }
    }
}

// ── Hybrid settings card ──────────────────────────────────────────────────────

@Composable
private fun HybridSettingsCard(
    autoSelectBest: Boolean,
    showComparison: Boolean,
    onAutoSelectChange: (Boolean) -> Unit,
    onShowComparisonChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CompareArrows, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Hybrid Mode Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                "Hybrid fires Groq and On-Device AI simultaneously and shows you both results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-select best explanation", style = MaterialTheme.typography.bodyMedium)
                    Text("Use Groq result if available, On-Device as fallback", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoSelectBest, onCheckedChange = onAutoSelectChange)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show comparison tabs in popup", style = MaterialTheme.typography.bodyMedium)
                    Text("View Groq and On-Device answers side by side", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showComparison, onCheckedChange = onShowComparisonChange)
            }
        }
    }
}
