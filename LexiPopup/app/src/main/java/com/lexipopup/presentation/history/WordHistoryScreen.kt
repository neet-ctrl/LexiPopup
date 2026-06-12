package com.lexipopup.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.domain.models.WordEntry

private val SOURCE_LABELS = mapOf(
    "online"    to ("🌐" to "Online API"),
    "groq"      to ("🤖" to "Groq AI"),
    "openai"    to ("🤖" to "OpenAI"),
    "on_device" to ("📱" to "On-Device AI"),
    "seed"      to ("🌱" to "Built-in"),
    "minimal"   to ("📦" to "Minimal Pack"),
    "standard"  to ("📦" to "Standard Pack"),
    "full"      to ("📦" to "Full Pack"),
    "local"     to ("✏️" to "Manual"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordHistoryScreen(
    onBack: () -> Unit,
    onWordSelected: (String) -> Unit,
    viewModel: WordHistoryViewModel = hiltViewModel()
) {
    val allWords by viewModel.historyWords.collectAsState()
    val totalCount by viewModel.historyCount.collectAsState()

    var query by remember { mutableStateOf("") }
    var filterSource by remember { mutableStateOf("all") }

    val sourceFilters = listOf("all", "online", "groq", "openai", "on_device", "seed")

    val filtered = remember(allWords, query, filterSource) {
        allWords.filter { w ->
            val matchesQuery = query.isBlank() ||
                w.word.contains(query, ignoreCase = true) ||
                w.shortMeaning.contains(query, ignoreCase = true)
            val matchesSource = filterSource == "all" || w.source == filterSource
            matchesQuery && matchesSource
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Word History", fontWeight = FontWeight.ExtraBold)
                            Text(
                                "$totalCount words saved offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 4.dp),
                    placeholder = { Text("Search history…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
                LazyRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sourceFilters) { src ->
                        val label = when (src) {
                            "all"       -> "All  (${totalCount})"
                            "online"    -> "🌐 Online"
                            "groq"      -> "🤖 Groq"
                            "openai"    -> "🤖 OpenAI"
                            "on_device" -> "📱 On-Device"
                            "seed"      -> "🌱 Built-in"
                            else        -> src
                        }
                        FilterChip(
                            selected = filterSource == src,
                            onClick = { filterSource = src },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    ) { padding ->
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                    Text(
                        if (query.isBlank() && filterSource == "all") "No history yet.\nLook up a word to start building your vocabulary log."
                        else "No matches for \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        "${filtered.size} result${if (filtered.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(filtered, key = { it.word }) { entry ->
                    HistoryWordCard(entry = entry, onClick = { onWordSelected(entry.word) })
                }
            }
        }
    }
}

@Composable
private fun HistoryWordCard(entry: WordEntry, onClick: () -> Unit) {
    val (emoji, label) = SOURCE_LABELS[entry.source] ?: ("📖" to entry.source)
    val sourceColor = when (entry.source) {
        "online"    -> Color(0xFF1565C0)
        "groq", "openai", "on_device" -> Color(0xFF6A1B9A)
        "seed"      -> Color(0xFF2E7D32)
        else        -> Color(0xFF37474F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(entry.word, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    if (entry.partOfSpeech.isNotBlank()) {
                        Text(
                            entry.partOfSpeech,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.pronunciation.isNotBlank()) {
                        Text(
                            entry.pronunciation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }
                if (entry.shortMeaning.isNotBlank()) {
                    Text(
                        entry.shortMeaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sourceColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "$emoji $label",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = sourceColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (entry.isFavorite) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFFC107))
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}
