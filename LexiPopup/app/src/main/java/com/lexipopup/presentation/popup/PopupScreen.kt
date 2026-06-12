package com.lexipopup.presentation.popup

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
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
import com.lexipopup.utils.ParallaxOffset
import com.lexipopup.utils.SensorHelper
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val hybridAiResult by viewModel.hybridAiResult.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Drag offset — Animatable for spring edge-snap on release
    val coroutineScope = rememberCoroutineScope()
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Parallax tilt from accelerometer
    var parallax by remember { mutableStateOf(ParallaxOffset(0f, 0f)) }
    LaunchedEffect(Unit) {
        SensorHelper.parallaxFlow(context).distinctUntilChanged().collect {
            parallax = it
        }
    }

    // Save Note dialog state
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    // Particle burst (favorite) state
    var showParticles by remember { mutableStateOf(false) }

    // Resize state
    var popupHeightFraction by remember { mutableStateOf(0.65f) }

    // Entrance: 0.85 → 1.0 spring (damping 0.8) | Exit: scale to 0.9 + fade (ease-out 180ms)
    var isClosing by remember { mutableStateOf(false) }
    var targetScale by remember { mutableStateOf(0.85f) }
    var targetAlpha by remember { mutableStateOf(0f) }
    val scaleAnim by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (isClosing) tween(180, easing = FastOutLinearInEasing)
                        else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "popup_scale"
    )
    val alphaAnim by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = if (isClosing) 180 else 140),
        label = "popup_alpha"
    )
    LaunchedEffect(Unit) { targetScale = 1f; targetAlpha = 1f }
    LaunchedEffect(isClosing) {
        if (isClosing) { targetScale = 0.9f; targetAlpha = 0f; delay(200); onClose() }
    }
    val safeClose: () -> Unit = { isClosing = true }

    // Voice search launcher (RecognizerIntent result → lookup word)
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) viewModel.lookupWord(spoken.trim().split(" ").first())
        }
    }

    // Shimmer for border glow
    val shimmerInfinite = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerInfinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "shimmer_offset"
    )

    // Save Note dialog
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Save Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Your note about this word…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveNote(noteText)
                    showNoteDialog = false
                    noteText = ""
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
        )
    }

    // Scrim / backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { safeClose() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = isBubble,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.7f), initialScale = 0.5f))
                        .togetherWith(fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.5f))
                } else {
                    (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.8f), initialScale = 0.85f))
                        .togetherWith(fadeOut(tween(150)))
                }
            },
            label = "bubble_morph"
        ) { bubbleMode ->
        if (bubbleMode) {
            BubbleMode(
                uiState = uiState,
                onExpand = { viewModel.toggleBubble() },
                modifier = Modifier.offset { IntOffset(animOffsetX.value.toInt(), animOffsetY.value.toInt()) }
            )
        } else {
            // Parallax offset in px → dp
            val parallaxX = with(density) { parallax.x.dp.toPx() }
            val parallaxY = with(density) { parallax.y.dp.toPx() }

            val popupWidthFraction = if (isLandscape) 0.65f else 0.88f
            val screenHeightFraction = if (isLandscape) 0.9f else popupHeightFraction.coerceIn(0.35f, 0.85f)

            Card(
                modifier = Modifier
                    .fillMaxWidth(popupWidthFraction)
                    .fillMaxHeight(screenHeightFraction)
                    .offset { IntOffset((animOffsetX.value + parallaxX).roundToInt(), (animOffsetY.value + parallaxY).roundToInt()) }
                    .scale(scaleAnim)
                    .graphicsLayer {
                        shadowElevation = 28f + (animOffsetY.value.coerceIn(-200f, 200f) / 20f)
                        shape = RoundedCornerShape(28.dp)
                        clip = true
                        // Subtle 3D tilt from parallax
                        rotationX = (parallax.y * 0.4f).coerceIn(-2f, 2f)
                        rotationY = (-parallax.x * 0.4f).coerceIn(-2f, 2f)
                        cameraDistance = 12f * density.density
                        alpha = alphaAnim
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent close on card tap */ },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Glassmorphism layer — native blur on API 31+, layered gradient fallback for API 29/30
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(20.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.07f)
                                        )
                                    )
                                )
                        )
                    }

                    Column {
                        // Top shimmer border glow
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            Color.Transparent
                                        ),
                                        startX = shimmerOffset * 1200f,
                                        endX = (shimmerOffset + 1f) * 1200f
                                    )
                                )
                        )

                        PopupHeader(
                            uiState = uiState,
                            settings = settings,
                            shimmerOffset = shimmerOffset,
                            onDrag = { dx, dy ->
                                if (settings.enableDragging) {
                                    coroutineScope.launch {
                                        val bound = 350f; val rf = 0.25f
                                        animOffsetX.snapTo(animOffsetX.value + if (abs(animOffsetX.value) < bound) dx else dx * rf)
                                        animOffsetY.snapTo(animOffsetY.value + if (abs(animOffsetY.value) < bound) dy else dy * rf)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (settings.enableDragging) {
                                    coroutineScope.launch {
                                        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                                        val cardFraction = if (isLandscape) 0.65f else 0.88f
                                        val maxEdgeX = (screenWidthPx * (1f - cardFraction)) / 2f * 0.9f
                                        val targetX = when {
                                            animOffsetX.value >  maxEdgeX * 0.3f ->  maxEdgeX
                                            animOffsetX.value < -maxEdgeX * 0.3f -> -maxEdgeX
                                            else -> 0f
                                        }
                                        launch { animOffsetX.animateTo(targetX, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
                                        launch { animOffsetY.animateTo(0f,      spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
                                    }
                                }
                            },
                            onClose = safeClose,
                            onMinimize = { viewModel.toggleBubble() },
                            onSpeakWord = { viewModel.speakWord(context) },
                            onToggleFavorite = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleFavorite()
                                showParticles = true
                            }
                        )

                        HorizontalDivider(thickness = 0.5.dp)

                        // Content area — Crossfade gives smooth 200ms transition between states
                        Box(modifier = Modifier.weight(1f)) {
                            androidx.compose.animation.Crossfade(
                                targetState = uiState,
                                animationSpec = tween(200),
                                label = "content_crossfade"
                            ) { state ->
                                when (state) {
                                    is PopupUiState.Loading -> PopupSkeleton()
                                    is PopupUiState.Success -> PopupContent(
                                        entry = state.entry,
                                        settings = settings,
                                        onWordChipClick = { word -> viewModel.lookupWord(word) }
                                    )
                                    is PopupUiState.Error -> PopupError(message = state.message)
                                    is PopupUiState.ManualSearch, is PopupUiState.Idle ->
                                        ManualSearchContent(
                                            query = searchQuery,
                                            suggestions = suggestions,
                                            onQueryChange = { viewModel.onSearchQueryChange(it) },
                                            onSearch = { viewModel.lookupWord(it) },
                                            onVoiceSearch = {
                                                try {
                                                    voiceLauncher.launch(
                                                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a word to look up")
                                                        }
                                                    )
                                                } catch (_: Exception) {}
                                            }
                                        )
                                }
                            }
                        }

                        // Hybrid AI comparison panel — only shown when toggle is ON
                        if (uiState is PopupUiState.Success &&
                            settings.hybridShowComparison &&
                            hybridAiResult != null
                        ) {
                            HorizontalDivider(thickness = 0.5.dp)
                            HybridComparisonPanel(hybridAiResult = hybridAiResult!!)
                        }

                        // Action bar
                        if (uiState is PopupUiState.Success) {
                            HorizontalDivider(thickness = 0.5.dp)
                            PopupActionBar(
                                settings = settings,
                                onCopy = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.copyToClipboard(context)
                                },
                                onSpeakWord = { viewModel.speakWord(context) },
                                onSpeakMeaning = { viewModel.speakMeaning(context) },
                                onTranslate = { viewModel.openTranslate(context) },
                                onShare = { viewModel.shareWord(context) },
                                onSaveNote = {
                                    noteText = ""
                                    showNoteDialog = true
                                },
                                onFullDetails = {
                                    val s = uiState as? PopupUiState.Success
                                    if (s != null) {
                                        context.startActivity(
                                            android.content.Intent(
                                                context,
                                                com.lexipopup.presentation.dashboard.MainActivity::class.java
                                            ).apply {
                                                putExtra("lookup_word", s.entry.word)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                            }
                                        )
                                    }
                                }
                            )
                        }

                        // Resize handle at bottom-right (if enabled)
                        if (settings.enableResizing) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                                Icon(
                                    Icons.Default.OpenInFull,
                                    "Resize",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(bottom = 4.dp, end = 4.dp)
                                        .pointerInput(Unit) {
                                            detectDragGestures { _, dragAmount ->
                                                val delta = dragAmount.y / size.height
                                                popupHeightFraction = (popupHeightFraction + delta).coerceIn(0.35f, 0.85f)
                                            }
                                        },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    // Particle burst overlay on favorite toggle
                    if (showParticles) {
                        ParticleBurst(
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-44).dp, y = 44.dp),
                            onFinish = { showParticles = false }
                        )
                    }
                }
            }
        }
        } // close AnimatedContent { bubbleMode -> }
    }
}

@Composable
fun ParticleBurst(modifier: Modifier, onFinish: () -> Unit) {
    val count = 12
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animatable.animateTo(1f, animationSpec = tween(700))
        onFinish()
    }
    val progress = animatable.value
    val colors = listOf(Color(0xFFFFC107), Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF2196F3))

    Canvas(modifier = modifier.size(100.dp)) {
        repeat(count) { i ->
            val angle = (i * 360f / count) * (Math.PI / 180f).toFloat()
            val radius = progress * 80f
            val x = center.x + radius * kotlin.math.cos(angle)
            val y = center.y + radius * kotlin.math.sin(angle)
            val alpha = (1f - progress).coerceIn(0f, 1f)
            drawCircle(
                color = colors[i % colors.size].copy(alpha = alpha),
                radius = (6f * (1f - progress * 0.5f)),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun PopupHeader(
    uiState: PopupUiState,
    settings: AppSettings,
    shimmerOffset: Float,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onSpeakWord: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() }
                ) { _, dragAmount ->
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (settings.enableDragging) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }

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
                    "Looking up…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Text(
                    "LexiPopup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

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
                    Text("📖 Meaning", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(entry.shortMeaning, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (settings.showDetailedMeaning && entry.detailedMeaning.isNotBlank() &&
                        entry.detailedMeaning != entry.shortMeaning
                    ) {
                        Text(entry.detailedMeaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("हिंदी", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(entry.hindiMeaning, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (entry.hindiPronunciation.isNotBlank()) {
                            Text("(${entry.hindiPronunciation})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Example sentence
        if (settings.showExampleSentence && entry.exampleSentence.isNotBlank()) {
            RaisedCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📝 Example", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(
                        "\"${entry.exampleSentence.trim('"', '"', '"')}\"",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Synonyms
        if (settings.showSynonyms && entry.synonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔗 Synonyms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entry.synonyms) { syn ->
                        WordChip(syn, MaterialTheme.colorScheme.primaryContainer) { onWordChipClick(syn) }
                    }
                }
            }
        }

        // Antonyms
        if (settings.showAntonyms && entry.antonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⚡ Antonyms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entry.antonyms) { ant ->
                        WordChip(ant, MaterialTheme.colorScheme.errorContainer) { onWordChipClick(ant) }
                    }
                }
            }
        }

        // Etymology (expandable)
        if (settings.showEtymology && entry.etymology.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📜 Word Origin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                    }
                    if (expanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(entry.etymology, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Difficulty + Frequency
        if (settings.showDifficultyBadge || settings.showFrequencyMeter) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (settings.showDifficultyBadge) {
                    val colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFf44336))
                    Surface(shape = RoundedCornerShape(50), color = colors.getOrElse(entry.difficultyLevel - 1) { colors[0] }.copy(alpha = 0.18f)) {
                        Text(
                            "⚡ ${entry.difficultyLabel}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.getOrElse(entry.difficultyLevel - 1) { colors[0] },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (settings.showFrequencyMeter) {
                    Column(modifier = Modifier.width(130.dp)) {
                        Text("Frequency ${entry.frequencyRating}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            progress = { entry.frequencyRating / 100f },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Source layer badge — shows which dictionary layer answered this lookup
        SourceLayerBadge(source = entry.source)

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SourceLayerBadge(source: String) {
    if (source.isBlank()) return
    data class LayerInfo(val emoji: String, val label: String, val color: Color)
    val info = when (source.lowercase()) {
        "seed"      -> LayerInfo("🌱", "Seed DB",       Color(0xFF4CAF50))
        "minimal"   -> LayerInfo("📦", "Minimal Pack",  Color(0xFF2196F3))
        "standard"  -> LayerInfo("📚", "Standard Pack", Color(0xFF3F51B5))
        "full"      -> LayerInfo("🗄", "Full Pack",     Color(0xFF9C27B0))
        "online"    -> LayerInfo("🌐", "Online API",    Color(0xFF009688))
        "groq"      -> LayerInfo("🤖", "Groq AI",       Color(0xFFFF9800))
        "openai"    -> LayerInfo("🤖", "OpenAI",        Color(0xFF43A047))
        "on_device" -> LayerInfo("📱", "On-Device AI",  Color(0xFFE91E63))
        else        -> LayerInfo("📄", source.replaceFirstChar { it.uppercase() }, Color(0xFF78909C))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(50),
            color = info.color.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, info.color.copy(alpha = 0.4f))
        ) {
            Text(
                text = "${info.emoji} ${info.label}",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = info.color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun RaisedCard(content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (pressed) 8.dp else 2.dp, label = "card_elev")
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "card_scale")
    Card(
        modifier = Modifier.fillMaxWidth().scale(scale)
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
    val scale by animateFloatAsState(if (isPressed) 0.91f else 1f, label = "chip_scale")
    Surface(
        onClick = onClick,
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(50),
        color = color,
        interactionSource = interactionSource
    ) {
        Text(word, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
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
    onSaveNote: () -> Unit,
    onFullDetails: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        if (settings.showCopyButton) ActionButton(Icons.Default.ContentCopy, "Copy", onCopy)
        if (settings.showSpeakWordButton) ActionButton(Icons.Default.VolumeUp, "Speak", onSpeakWord)
        if (settings.showSpeakMeaningButton) ActionButton(Icons.Default.RecordVoiceOver, "Meaning", onSpeakMeaning)
        if (settings.showTranslateButton) ActionButton(Icons.Default.Translate, "Translate", onTranslate)
        if (settings.showShareButton) ActionButton(Icons.Default.Share, "Share", onShare)
        if (settings.showSaveNoteButton) ActionButton(Icons.Default.Edit, "Note", onSaveNote)
        if (settings.showFullDetailsButton) ActionButton(Icons.Default.MenuBook, "Details", onFullDetails)
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.87f else 1f, label = "action_scale")
    val elev by animateDpAsState(if (isPressed) 6.dp else 0.dp, label = "action_elev")
    Column(modifier = Modifier.scale(scale), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, shadowElevation = elev, color = Color.Transparent) {
            IconButton(onClick = onClick, interactionSource = interactionSource) {
                Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BubbleMode(uiState: PopupUiState, onExpand: () -> Unit, modifier: Modifier) {
    val word = (uiState as? PopupUiState.Success)?.entry?.word ?: ""
    val firstLetter = word.firstOrNull()?.uppercase() ?: "L"
    val pulse by rememberInfiniteTransition(label = "bubble_pulse").animateFloat(
        initialValue = 0.95f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bubble_scale"
    )
    Surface(
        onClick = onExpand,
        modifier = modifier.size(60.dp).scale(pulse),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 14.dp
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
    onSearch: (String) -> Unit,
    onVoiceSearch: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search any word…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = onVoiceSearch) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50)
            )
            Button(onClick = { if (query.isNotBlank()) onSearch(query.trim().split(" ").first()) }, shape = RoundedCornerShape(50)) {
                Text("GO")
            }
        }

        if (suggestions.isNotEmpty()) {
            Text("Suggestions:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            suggestions.take(5).forEach { suggestion ->
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
        initialValue = 0.25f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "sk_alpha"
    )
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (it == 0) 0.45f else if (it % 2 == 0) 1f else 0.75f)
                    .height(if (it == 0) 30.dp else 16.dp)
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
            Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HybridComparisonPanel(hybridAiResult: com.lexipopup.utils.ai.HybridAiResult) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🤖 AI Comparison",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Groq column
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFF9800).copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFF9800).copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🤖 Groq", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                            if (hybridAiResult.groqEntry != null) {
                                Text(hybridAiResult.groqEntry.shortMeaning, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("No result", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // On-Device column
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE91E63).copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE91E63).copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📱 On-Device", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                            if (hybridAiResult.onDeviceEntry != null) {
                                Text(hybridAiResult.onDeviceEntry.shortMeaning, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("No result", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
