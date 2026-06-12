package com.lexipopup.presentation.popup

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Window state machine ──────────────────────────────────────────────────────

enum class PopupWindowState { FULL, BUBBLE, EDGE_LEFT, EDGE_RIGHT }

// ── Main Screen ───────────────────────────────────────────────────────────────

@Composable
fun PopupScreen(
    viewModel: PopupViewModel,
    onClose: () -> Unit
) {
    val uiState       by viewModel.uiState.collectAsState()
    val settings      by viewModel.settings.collectAsState()
    val suggestions   by viewModel.suggestions.collectAsState()
    val searchQuery   by viewModel.searchQuery.collectAsState()
    val isBubble      by viewModel.isBubbleMode.collectAsState()
    val hybridAiResult by viewModel.hybridAiResult.collectAsState()

    val context       = LocalContext.current
    val haptic        = LocalHapticFeedback.current
    val density       = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope         = rememberCoroutineScope()
    val isLandscape   = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Main window drag / position state ────────────────────────────────────
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }

    // ── Bubble independent position (completely separate from main window) ────
    val bubbleX = remember { Animatable(0f) }
    val bubbleY = remember { Animatable(100f) }  // slightly below center by default

    fun snapBubbleToEdge() {
        scope.launch {
            val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
            // Snap to left or right based on current X position
            val snapX = if (bubbleX.value >= 0f)
                screenW / 2f - with(density) { 44.dp.toPx() } / 2f - with(density) { 12.dp.toPx() }
            else
                -(screenW / 2f - with(density) { 44.dp.toPx() } / 2f - with(density) { 12.dp.toPx() })
            // Clamp Y so bubble stays within screen bounds
            val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
            val maxY = screenH / 2f - with(density) { 44.dp.toPx() } / 2f - with(density) { 24.dp.toPx() }
            val snapY = bubbleY.value.coerceIn(-maxY, maxY)
            launch { bubbleX.animateTo(snapX, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
            launch { bubbleY.animateTo(snapY, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        }
    }

    // Reset bubble to a nice default when entering bubble mode
    LaunchedEffect(isBubble) {
        if (isBubble) {
            val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
            val snapX = screenW / 2f - with(density) { 44.dp.toPx() } / 2f - with(density) { 12.dp.toPx() }
            bubbleX.animateTo(snapX, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            bubbleY.animateTo(100f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    // Restore saved position once settings arrive
    var positionRestored by remember { mutableStateOf(false) }
    LaunchedEffect(settings.popupLastOffsetX, settings.popupLastOffsetY) {
        if (!positionRestored && (settings.popupLastOffsetX != 0f || settings.popupLastOffsetY != 0f)) {
            animX.snapTo(settings.popupLastOffsetX)
            animY.snapTo(settings.popupLastOffsetY)
            positionRestored = true
        }
    }

    // ── Window size state ────────────────────────────────────────────────────
    var widthFraction  by remember { mutableStateOf(settings.popupWidthFraction.coerceIn(0.50f, 0.95f)) }
    var heightFraction by remember { mutableStateOf(settings.popupHeightFraction.coerceIn(0.35f, 0.88f)) }

    // ── Window collapse mode ─────────────────────────────────────────────────
    var windowMode by remember { mutableStateOf(PopupWindowState.FULL) }
    val effectiveWindowState = when {
        isBubble                             -> PopupWindowState.BUBBLE
        windowMode == PopupWindowState.EDGE_LEFT  -> PopupWindowState.EDGE_LEFT
        windowMode == PopupWindowState.EDGE_RIGHT -> PopupWindowState.EDGE_RIGHT
        else                                 -> PopupWindowState.FULL
    }

    // ── Parallax tilt ────────────────────────────────────────────────────────
    var parallax by remember { mutableStateOf(ParallaxOffset(0f, 0f)) }
    LaunchedEffect(Unit) {
        SensorHelper.parallaxFlow(context).distinctUntilChanged().collect { parallax = it }
    }

    // ── Entrance / exit animation ────────────────────────────────────────────
    var isClosing   by remember { mutableStateOf(false) }
    var scaleTarget by remember { mutableStateOf(0.85f) }
    var alphaTarget by remember { mutableStateOf(0f) }
    val scaleAnim by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = if (isClosing) tween(180, easing = FastOutLinearInEasing)
                        else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "popup_scale"
    )
    val alphaAnim by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = tween(if (isClosing) 180 else 140),
        label = "popup_alpha"
    )
    LaunchedEffect(Unit) { scaleTarget = 1f; alphaTarget = 1f }
    LaunchedEffect(isClosing) {
        if (isClosing) { scaleTarget = 0.9f; alphaTarget = 0f; delay(200); onClose() }
    }
    val safeClose = { isClosing = true }

    // ── Shimmer border ───────────────────────────────────────────────────────
    val shimmerInf = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerInf.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "shimmer_offset"
    )

    // ── Misc dialog state ────────────────────────────────────────────────────
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText       by remember { mutableStateOf("") }
    var showParticles  by remember { mutableStateOf(false) }

    // ── Voice search launcher ────────────────────────────────────────────────
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

    // ── Collapse helpers ─────────────────────────────────────────────────────
    fun collapseToEdge(side: PopupWindowState) {
        scope.launch {
            val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
            val targetX = if (side == PopupWindowState.EDGE_RIGHT) screenW else -screenW
            launch { animX.animateTo(targetX, tween(180, easing = FastOutLinearInEasing)) }
            delay(120)
            windowMode = side
            animX.snapTo(0f); animY.snapTo(0f)
        }
    }

    fun collapseButtonTapped() {
        val side = if (animX.value >= 0f) PopupWindowState.EDGE_RIGHT else PopupWindowState.EDGE_LEFT
        if (settings.enableEdgeCollapse) collapseToEdge(side) else viewModel.toggleBubble()
    }

    fun handleDragEnd() {
        scope.launch {
            val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
            val threshold = screenW * 0.30f

            if (settings.enableEdgeCollapse) {
                when {
                    animX.value > threshold -> {
                        launch { animX.animateTo(screenW, tween(160)) }
                        delay(100)
                        windowMode = PopupWindowState.EDGE_RIGHT
                        viewModel.saveWindowPosition(0f, 0f)
                        animX.snapTo(0f); animY.snapTo(0f)
                        return@launch
                    }
                    animX.value < -threshold -> {
                        launch { animX.animateTo(-screenW, tween(160)) }
                        delay(100)
                        windowMode = PopupWindowState.EDGE_LEFT
                        viewModel.saveWindowPosition(0f, 0f)
                        animX.snapTo(0f); animY.snapTo(0f)
                        return@launch
                    }
                }
            }
            // Normal edge-snap spring
            val cardFraction = if (isLandscape) 0.65f else widthFraction
            val maxEdgeX = (screenW * (1f - cardFraction)) / 2f * 0.9f
            val snapX = when {
                animX.value > maxEdgeX * 0.3f ->  maxEdgeX
                animX.value < -maxEdgeX * 0.3f -> -maxEdgeX
                else -> 0f
            }
            launch { animX.animateTo(snapX, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
            launch { animY.animateTo(0f,    spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
            viewModel.saveWindowPosition(snapX, 0f)
        }
    }

    // ── Save size when it changes ────────────────────────────────────────────
    LaunchedEffect(widthFraction, heightFraction) {
        viewModel.saveWindowSize(widthFraction, heightFraction)
    }

    // ── Save Note dialog ─────────────────────────────────────────────────────
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
                    showNoteDialog = false; noteText = ""
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
        )
    }

    // ── Root container ───────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        // Scrim — only in FULL mode
        if (effectiveWindowState == PopupWindowState.FULL) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { safeClose() }
            )
        }

        // Animated window content
        AnimatedContent(
            targetState = effectiveWindowState,
            transitionSpec = {
                val toEdge = targetState == PopupWindowState.EDGE_LEFT || targetState == PopupWindowState.EDGE_RIGHT
                val fromEdge = initialState == PopupWindowState.EDGE_LEFT || initialState == PopupWindowState.EDGE_RIGHT
                when {
                    toEdge -> fadeIn(tween(180)) togetherWith fadeOut(tween(150))
                    fromEdge -> (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.7f), 0.8f)) togetherWith fadeOut(tween(150))
                    targetState == PopupWindowState.BUBBLE ->
                        (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.7f), 0.5f)) togetherWith
                        (fadeOut(tween(180)) + scaleOut(tween(180), 0.5f))
                    else -> (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.8f), 0.85f)) togetherWith fadeOut(tween(150))
                }
            },
            label = "window_state"
        ) { state ->
            when (state) {

                // ── Bubble mode — own drag state, snaps to screen edge ────────
                PopupWindowState.BUBBLE -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BubbleMode(
                            uiState  = uiState,
                            onExpand = { viewModel.toggleBubble() },
                            modifier = Modifier
                                .offset { IntOffset(bubbleX.value.toInt(), bubbleY.value.toInt()) }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragEnd = { snapBubbleToEdge() }
                                    ) { _, drag ->
                                        scope.launch {
                                            bubbleX.snapTo(bubbleX.value + drag.x)
                                            bubbleY.snapTo(bubbleY.value + drag.y)
                                        }
                                    }
                                }
                        )
                    }
                }

                // ── Edge-collapsed tab (left) ─────────────────────────────────
                PopupWindowState.EDGE_LEFT -> {
                    Box(Modifier.fillMaxSize()) {
                        EdgeCollapsedTab(
                            side = "left",
                            word = (uiState as? PopupUiState.Success)?.entry?.word ?: "",
                            onExpand = { windowMode = PopupWindowState.FULL },
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                }

                // ── Edge-collapsed tab (right) ────────────────────────────────
                PopupWindowState.EDGE_RIGHT -> {
                    Box(Modifier.fillMaxSize()) {
                        EdgeCollapsedTab(
                            side = "right",
                            word = (uiState as? PopupUiState.Success)?.entry?.word ?: "",
                            onExpand = { windowMode = PopupWindowState.FULL },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }

                // ── Full floating window ──────────────────────────────────────
                PopupWindowState.FULL -> {
                    val parallaxXPx = with(density) { parallax.x.dp.toPx() }
                    val parallaxYPx = with(density) { parallax.y.dp.toPx() }
                    val cardW = if (isLandscape) 0.65f else widthFraction.coerceIn(0.50f, 0.95f)
                    val cardH = if (isLandscape) 0.90f else heightFraction.coerceIn(0.35f, 0.88f)

                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(cardW)
                                .fillMaxHeight(cardH)
                                .offset {
                                    IntOffset(
                                        (animX.value + parallaxXPx).roundToInt(),
                                        (animY.value + parallaxYPx).roundToInt()
                                    )
                                }
                                .scale(scaleAnim)
                                .graphicsLayer {
                                    shadowElevation = 28f + (animY.value.coerceIn(-200f, 200f) / 20f)
                                    shape = RoundedCornerShape(28.dp)
                                    clip = true
                                    rotationX = (parallax.y * 0.4f).coerceIn(-2f, 2f)
                                    rotationY = (-parallax.x * 0.4f).coerceIn(-2f, 2f)
                                    cameraDistance = 12f * density.density
                                    alpha = alphaAnim
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { /* consume tap, don't close */ },
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                // Glassmorphism
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Box(
                                        Modifier.fillMaxSize().blur(20.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                    )
                                } else {
                                    Box(
                                        Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.07f)
                                            ))
                                        )
                                    )
                                }

                                Column(Modifier.fillMaxSize()) {
                                    // Shimmer border line
                                    Box(
                                        Modifier.fillMaxWidth().height(3.dp).background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                    Color.Transparent
                                                ),
                                                startX = shimmerOffset * 1200f,
                                                endX   = (shimmerOffset + 1f) * 1200f
                                            )
                                        )
                                    )

                                    // Header
                                    PopupHeader(
                                        uiState        = uiState,
                                        settings       = settings,
                                        shimmerOffset  = shimmerOffset,
                                        onDrag         = { dx, dy ->
                                            if (settings.enableDragging) {
                                                scope.launch {
                                                    val bound = 350f; val rf = 0.25f
                                                    animX.snapTo(animX.value + if (abs(animX.value) < bound) dx else dx * rf)
                                                    animY.snapTo(animY.value + if (abs(animY.value) < bound) dy else dy * rf)
                                                }
                                            }
                                        },
                                        onDragEnd      = { if (settings.enableDragging) handleDragEnd() },
                                        onClose        = safeClose,
                                        onCollapse     = { collapseButtonTapped() },
                                        onSpeakWord    = { viewModel.speakWord(context) },
                                        onToggleFavorite = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleFavorite()
                                            showParticles = true
                                        }
                                    )

                                    HorizontalDivider(thickness = 0.5.dp)

                                    // Content area
                                    Box(Modifier.weight(1f)) {
                                        androidx.compose.animation.Crossfade(
                                            targetState  = uiState,
                                            animationSpec = tween(200),
                                            label        = "content_crossfade"
                                        ) { st ->
                                            when (st) {
                                                is PopupUiState.Loading -> PopupSkeleton()
                                                is PopupUiState.Success -> PopupContent(
                                                    entry            = st.entry,
                                                    settings         = settings,
                                                    onWordChipClick  = { w -> viewModel.lookupWord(w) }
                                                )
                                                is PopupUiState.Error  -> PopupError(st.message)
                                                is PopupUiState.ManualSearch,
                                                is PopupUiState.Idle   -> ManualSearchContent(
                                                    query          = searchQuery,
                                                    suggestions    = suggestions,
                                                    onQueryChange  = { viewModel.onSearchQueryChange(it) },
                                                    onSearch       = { viewModel.lookupWord(it) },
                                                    onVoiceSearch  = {
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

                                    // Hybrid AI comparison panel
                                    if (uiState is PopupUiState.Success &&
                                        settings.hybridShowComparison &&
                                        hybridAiResult != null
                                    ) {
                                        HorizontalDivider(thickness = 0.5.dp)
                                        HybridComparisonPanel(hybridAiResult = hybridAiResult!!)
                                    }

                                    // Scrollable action bar
                                    if (uiState is PopupUiState.Success) {
                                        HorizontalDivider(thickness = 0.5.dp)
                                        PopupActionBar(
                                            settings       = settings,
                                            haptic         = haptic,
                                            onCopy         = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.copyToClipboard(context) },
                                            onSpeakWord    = { viewModel.speakWord(context) },
                                            onSpeakMeaning = { viewModel.speakMeaning(context) },
                                            onTranslate    = { viewModel.openTranslate(context) },
                                            onShare        = { viewModel.shareWord(context) },
                                            onSaveNote     = { noteText = ""; showNoteDialog = true },
                                            onFullDetails  = {
                                                val s = uiState as? PopupUiState.Success
                                                if (s != null) {
                                                    context.startActivity(
                                                        Intent(context, com.lexipopup.presentation.dashboard.MainActivity::class.java).apply {
                                                            putExtra("lookup_word", s.entry.word)
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                                        }
                                                    )
                                                }
                                            },
                                            onSearchWeb    = { viewModel.openSearchWeb(context) },
                                            onAddFlashcard = { viewModel.addFlashcard() }
                                        )
                                    }

                                    // Resize handle (corner ◢ — drag diagonally)
                                    if (settings.enableResizing) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.BottomEnd
                                        ) {
                                            Icon(
                                                Icons.Default.OpenInFull,
                                                contentDescription = "Resize",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .padding(bottom = 4.dp, end = 4.dp)
                                                    .pointerInput(Unit) {
                                                        detectDragGestures { _, dragAmount ->
                                                            val dy = dragAmount.y / size.height.coerceAtLeast(1)
                                                            val dx = dragAmount.x / size.width.coerceAtLeast(1)
                                                            heightFraction = (heightFraction + dy).coerceIn(0.35f, 0.88f)
                                                            widthFraction  = (widthFraction  + dx * 0.5f).coerceIn(0.50f, 0.95f)
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                } // end Column

                                // Particle burst on favorite
                                if (showParticles) {
                                    ParticleBurst(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-44).dp, y = 44.dp),
                                        onFinish = { showParticles = false }
                                    )
                                }
                            } // end Box inside Card
                        } // end Card
                    } // end Box(Center)
                }
            } // when
        } // AnimatedContent
    } // root Box
}

// ── Edge-collapsed tab (YouTube-style) ────────────────────────────────────────

@Composable
fun EdgeCollapsedTab(
    side: String,         // "left" | "right"
    word: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glow by rememberInfiniteTransition(label = "tab_glow").animateFloat(
        initialValue = 0.70f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    val shape = if (side == "left")
        RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    else
        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

    val firstLetter = word.firstOrNull()?.uppercase() ?: "L"

    Surface(
        onClick = onExpand,
        modifier = modifier
            .width(48.dp)
            .height(120.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = glow),
        shadowElevation = 18.dp
    ) {
        // Shimmer border on open side
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
            ) {
                Text(
                    firstLetter,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(6.dp))
                Icon(
                    imageVector = if (side == "left") Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                    contentDescription = "Tap to expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                )
            }
        }
    }
}

// ── Particle burst ────────────────────────────────────────────────────────────

@Composable
fun ParticleBurst(modifier: Modifier, onFinish: () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(700)); onFinish() }
    val progress = anim.value
    val colors = listOf(Color(0xFFFFC107), Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF2196F3))
    androidx.compose.foundation.Canvas(modifier = modifier.size(100.dp)) {
        repeat(12) { i ->
            val angle = (i * 30f) * (Math.PI / 180f).toFloat()
            val r = progress * 80f
            drawCircle(
                color = colors[i % colors.size].copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                radius = 6f * (1f - progress * 0.5f),
                center = Offset(center.x + r * kotlin.math.cos(angle), center.y + r * kotlin.math.sin(angle))
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
fun PopupHeader(
    uiState: PopupUiState,
    settings: AppSettings,
    shimmerOffset: Float,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    onSpeakWord: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onDragEnd() }) { _, d ->
                    onDrag(d.x, d.y)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle (visual only)
        if (settings.enableDragging) {
            Icon(
                Icons.Default.DragHandle, "Drag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
        }

        // Edge-collapse / minimize button
        if (settings.enableCollapseTooBubble || settings.enableEdgeCollapse) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Remove, "Collapse to edge",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Word + pronunciation
        Column(modifier = Modifier.weight(1f)) {
            when (val st = uiState) {
                is PopupUiState.Success -> {
                    Text(
                        text = st.entry.word.uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (settings.showPronunciation && st.entry.pronunciation.isNotBlank()) {
                        Text(
                            text = st.entry.pronunciation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is PopupUiState.Loading ->
                    Text("Looking up…", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                else ->
                    Text("LexiPopup", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
            }
        }

        // Header action buttons
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
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

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

        if (entry.shortMeaning.isNotBlank()) {
            RaisedCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📖 Meaning", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(entry.shortMeaning, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (settings.showDetailedMeaning && entry.detailedMeaning.isNotBlank() && entry.detailedMeaning != entry.shortMeaning) {
                        Text(entry.detailedMeaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

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

        if (settings.showExampleSentence && entry.exampleSentence.isNotBlank()) {
            RaisedCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📝 Example", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(
                        "\"${entry.exampleSentence.trim('"', '\u201C', '\u201D')}\"",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

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

        if (settings.showEtymology && entry.etymology.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(Modifier.padding(10.dp)) {
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

        if (settings.showDifficultyBadge || settings.showFrequencyMeter) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (settings.showDifficultyBadge) {
                    val cols = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFf44336))
                    Surface(shape = RoundedCornerShape(50), color = cols.getOrElse(entry.difficultyLevel - 1) { cols[0] }.copy(alpha = 0.18f)) {
                        Text("⚡ ${entry.difficultyLabel}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall, color = cols.getOrElse(entry.difficultyLevel - 1) { cols[0] }, fontWeight = FontWeight.Bold)
                    }
                }
                if (settings.showFrequencyMeter) {
                    Column(Modifier.width(130.dp)) {
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

        SourceLayerBadge(source = entry.source)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SourceLayerBadge(source: String) {
    if (source.isBlank()) return
    data class LI(val emoji: String, val label: String, val color: Color)
    val info = when (source.lowercase()) {
        "seed"      -> LI("🌱", "Seed DB",       Color(0xFF4CAF50))
        "minimal"   -> LI("📦", "Minimal Pack",  Color(0xFF2196F3))
        "standard"  -> LI("📚", "Standard Pack", Color(0xFF3F51B5))
        "full"      -> LI("🗄", "Full Pack",     Color(0xFF9C27B0))
        "online"    -> LI("🌐", "Online API",    Color(0xFF009688))
        "groq"      -> LI("🤖", "Groq AI",       Color(0xFFFF9800))
        "openai"    -> LI("🤖", "OpenAI",        Color(0xFF43A047))
        "on_device" -> LI("📱", "On-Device AI",  Color(0xFFE91E63))
        "rule_based"-> LI("🔧", "Rule-Based",    Color(0xFF607D8B))
        else        -> LI("📄", source.replaceFirstChar { it.uppercase() }, Color(0xFF78909C))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(50),
            color = info.color.copy(alpha = 0.12f),
            border = BorderStroke(0.5.dp, info.color.copy(alpha = 0.4f))
        ) {
            Text("${info.emoji} ${info.label}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall, color = info.color, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
fun RaisedCard(content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val elev by animateDpAsState(if (pressed) 8.dp else 2.dp, label = "card_elev")
    val sc   by animateFloatAsState(if (pressed) 0.97f else 1f, label = "card_scale")
    Card(
        modifier = Modifier.fillMaxWidth().scale(sc).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { pressed = true }, onDragEnd = { pressed = false },
                onDragCancel = { pressed = false }, onDrag = { _, _ -> }
            )
        },
        elevation = CardDefaults.cardElevation(defaultElevation = elev),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) { Box(Modifier.padding(12.dp)) { content() } }
}

@Composable
fun WordChip(word: String, color: Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.91f else 1f, label = "chip_scale")
    Surface(onClick = onClick, modifier = Modifier.scale(sc), shape = RoundedCornerShape(50), color = color, interactionSource = src) {
        Text(word, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

// ── Horizontal-scrolling action bar ──────────────────────────────────────────

@Composable
fun PopupActionBar(
    settings: AppSettings,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onCopy: () -> Unit,
    onSpeakWord: () -> Unit,
    onSpeakMeaning: () -> Unit,
    onTranslate: () -> Unit,
    onShare: () -> Unit,
    onSaveNote: () -> Unit,
    onFullDetails: () -> Unit,
    onSearchWeb: () -> Unit,
    onAddFlashcard: () -> Unit
) {
    // Button registry: id → (icon, label, action, enabled)
    data class BtnDef(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val action: () -> Unit, val enabled: Boolean)
    val registry = mapOf(
        "copy"      to BtnDef(Icons.Default.ContentCopy,    "Copy",      onCopy,          settings.showCopyButton),
        "speak"     to BtnDef(Icons.Default.VolumeUp,       "Speak",     onSpeakWord,     settings.showSpeakWordButton),
        "meaning"   to BtnDef(Icons.Default.RecordVoiceOver,"Meaning",   onSpeakMeaning,  settings.showSpeakMeaningButton),
        "translate" to BtnDef(Icons.Default.Translate,      "Translate", onTranslate,     settings.showTranslateButton),
        "share"     to BtnDef(Icons.Default.Share,          "Share",     onShare,         settings.showShareButton),
        "note"      to BtnDef(Icons.Default.Edit,           "Note",      onSaveNote,      settings.showSaveNoteButton),
        "details"   to BtnDef(Icons.Default.MenuBook,       "Details",   onFullDetails,   settings.showFullDetailsButton),
        "web"       to BtnDef(Icons.Default.Language,       "Web",       onSearchWeb,     settings.showSearchWebButton),
        "flashcard" to BtnDef(Icons.Default.Style,          "Flashcard", onAddFlashcard,  settings.showFlashcardButton)
    )

    val orderedEnabled = settings.buttonOrder
        .split(",")
        .map { it.trim() }
        .mapNotNull { id -> registry[id]?.takeIf { it.enabled } }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(orderedEnabled) { btn ->
            ActionButton(
                icon  = btn.icon,
                label = btn.label,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    btn.action()
                }
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc  by animateFloatAsState(if (pressed) 0.87f else 1f, label = "action_scale")
    val elv by animateDpAsState(if (pressed) 6.dp else 0.dp, label = "action_elev")
    Column(Modifier.scale(sc), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, shadowElevation = elv, color = Color.Transparent) {
            IconButton(onClick = onClick, interactionSource = src) {
                Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Bubble mode (legacy, kept for compat) ────────────────────────────────────

@Composable
fun BubbleMode(uiState: PopupUiState, onExpand: () -> Unit, modifier: Modifier) {
    val word = (uiState as? PopupUiState.Success)?.entry?.word ?: ""
    val pulse by rememberInfiniteTransition(label = "bubble_pulse").animateFloat(
        initialValue = 0.93f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "bubble_scale"
    )
    // 44dp — smaller, less intrusive
    Surface(
        onClick = onExpand,
        modifier = modifier.size(44.dp).scale(pulse),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 12.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = word.firstOrNull()?.uppercase() ?: "L",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ── Manual search ─────────────────────────────────────────────────────────────

@Composable
fun ManualSearchContent(
    query: String,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onVoiceSearch: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search any word…") },
                leadingIcon  = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = onVoiceSearch) {
                        Icon(Icons.Default.Mic, "Voice search", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                singleLine = true, shape = RoundedCornerShape(50)
            )
            Button(onClick = { if (query.isNotBlank()) onSearch(query.trim().split(" ").first()) }, shape = RoundedCornerShape(50)) {
                Text("GO")
            }
        }
        if (suggestions.isNotEmpty()) {
            Text("Suggestions:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            suggestions.take(5).forEach { suggestion ->
                Surface(
                    onClick = { onSearch(suggestion) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(suggestion, modifier = Modifier.padding(12.dp, 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ── Loading skeleton ──────────────────────────────────────────────────────────

@Composable
fun PopupSkeleton() {
    val shimmer by rememberInfiniteTransition(label = "sk").animateFloat(
        initialValue = 0.25f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "sk_alpha"
    )
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(6) {
            Box(
                Modifier
                    .fillMaxWidth(if (it == 0) 0.45f else if (it % 2 == 0) 1f else 0.75f)
                    .height(if (it == 0) 30.dp else 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = shimmer))
            )
        }
    }
}

// ── Error state ───────────────────────────────────────────────────────────────

@Composable
fun PopupError(message: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Hybrid AI comparison ──────────────────────────────────────────────────────

@Composable
fun HybridComparisonPanel(hybridAiResult: com.lexipopup.utils.ai.HybridAiResult) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖 AI Comparison", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable fun AiColumn(emoji: String, label: String, color: Color, text: String?) {
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            color = color.copy(alpha = 0.10f), border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f))) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("$emoji $label", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                                Text(text ?: "No result", style = MaterialTheme.typography.bodySmall,
                                    color = if (text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    AiColumn("🤖", "Groq",      Color(0xFFFF9800), hybridAiResult.groqEntry?.shortMeaning)
                    AiColumn("📱", "On-Device", Color(0xFFE91E63), hybridAiResult.onDeviceEntry?.shortMeaning)
                }
            }
        }
    }
}
