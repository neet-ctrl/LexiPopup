package com.lexipopup.presentation.popup

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import kotlinx.coroutines.launch

@Composable
fun PopupScreen(
    viewModel: PopupViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isBubble by viewModel.isBubbleMode.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Popup offset for dragging
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Scale entrance animation
    val scaleAnim by animateFloatAsState(
        targetValue = if (isBubble) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "popup_scale"
    )

    val shimmerInfinite = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerInfinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart),
        label = "shimmer_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        if (isBubble) {
            // Bubble mode
            BubbleMode(
                uiState = uiState,
                onExpand = { viewModel.toggleBubble() },
                modifier = Modifier.offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
            )
        } else {
            // Full popup
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .heightIn(min = 200.dp, max = 620.dp)
                    .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                    .scale(scaleAnim)
                    .graphicsLayer {
                        shadowElevation = 32f
                        shape = RoundedCornerShape(28.dp)
                        clip = true
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent close on card tap */ },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column {
                    // Drag handle + shimmer border
                    PopupHeader(
                        uiState = uiState,
                        settings = settings,
                        shimmerOffset = shimmerOffset,
                        onDrag = { dx, dy ->
                            offsetX += dx
                            offsetY += dy
                        },
                        onClose = onClose,
                        onMinimize = { viewModel.toggleBubble() },
                        onSpeakWord = { viewModel.speakWord(context) },
                        onToggleFavorite = { viewModel.toggleFavorite() }
                    )

                    HorizontalDivider(thickness = 0.5.dp)

                    // Content area
                    Box(modifier = Modifier.weight(1f)) {
                        when (val state = uiState) {
                            is PopupUiState.Loading -> PopupSkeleton()
                            is PopupUiState.Success -> PopupContent(
                                entry = state.entry,
                                settings = settings,
                                onWordChipClick = { word -> viewModel.lookupWord(word) }
                            )
                            is PopupUiState.Error -> PopupError(message = state.message)
                            is PopupUiState.ManualSearch -> ManualSearchContent(
                                query = searchQuery,
                                suggestions = suggestions,
                                onQueryChange = { viewModel.onSearchQueryChange(it) },
                                onSearch = { viewModel.lookupWord(it) }
                            )
                            else -> ManualSearchContent(
                                query = searchQuery,
                                suggestions = suggestions,
                                onQueryChange = { viewModel.onSearchQueryChange(it) },
                                onSearch = { viewModel.lookupWord(it) }
                            )
                        }
                    }

                    // Action bar at bottom
                    if (uiState is PopupUiState.Success) {
                        HorizontalDivider(thickness = 0.5.dp)
                        PopupActionBar(
                            settings = settings,
                            onCopy = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.copyToClipboard(context) },
                            onSpeakWord = { viewModel.speakWord(context) },
                            onSpeakMeaning = { viewModel.speakMeaning(context) },
                            onTranslate = { viewModel.openTranslate(context) },
                            onShare = { viewModel.shareWord(context) },
                            onFullDetails = { /* navigate to dashboard */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PopupHeader(
    uiState: PopupUiState,
    settings: AppSettings,
    shimmerOffset: Float,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onSpeakWord: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column {
        // Shimmer border indicator + drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        startX = shimmerOffset * 1000f,
                        endX = (shimmerOffset + 1f) * 1000f
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle dots
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is PopupUiState.Success -> {
                        Text(
                            text = state.entry.word.uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (settings.showPronunciation && state.entry.pronunciation.isNotBlank()) {
                            Text(
                                text = state.entry.pronunciation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is PopupUiState.Loading -> Text(
                        text = "Looking up…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is PopupUiState.ManualSearch, is PopupUiState.Idle -> Text(
                        text = "LexiPopup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    else -> {}
                }
            }

            // Action icons
            if (uiState is PopupUiState.Success) {
                if (settings.showSpeakWordButton) {
                    IconButton(onClick = onSpeakWord) {
                        Icon(Icons.Default.VolumeUp, "Speak", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (settings.showFavoriteButton) {
                    val isFav = (uiState as? PopupUiState.Success)?.entry?.isFavorite ?: false
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                            "Favorite",
                            tint = if (isFav) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (settings.enableCollapseTooBubble) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.Remove, "Minimize", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PopupContent(
    entry: WordEntry,
    settings: AppSettings,
    onWordChipClick: (String) -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Part of speech chip
        if (settings.showPartOfSpeech && entry.partOfSpeech.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(entry.partOfSpeechColor).copy(alpha = 0.15f)
            ) {
                Text(
                    text = entry.partOfSpeech.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(entry.partOfSpeechColor)
                )
            }
        }

        // Short meaning
        if (entry.shortMeaning.isNotBlank()) {
            RaisedCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "📖 Meaning",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        entry.shortMeaning,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (settings.showDetailedMeaning && entry.detailedMeaning.isNotBlank() &&
                        entry.detailedMeaning != entry.shortMeaning
                    ) {
                        Text(
                            entry.detailedMeaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Hindi meaning
        if (settings.showHindiMeaning && entry.hindiMeaning.isNotBlank()) {
            RaisedCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🇮🇳  ", style = MaterialTheme.typography.bodyMedium)
                    Column {
                        Text(
                            "हिंदी",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            entry.hindiMeaning,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (entry.hindiPronunciation.isNotBlank()) {
                            Text(
                                "(${entry.hindiPronunciation})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Example sentence
        if (settings.showExampleSentence && entry.exampleSentence.isNotBlank()) {
            RaisedCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "📝 Example",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "\"${entry.exampleSentence.trimQuotes()}\"",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Synonyms
        if (settings.showSynonyms && entry.synonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "🔗 Synonyms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entry.synonyms) { syn ->
                        WordChip(word = syn, color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { onWordChipClick(syn) })
                    }
                }
            }
        }

        // Antonyms
        if (settings.showAntonyms && entry.antonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "⚡ Antonyms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entry.antonyms) { ant ->
                        WordChip(word = ant, color = MaterialTheme.colorScheme.errorContainer,
                            onClick = { onWordChipClick(ant) })
                    }
                }
            }
        }

        // Etymology
        if (settings.showEtymology && entry.etymology.isNotBlank()) {
            Text(
                "📜 ${entry.etymology}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Difficulty + Frequency
        if (settings.showDifficultyBadge || settings.showFrequencyMeter) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (settings.showDifficultyBadge) {
                    val colors = listOf(
                        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFf44336)
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.getOrElse(entry.difficultyLevel - 1) { colors[0] }.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "⚡ ${entry.difficultyLabel}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.getOrElse(entry.difficultyLevel - 1) { colors[0] },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (settings.showFrequencyMeter) {
                    Column(modifier = Modifier.width(120.dp)) {
                        Text(
                            "Frequency ${entry.frequencyRating}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { entry.frequencyRating / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun RaisedCard(content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (pressed) 6.dp else 2.dp, label = "card_elev")
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "card_scale")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pressed = true },
                    onDragEnd = { pressed = false },
                    onDragCancel = { pressed = false },
                    onDrag = { _, _ -> }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.padding(12.dp)) { content() }
    }
}

@Composable
fun WordChip(word: String, color: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "chip_scale")
    Surface(
        onClick = onClick,
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(50),
        color = color,
        interactionSource = interactionSource
    ) {
        Text(
            text = word,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PopupActionBar(
    settings: AppSettings,
    onCopy: () -> Unit,
    onSpeakWord: () -> Unit,
    onSpeakMeaning: () -> Unit,
    onTranslate: () -> Unit,
    onShare: () -> Unit,
    onFullDetails: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (settings.showCopyButton) ActionButton(Icons.Default.ContentCopy, "Copy", onCopy)
        if (settings.showSpeakWordButton) ActionButton(Icons.Default.VolumeUp, "Speak", onSpeakWord)
        if (settings.showSpeakMeaningButton) ActionButton(Icons.Default.RecordVoiceOver, "Meaning", onSpeakMeaning)
        if (settings.showTranslateButton) ActionButton(Icons.Default.Translate, "Translate", onTranslate)
        if (settings.showShareButton) ActionButton(Icons.Default.Share, "Share", onShare)
        if (settings.showFullDetailsButton) ActionButton(Icons.Default.MenuBook, "Details", onFullDetails)
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, label = "action_scale")
    Column(
        modifier = Modifier.scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick, interactionSource = interactionSource) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BubbleMode(uiState: PopupUiState, onExpand: () -> Unit, modifier: Modifier) {
    val word = (uiState as? PopupUiState.Success)?.entry?.word ?: ""
    val firstLetter = word.firstOrNull()?.uppercase() ?: "L"
    val pulse by rememberInfiniteTransition(label = "bubble_pulse").animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "bubble_scale"
    )
    Surface(
        onClick = onExpand,
        modifier = modifier.size(60.dp).scale(pulse),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 12.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(firstLetter, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ManualSearchContent(
    query: String,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search any word…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(50)
            )
            Button(onClick = { if (query.isNotBlank()) onSearch(query) }, shape = RoundedCornerShape(50)) {
                Text("GO")
            }
        }

        if (suggestions.isNotEmpty()) {
            Text("Suggestions:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            suggestions.forEach { suggestion ->
                Surface(
                    onClick = { onSearch(suggestion) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(suggestion, modifier = Modifier.padding(12.dp, 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun PopupSkeleton() {
    val shimmer by rememberInfiniteTransition(label = "sk").animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sk_alpha"
    )
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(5) {
            Box(modifier = Modifier
                .fillMaxWidth(if (it == 0) 0.5f else 1f)
                .height(if (it == 0) 28.dp else 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = shimmer))
            )
        }
    }
}

@Composable
fun PopupError(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun String.trimQuotes() = this.trim('"', '\'', '"', '"')
