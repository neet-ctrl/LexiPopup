package com.lexipopup.presentation.random

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexipopup.data.local.entities.RandomWordEntity
import kotlin.math.roundToInt

private val AccentCyan  = Color(0xFF4FC3F7)
private val NavyDark    = Color(0xFF0B1D2E)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RandomWordSettingsScreen(
    viewModel: RandomWordSettingsViewModel,
    onBack: () -> Unit
) {
    val settings       by viewModel.settings.collectAsState()
    val discovered     by viewModel.discoveredWords.collectAsState()
    val fetchTriggered by viewModel.fetchTriggered.collectAsState()
    val snackbar       = remember { SnackbarHostState() }

    LaunchedEffect(fetchTriggered) {
        if (fetchTriggered) {
            snackbar.showSnackbar("Fetching new words… they'll appear on the widget shortly.")
            viewModel.clearFetchTrigger()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = AccentCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Random Word", fontWeight = FontWeight.ExtraBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Hero banner ────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(NavyDark, Color(0xFF1A3A4A))))
                        .padding(20.dp)
                ) {
                    Column {
                        Text("⚡ Random Word Widget",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "AI picks advanced vocabulary you'll actually use — one new word every time you interact. Tap the widget to flip and see the full definition, then save or skip.",
                            color = Color(0xB3FFFFFF),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.triggerFetch() }) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fetch Words Now")
                            }
                            OutlinedButton(onClick = { viewModel.regenerateQueue() }) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Regenerate")
                            }
                        }
                    }
                }
            }

            // ── AI Provider ────────────────────────────────────────────────
            item {
                SectionCard(
                    icon = "🤖",
                    title = "AI Provider",
                    subtitle = "Which AI generates your random words"
                ) {
                    listOf("groq" to "Groq Cloud (free · llama-3.3-70b)", "openai" to "OpenAI (gpt-4o-mini)").forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.randomWordProvider == key,
                                onClick  = { viewModel.setProvider(key) }
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                if (key == "groq" && settings.groqApiKey.isBlank()) {
                                    Text("⚠ No Groq API key — set it in AI Settings",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                } else if (key == "openai" && settings.openAiApiKey.isBlank()) {
                                    Text("⚠ No OpenAI API key — set it in AI Settings",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // ── Difficulty ─────────────────────────────────────────────────
            item {
                SectionCard(
                    icon = "🎯",
                    title = "Word Difficulty",
                    subtitle = "How advanced the chosen words are"
                ) {
                    val difficulties = listOf(
                        "advanced"  to "Advanced — educated everyday usage",
                        "academic"  to "Academic — university-level writing",
                        "expert"    to "Expert — specialist & literary",
                        "gre"       to "GRE Prep — high-frequency exam vocabulary"
                    )
                    difficulties.forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.randomWordDifficulty == key,
                                onClick  = { viewModel.setDifficulty(key) }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ── Topics ─────────────────────────────────────────────────────
            item {
                SectionCard(
                    icon = "📚",
                    title = "Topic Filters",
                    subtitle = "Words are weighted towards selected topics"
                ) {
                    val allTopics = listOf(
                        "general", "business", "science", "philosophy",
                        "literature", "technology", "law", "medicine",
                        "psychology", "economics", "history", "arts"
                    )
                    val selectedTopics = settings.randomWordTopics
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        allTopics.forEach { topic ->
                            val selected = topic in selectedTopics
                            FilterChip(
                                selected = selected,
                                onClick  = {
                                    if (selected) selectedTopics.remove(topic)
                                    else selectedTopics.add(topic)
                                    viewModel.setTopics(selectedTopics.joinToString(","))
                                },
                                label    = { Text(topic.replaceFirstChar { it.uppercaseChar() }) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                                    selectedLabelColor     = AccentCyan
                                )
                            )
                        }
                    }
                }
            }

            // ── Queue size ─────────────────────────────────────────────────
            item {
                SectionCard(
                    icon = "📦",
                    title = "Queue Size",
                    subtitle = "How many words to keep pre-fetched (${settings.randomWordPrefetchCount})"
                ) {
                    Slider(
                        value = settings.randomWordPrefetchCount.toFloat(),
                        onValueChange = { viewModel.setPrefetchCount(it.roundToInt()) },
                        valueRange = 3f..20f,
                        steps = 16,
                        colors = SliderDefaults.colors(activeTrackColor = AccentCyan)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("3", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("20", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Toggles ────────────────────────────────────────────────────
            item {
                SectionCard(icon = "⚙️", title = "Behaviour") {
                    ToggleRow(
                        label    = "Auto-refresh queue",
                        subtitle = "Automatically fetch new words every 6 hours when connected",
                        checked  = settings.randomWordAutoRefresh,
                        onCheck  = { viewModel.setAutoRefresh(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ToggleRow(
                        label    = "Show Hindi meaning",
                        subtitle = "Include Devanagari translation when the widget is expanded",
                        checked  = settings.randomWordShowHindi,
                        onCheck  = { viewModel.setShowHindi(it) }
                    )
                }
            }

            // ── Discovered words ───────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Discovered Words (${discovered.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (discovered.isEmpty()) {
                item {
                    Text(
                        "No words discovered yet — interact with the widget to start building your collection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(discovered) { word ->
                    DiscoveredWordCard(word)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Component helpers ─────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    icon: String,
    title: String,
    subtitle: String = "",
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String = "",
    checked: Boolean,
    onCheck: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) {
                Text(subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheck)
    }
}

@Composable
private fun DiscoveredWordCard(word: RandomWordEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = AccentCyan.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        word.word.first().uppercaseChar().toString(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = AccentCyan
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        word.word.replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (word.partOfSpeech.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                word.partOfSpeech,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (word.teaser.isNotBlank()) {
                    Text(
                        word.teaser,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (word.definition.isNotBlank()) {
                    Text(
                        word.definition.take(70) + if (word.definition.length > 70) "…" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (word.pronunciation.isNotBlank()) {
                    Text(
                        word.pronunciation,
                        fontSize = 10.sp,
                        color = AccentCyan,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = AccentCyan.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
