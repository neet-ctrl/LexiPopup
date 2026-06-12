@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lexipopup.presentation.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
                                        AiProviderType.GROQ      -> "Free cloud AI — fast & high quality. Requires a free API key."
                                        AiProviderType.OPENAI    -> "GPT-4o-mini — premium quality. Requires a paid API key."
                                        AiProviderType.ON_DEVICE -> "Offline AI on your device. Requires a one-time model download."
                                        AiProviderType.HYBRID    -> "Runs both Groq + On-Device in parallel. Shows the best result."
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
                exit  = shrinkVertically()
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
                exit  = shrinkVertically()
            ) {
                OpenAiSettingsCard(
                    apiKey = settings.openAiApiKey,
                    onKeyChange = viewModel::updateOpenAiKey,
                    onOpenSignup = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/api-keys")))
                    }
                )
            }

            // ── On-Device settings ────────────────────────────────────────
            AnimatedVisibility(
                visible = selectedProvider == AiProviderType.ON_DEVICE || selectedProvider == AiProviderType.HYBRID,
                enter = expandVertically(),
                exit  = shrinkVertically()
            ) {
                OnDeviceSettingsCard(
                    status        = onDeviceStatus,
                    selectedModel = viewModel.aiProviderManager.onDeviceProvider.selectedModel,
                    onSelectModel = viewModel::selectOnDeviceModel,
                    onDownload    = { viewModel.downloadModel() },
                    onDelete      = viewModel::deleteModel,
                    onOpenModelPage = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android")))
                    }
                )
            }

            // ── Hybrid settings ───────────────────────────────────────────
            AnimatedVisibility(
                visible = selectedProvider == AiProviderType.HYBRID,
                enter = expandVertically(),
                exit  = shrinkVertically()
            ) {
                HybridSettingsCard(
                    autoSelectBest     = settings.hybridAutoSelectBest,
                    showComparison     = settings.hybridShowComparison,
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

// ── Step indicator helper ──────────────────────────────────────────────────────

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Groq Cloud Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            if (apiKey.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp))
                    Text("API key set — Groq is ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium)
                }
            } else {
                // Step-by-step setup guide
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How to get your free Groq API key:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold)
                        SetupStep(1, "Tap the button below — opens console.groq.com")
                        SetupStep(2, "Sign up with Google or email (free, no credit card)")
                        SetupStep(3, "Go to API Keys in the left menu")
                        SetupStep(4, "Click Create API Key → give it any name")
                        SetupStep(5, "Copy the key (starts with gsk_…) and paste it below")
                        Button(onClick = onOpenSignup, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open console.groq.com")
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
                "Free tier: 1,000 requests/day · 30 req/min · Model: ${com.lexipopup.utils.ai.GroqAiProvider.MODEL}",
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
    onKeyChange: (String) -> Unit,
    onOpenSignup: () -> Unit
) {
    var keyVisible by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("OpenAI Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            if (apiKey.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp))
                    Text("API key set — OpenAI is ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium)
                }
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How to get your OpenAI API key:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold)
                        SetupStep(1, "Tap the button below — opens platform.openai.com/api-keys")
                        SetupStep(2, "Sign up or log in to your OpenAI account")
                        SetupStep(3, "Click Create new secret key → give it any name")
                        SetupStep(4, "Copy the key (starts with sk-…) — it won't be shown again")
                        SetupStep(5, "Paste it in the field below")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(
                                    "OpenAI requires a paid account (~\$5 minimum credit). " +
                                    "Consider Groq instead — it's free.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Button(onClick = onOpenSignup, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open platform.openai.com")
                        }
                    }
                }
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
                "Uses GPT-4o-mini · ~\$0.0002 per word lookup · requires credits at platform.openai.com",
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
    onDelete: () -> Unit,
    onOpenModelPage: () -> Unit
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("On-Device AI Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text("No internet needed after download · runs 100% on your phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Model selector
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
                                        "RAM: ${model.ramRequiredGb} GB · Quality: ${model.qualityPercent}%",
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

            // Download confirmation dialog
            var showDownloadConfirmDialog by remember { mutableStateOf(false) }
            if (showDownloadConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDownloadConfirmDialog = false },
                    title = { Text("Download AI Model?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("You're about to download ${selectedModel.displayName}.",
                                style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Download, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Text("Download size: ${selectedModel.sizeGb} GB",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Memory, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Text("RAM required: ${selectedModel.ramRequiredGb} GB",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Wifi, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary)
                                Text("Use Wi-Fi to avoid mobile data charges.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary)
                            }
                            Text("Once downloaded the model works fully offline, forever.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showDownloadConfirmDialog = false; onDownload() }) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download (${selectedModel.sizeGb} GB)")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDownloadConfirmDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // ── Status area ────────────────────────────────────────────────
            when (status) {
                is OnDeviceModelStatus.NotDownloaded -> {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("How on-device AI works:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold)
                            SetupStep(1, "Choose a model above (Gemma 2B = smaller, Phi-2 = smarter)")
                            SetupStep(2, "Tap Download — the model comes from Google's servers (no account needed)")
                            SetupStep(3, "Wait for the ${selectedModel.sizeGb} GB download to finish")
                            SetupStep(4, "Done — AI works fully offline forever after this")
                        }
                    }
                    Button(
                        onClick = { showDownloadConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download ${selectedModel.displayName}")
                    }
                    Text(
                        "Requires ${selectedModel.ramRequiredGb} GB RAM · ${selectedModel.sizeGb} GB download · Wi-Fi recommended",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is OnDeviceModelStatus.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloading…",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium)
                            Text("${(status.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val mb    = status.bytesReceived / (1024 * 1024)
                        val total = if (status.totalBytes > 0) status.totalBytes / (1024 * 1024) else "?"
                        Text("${mb} MB / $total MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                is OnDeviceModelStatus.Downloaded,
                is OnDeviceModelStatus.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp))
                        Column {
                            Text("Model ready — works offline",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.tertiary)
                            Text("Select a word in any app to use AI lookup",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Model (free up ${selectedModel.sizeGb} GB storage)")
                    }
                }

                is OnDeviceModelStatus.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading model into memory…",
                            style = MaterialTheme.typography.labelMedium)
                    }
                }

                is OnDeviceModelStatus.Error -> {
                    // No raw error codes — show a calm, actionable guide instead
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.WifiOff, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Text("Download didn't complete",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error)
                            }

                            Text("Things to check:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium)

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                TroubleshootRow(
                                    icon = Icons.Default.Wifi,
                                    text = "Make sure Wi-Fi or mobile data is active"
                                )
                                TroubleshootRow(
                                    icon = Icons.Default.BatteryFull,
                                    text = "Disable battery optimisation for LexiPopup in phone Settings → Apps → LexiPopup → Battery → Unrestricted"
                                )
                                TroubleshootRow(
                                    icon = Icons.Default.Refresh,
                                    text = "Tap Retry below — the download will resume from where it stopped"
                                )
                                TroubleshootRow(
                                    icon = Icons.Default.OpenInNew,
                                    text = "Still failing? Download the model manually from Google's AI Edge page and place it in the app"
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Retry Download")
                                }
                                OutlinedButton(onClick = onOpenModelPage) {
                                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Manual")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Troubleshoot row helper ────────────────────────────────────────────────────

@Composable
private fun TroubleshootRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, null,
            modifier = Modifier.size(16.dp).padding(top = 1.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
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
                Text("Hybrid Mode Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
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
                    Text("Use Groq result if available, On-Device as fallback",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("View Groq and On-Device answers side by side",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showComparison, onCheckedChange = onShowComparisonChange)
            }
        }
    }
}
