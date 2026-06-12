package com.lexipopup.presentation.dictionary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.presentation.popup.BioCategoryBadge
import com.lexipopup.presentation.popup.BiologyWordCard
import java.util.Locale

private val BioGreen      = Color(0xFF2E7D32)
private val BioGreenLight = Color(0xFFE8F5E9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    word: String,
    viewModel: WordDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSynonymClick: (String) -> Unit = {}
) {
    val entry          by viewModel.wordEntry.collectAsState()
    val isLoading      by viewModel.isLoading.collectAsState()
    val showNoteDialog by viewModel.showNoteDialog.collectAsState()
    val noteText       by viewModel.noteText.collectAsState()
    val snackMessage   by viewModel.snackMessage.collectAsState()
    val appSettings    by viewModel.appSettings.collectAsState()
    val context        = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(word) { viewModel.load(word) }
    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearSnack()
        }
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { }
        onDispose { tts?.shutdown() }
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeNoteDialog,
            title = { Text("Add Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = viewModel::onNoteTextChange,
                    label = { Text("Your note for \"$word\"") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { Button(onClick = viewModel::saveNote) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::closeNoteDialog) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        word.replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    entry?.let { e ->
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (e.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                "Favorite",
                                tint = if (e.isFavorite) Color(0xFFFFC107) else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${e.word}\n${e.shortMeaning}\n— LexiPopup")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
                        }) { Icon(Icons.Default.Share, "Share") }
                    }
                }
            )
        }
    ) { padding ->

        if (isLoading || entry == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (isLoading) CircularProgressIndicator()
                else Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.SearchOff, null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                    )
                    Text("Word not found in local dictionary", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Try searching for a different word", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        val e      = entry!!
        val isBio  = e.isBiology()
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── BIOLOGY LAYOUT ────────────────────────────────────────────────
            if (isBio) {
                // Green header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BioGreen.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🧬", fontSize = 18.sp)
                    Text(
                        "Biology Term",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = BioGreen,
                        modifier = Modifier.weight(1f)
                    )
                    if (e.partOfSpeech.isNotBlank()) {
                        BioCategoryBadge(e.partOfSpeech)
                    }
                }

                // Pronunciation + Listen row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (e.pronunciation.isNotBlank()) {
                        Text(
                            e.pronunciation,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    FilledTonalButton(
                        onClick = {
                            tts?.language = Locale.US
                            tts?.speak(e.word, TextToSpeech.QUEUE_FLUSH, null, null)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = BioGreenLight)
                    ) {
                        Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp), tint = BioGreen)
                        Spacer(Modifier.width(6.dp))
                        Text("LISTEN", color = BioGreen)
                    }
                }

                HorizontalDivider(color = BioGreen.copy(alpha = 0.2f))

                // Full biology card (all sections: classification, definition, hindi,
                // example, functions, structure, related terms, diseases, etymology, difficulty, frequency)
                BiologyWordCard(
                    entry    = e,
                    settings = appSettings,
                    modifier = Modifier.fillMaxWidth()
                )

            } else {
                // ── ENGLISH LAYOUT ────────────────────────────────────────────
                val posColor = Color(e.partOfSpeechColor)

                // Pronunciation + POS chip + Listen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (e.pronunciation.isNotBlank()) {
                            Text(
                                e.pronunciation,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic
                            )
                        }
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    e.partOfSpeech.ifBlank { "word" }.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = posColor.copy(alpha = 0.14f)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = posColor.copy(alpha = 0.4f)
                            )
                        )
                    }
                    FilledTonalButton(onClick = {
                        tts?.language = Locale.US
                        tts?.speak(e.word, TextToSpeech.QUEUE_FLUSH, null, null)
                    }) {
                        Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("LISTEN")
                    }
                }

                HorizontalDivider()

                if (e.shortMeaning.isNotBlank()) {
                    SectionCard(icon = "📖", title = "Definition") {
                        Text(e.shortMeaning, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
                    }
                }
                if (e.detailedMeaning.isNotBlank() && e.detailedMeaning != e.shortMeaning) {
                    SectionCard(icon = "📝", title = "Detailed Meaning") {
                        Text(e.detailedMeaning, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (e.hindiMeaning.isNotBlank()) {
                    SectionCard(icon = "🇮🇳", title = "Hindi Meaning") {
                        Text(e.hindiMeaning, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp, color = MaterialTheme.colorScheme.primary)
                        if (e.hindiPronunciation.isNotBlank()) {
                            Text(e.hindiPronunciation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                        }
                    }
                }
                if (e.exampleSentence.isNotBlank()) {
                    SectionCard(icon = "📌", title = "Example Sentence") {
                        Text(
                            "\"${e.exampleSentence}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }
                if (e.synonyms.isNotEmpty() || e.antonyms.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (e.synonyms.isNotEmpty()) {
                            SynAntCard(
                                modifier    = Modifier.weight(1f),
                                icon        = "🔗",
                                title       = "Synonyms",
                                words       = e.synonyms,
                                chipColor   = MaterialTheme.colorScheme.primaryContainer,
                                onWordClick = onSynonymClick
                            )
                        }
                        if (e.antonyms.isNotEmpty()) {
                            SynAntCard(
                                modifier    = Modifier.weight(1f),
                                icon        = "↔️",
                                title       = "Antonyms",
                                words       = e.antonyms,
                                chipColor   = MaterialTheme.colorScheme.errorContainer,
                                onWordClick = onSynonymClick
                            )
                        }
                    }
                }
                if (e.etymology.isNotBlank()) {
                    SectionCard(icon = "🌱", title = "Word Origin") {
                        Text(e.etymology, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                    }
                }
                SectionCard(icon = "📊", title = "Word Statistics") {
                    StatBar(label = "Difficulty", value = e.difficultyLevel, max = 4, tag = e.difficultyLabel, color = difficultyColor(e.difficultyLevel))
                    Spacer(Modifier.height(8.dp))
                    StatBar(label = "Frequency", value = e.frequencyRating, max = 100, tag = frequencyTag(e.frequencyRating), color = MaterialTheme.colorScheme.tertiary)
                }
            }

            // ── Shared: User note ──────────────────────────────────────────────
            if (e.userNote.isNotBlank()) {
                SectionCard(
                    icon  = "📎",
                    title = "Your Note",
                    tint  = if (isBio) BioGreen else MaterialTheme.colorScheme.primary
                ) {
                    Text(e.userNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            HorizontalDivider(color = if (isBio) BioGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant)

            // ── Shared: Action buttons ─────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("word", "${e.word}: ${e.shortMeaning}"))
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (isBio) ButtonDefaults.outlinedButtonColors(contentColor = BioGreen) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", maxLines = 1)
                }
                OutlinedButton(
                    onClick = viewModel::openNoteDialog,
                    modifier = Modifier.weight(1f),
                    colors = if (isBio) ButtonDefaults.outlinedButtonColors(contentColor = BioGreen) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.NoteAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Note", maxLines = 1)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    icon: String,
    title: String,
    tint: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val labelColor = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$icon  $title", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = labelColor)
            content()
        }
    }
}

@Composable
private fun SynAntCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    words: List<String>,
    chipColor: Color,
    onWordClick: (String) -> Unit
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$icon  $title", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            words.take(5).forEach { w ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(chipColor.copy(alpha = 0.7f))
                        .clickable { onWordClick(w) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(w, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StatBar(label: String, value: Int, max: Int, tag: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { value.toFloat() / max },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun difficultyColor(level: Int): Color = when (level) {
    1    -> Color(0xFF4CAF50)
    2    -> Color(0xFF2196F3)
    3    -> Color(0xFFFF9800)
    else -> Color(0xFFf44336)
}

private fun frequencyTag(rating: Int): String = when {
    rating >= 80 -> "Very Common"
    rating >= 60 -> "Common"
    rating >= 40 -> "Moderate"
    rating >= 20 -> "Rare"
    else         -> "Very Rare"
}
