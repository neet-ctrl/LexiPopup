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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.lexipopup.R
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.utils.ParallaxOffset
import com.lexipopup.utils.SensorHelper
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.presentation.chat.AiChatScreen
import com.lexipopup.presentation.chat.AiChatViewModel
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
    val uiState        by viewModel.uiState.collectAsState()
    val settings       by viewModel.settings.collectAsState()
    val suggestions    by viewModel.suggestions.collectAsState()
    val searchQuery    by viewModel.searchQuery.collectAsState()
    val isBubble       by viewModel.isBubbleMode.collectAsState()
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

    // ── Bubble position ───────────────────────────────────────────────────────
    val bubbleX = remember { Animatable(0f) }
    val bubbleY = remember { Animatable(100f) }
    var edgeTabOffsetY by remember { mutableStateOf(0f) }

    // ── Window collapse mode ──────────────────────────────────────────────────
    var windowMode by remember { mutableStateOf(PopupWindowState.FULL) }
    // EDGE states take priority over BUBBLE to prevent full-screen touch blocking
    val effectiveWindowState = when {
        windowMode == PopupWindowState.EDGE_LEFT  -> PopupWindowState.EDGE_LEFT
        windowMode == PopupWindowState.EDGE_RIGHT -> PopupWindowState.EDGE_RIGHT
        isBubble                              -> PopupWindowState.BUBBLE
        else                                  -> PopupWindowState.FULL
    }

    // Snap the bubble/collapsed handle to the nearest screen edge (left or right).
    // This sets windowMode to EDGE_* so the window shrinks to WRAP_CONTENT and
    // touches outside the handle pass through to the underlying app.
    fun snapBubbleToEdge() {
        val side = if (bubbleX.value >= 0f) PopupWindowState.EDGE_RIGHT else PopupWindowState.EDGE_LEFT
        windowMode = side
        scope.launch { if (isBubble) viewModel.toggleBubble() }
    }

    // If isBubble is ever set programmatically, immediately redirect to an edge
    // collapse so we never leave a MATCH_PARENT transparent window blocking touches.
    LaunchedEffect(isBubble) {
        if (isBubble) {
            windowMode = PopupWindowState.EDGE_RIGHT
            viewModel.toggleBubble()
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

    // ── Window size state ─────────────────────────────────────────────────────
    var widthFraction  by remember { mutableStateOf(settings.popupWidthFraction.coerceIn(0.50f, 0.95f)) }
    var heightFraction by remember { mutableStateOf(settings.popupHeightFraction.coerceIn(0.35f, 0.92f)) }

    // ── Pass touches through to underlying app in bubble/edge modes ─────────────
    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    SideEffect {
        val win = activity?.window ?: return@SideEffect
        val flagNotTouchModal = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val flagNotFocusable  = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val wrapContent       = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        val matchParent       = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        when (effectiveWindowState) {
            PopupWindowState.EDGE_LEFT,
            PopupWindowState.EDGE_RIGHT -> {
                win.addFlags(flagNotTouchModal)
                win.addFlags(flagNotFocusable)
                // Shrink window to just the tab so every touch outside the circle
                // passes through to the app underneath.
                win.setLayout(wrapContent, wrapContent)
                val attrs = win.attributes
                attrs.gravity = (if (effectiveWindowState == PopupWindowState.EDGE_LEFT)
                    android.view.Gravity.START else android.view.Gravity.END) or
                    android.view.Gravity.CENTER_VERTICAL
                attrs.y = edgeTabOffsetY.toInt()
                win.attributes = attrs
            }
            PopupWindowState.BUBBLE -> {
                // Treat BUBBLE same as EDGE_RIGHT: WRAP_CONTENT so window only
                // covers the handle — touches outside pass through freely.
                win.addFlags(flagNotTouchModal)
                win.addFlags(flagNotFocusable)
                win.setLayout(wrapContent, wrapContent)
                val attrs = win.attributes
                attrs.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                attrs.y = 0
                win.attributes = attrs
            }
            else -> {
                win.clearFlags(flagNotTouchModal)
                win.clearFlags(flagNotFocusable)
                win.setLayout(matchParent, matchParent)
                val attrs = win.attributes
                attrs.gravity = android.view.Gravity.NO_GRAVITY
                win.attributes = attrs
            }
        }
    }

    // ── Parallax tilt ─────────────────────────────────────────────────────────
    var parallax by remember { mutableStateOf(ParallaxOffset(0f, 0f)) }
    LaunchedEffect(Unit) {
        SensorHelper.parallaxFlow(context).distinctUntilChanged().collect { parallax = it }
    }

    // ── Entrance / exit animation ─────────────────────────────────────────────
    var isClosing   by remember { mutableStateOf(false) }
    var scaleTarget by remember { mutableStateOf(0.88f) }
    var alphaTarget by remember { mutableStateOf(0f) }
    val scaleAnim by animateFloatAsState(
        targetValue   = scaleTarget,
        animationSpec = if (isClosing) tween(180, easing = FastOutLinearInEasing)
                        else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "popup_scale"
    )
    val alphaAnim by animateFloatAsState(
        targetValue   = alphaTarget,
        animationSpec = tween(if (isClosing) 160 else 130),
        label = "popup_alpha"
    )
    LaunchedEffect(Unit) { scaleTarget = 1f; alphaTarget = 1f }
    LaunchedEffect(isClosing) {
        if (isClosing) { scaleTarget = 0.92f; alphaTarget = 0f; delay(200); onClose() }
    }
    val safeClose = { isClosing = true }

    // ── Shimmer border ────────────────────────────────────────────────────────
    val shimmerInf = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerInf.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "shimmer_offset"
    )

    // ── Dialog state ──────────────────────────────────────────────────────────
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText       by remember { mutableStateOf("") }
    var showParticles  by remember { mutableStateOf(false) }

    // ── AI Chat mode ──────────────────────────────────────────────────────────
    var showAiChat       by remember { mutableStateOf(false) }
    val aiChatViewModel: AiChatViewModel = hiltViewModel()
    val currentWordForChat by remember {
        derivedStateOf { (uiState as? PopupUiState.Success)?.entry?.word ?: "" }
    }
    LaunchedEffect(showAiChat) {
        if (showAiChat && currentWordForChat.isNotBlank()) {
            aiChatViewModel.startNewSession()
            delay(120)
            aiChatViewModel.sendMessage("Let's talk more about this word \"${currentWordForChat}\"")
        }
    }

    // ── Layer picker state ────────────────────────────────────────────────────
    val showLayerPicker by viewModel.showLayerPicker.collectAsState()
    val forcingLayerId  by viewModel.forcingLayerId.collectAsState()
    val activeLayers    by viewModel.activeLayers.collectAsState()

    // ── Voice search ──────────────────────────────────────────────────────────
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

    // ── Collapse helpers ──────────────────────────────────────────────────────
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
        // Always collapse to the nearest screen edge — never use BUBBLE mode which
        // would leave a MATCH_PARENT window blocking all touches underneath.
        val side = if (animX.value >= 0f) PopupWindowState.EDGE_RIGHT else PopupWindowState.EDGE_LEFT
        collapseToEdge(side)
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
            val cardFraction = if (isLandscape) 0.65f else widthFraction
            val maxEdgeX = (screenW * (1f - cardFraction)) / 2f * 0.9f
            val snapX = when {
                animX.value > maxEdgeX * 0.3f ->  maxEdgeX
                animX.value < -maxEdgeX * 0.3f -> -maxEdgeX
                else -> 0f
            }
            launch { animX.animateTo(snapX, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
            launch { animY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
            viewModel.saveWindowPosition(snapX, 0f)
        }
    }

    LaunchedEffect(widthFraction, heightFraction) {
        viewModel.saveWindowSize(widthFraction, heightFraction)
    }

    // ── Save Note dialog ──────────────────────────────────────────────────────
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

    // ── Root container ────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        // Scrim — FULL mode only
        if (effectiveWindowState == PopupWindowState.FULL) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { safeClose() }
            )
        }

        AnimatedContent(
            targetState = effectiveWindowState,
            transitionSpec = {
                val toEdge   = targetState  == PopupWindowState.EDGE_LEFT || targetState  == PopupWindowState.EDGE_RIGHT
                val fromEdge = initialState == PopupWindowState.EDGE_LEFT || initialState == PopupWindowState.EDGE_RIGHT
                when {
                    toEdge   -> fadeIn(tween(180)) togetherWith fadeOut(tween(150))
                    fromEdge -> (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.7f), 0.82f)) togetherWith fadeOut(tween(150))
                    targetState == PopupWindowState.BUBBLE ->
                        (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.7f), 0.5f)) togetherWith
                        (fadeOut(tween(180)) + scaleOut(tween(180), 0.5f))
                    else -> (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.8f), 0.88f)) togetherWith fadeOut(tween(150))
                }
            },
            label = "window_state"
        ) { state ->
            when (state) {

                PopupWindowState.BUBBLE -> {
                    // BUBBLE state is immediately redirected to EDGE in LaunchedEffect(isBubble).
                    // This fallback just shows the edge handle so there's no blank frame.
                    CollapsedEdgeHandle(
                        side = "right",
                        onExpand = { windowMode = PopupWindowState.FULL }
                    )
                }

                PopupWindowState.EDGE_LEFT -> {
                    CollapsedEdgeHandle(
                        side = "left",
                        onExpand = { windowMode = PopupWindowState.FULL; edgeTabOffsetY = 0f },
                        modifier = Modifier.pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                edgeTabOffsetY += dragAmount.y
                            }
                        }
                    )
                }

                PopupWindowState.EDGE_RIGHT -> {
                    CollapsedEdgeHandle(
                        side = "right",
                        onExpand = { windowMode = PopupWindowState.FULL; edgeTabOffsetY = 0f },
                        modifier = Modifier.pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                edgeTabOffsetY += dragAmount.y
                            }
                        }
                    )
                }

                // ── Full floating window ──────────────────────────────────────
                PopupWindowState.FULL -> {
                    val parallaxXPx = with(density) { parallax.x.dp.toPx() }
                    val parallaxYPx = with(density) { parallax.y.dp.toPx() }
                    val cardW = if (isLandscape) 0.68f else widthFraction.coerceIn(0.52f, 0.96f)
                    val cardH = if (isLandscape) 0.92f else heightFraction.coerceIn(0.38f, 0.92f)

                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val glassSurface = MaterialTheme.colorScheme.surface
                        val glassPrimary = MaterialTheme.colorScheme.primary
                        val isSolid = settings.popupBgAlpha >= 1.0f
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
                                    shadowElevation = 40f
                                    shape = RoundedCornerShape(24.dp)
                                    clip = true
                                    rotationX = (parallax.y * 0.35f).coerceIn(-2f, 2f)
                                    rotationY = (-parallax.x * 0.35f).coerceIn(-2f, 2f)
                                    cameraDistance = 12f * density.density
                                    alpha = alphaAnim * settings.popupBgAlpha
                                }
                                .border(
                                    width = if (isSolid) 2.dp else 1.dp,
                                    brush = if (isSolid) Brush.linearGradient(
                                        colors = listOf(
                                            glassPrimary,
                                            MaterialTheme.colorScheme.tertiary,
                                            MaterialTheme.colorScheme.secondary,
                                            glassPrimary.copy(alpha = 0.6f)
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(
                                            x = (shimmerOffset + 1f) * 500f,
                                            y = 0f
                                        ),
                                        end = androidx.compose.ui.geometry.Offset(
                                            x = (shimmerOffset + 1f) * 500f + 800f,
                                            y = 800f
                                        )
                                    ) else Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.45f),
                                            glassPrimary.copy(alpha = 0.20f),
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { /* consume */ },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    glassSurface.copy(alpha = if (isSolid) 1.0f else 0.82f)
                                else
                                    glassSurface.copy(alpha = if (isSolid) 1.0f else 0.97f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 22.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {

                                // Glassmorphism background layer
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Box(Modifier.fillMaxSize().blur(32.dp)
                                        .background(
                                            Brush.verticalGradient(listOf(
                                                glassSurface.copy(alpha = 0.55f),
                                                glassPrimary.copy(alpha = 0.06f),
                                                glassSurface.copy(alpha = 0.50f)
                                            ))
                                        ))
                                } else {
                                    Box(Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(listOf(
                                            glassSurface.copy(alpha = 0.98f),
                                            glassSurface.copy(alpha = 0.95f),
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.06f)
                                        ))
                                    ))
                                }

                                Column(Modifier.fillMaxSize()) {

                                    // ── Shimmer accent line ───────────────────
                                    Box(
                                        Modifier.fillMaxWidth().height(3.dp).background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                    Color.Transparent
                                                ),
                                                startX = shimmerOffset * 1200f,
                                                endX   = (shimmerOffset + 1f) * 1200f
                                            )
                                        )
                                    )

                                    // ── Drag pill ─────────────────────────────
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 2.dp)
                                            .pointerInput(Unit) {
                                                detectDragGestures(onDragEnd = {
                                                    if (settings.enableDragging) handleDragEnd()
                                                }) { _, d ->
                                                    if (settings.enableDragging) {
                                                        scope.launch {
                                                            val bound = 360f; val rf = 0.25f
                                                            animX.snapTo(animX.value + if (abs(animX.value) < bound) d.x else d.x * rf)
                                                            animY.snapTo(animY.value + if (abs(animY.value) < bound) d.y else d.y * rf)
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier
                                                .width(36.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                                        )
                                    }

                                    if (showAiChat) {
                                        PopupAiChatContent(
                                            word      = currentWordForChat,
                                            viewModel = aiChatViewModel,
                                            onBack    = { showAiChat = false },
                                            onClose   = safeClose
                                        )
                                    } else {
                                    // ── Header ────────────────────────────────
                                    PopupHeader(
                                        uiState          = uiState,
                                        settings         = settings,
                                        onDrag           = { dx, dy ->
                                            if (settings.enableDragging) {
                                                scope.launch {
                                                    val bound = 360f; val rf = 0.25f
                                                    animX.snapTo(animX.value + if (abs(animX.value) < bound) dx else dx * rf)
                                                    animY.snapTo(animY.value + if (abs(animY.value) < bound) dy else dy * rf)
                                                }
                                            }
                                        },
                                        onDragEnd        = { if (settings.enableDragging) handleDragEnd() },
                                        onClose          = safeClose,
                                        onCollapse       = { collapseButtonTapped() },
                                        onSpeakWord      = { viewModel.speakWord(context) },
                                        onToggleFavorite = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleFavorite()
                                            showParticles = true
                                        },
                                        onManualSearch   = { viewModel.setManualSearchMode() }
                                    )

                                    // ── Word info row (POS + frequency + difficulty) ──
                                    val successEntry = (uiState as? PopupUiState.Success)?.entry
                                    if (successEntry != null) {
                                        WordInfoRow(entry = successEntry, settings = settings)
                                    }

                                    HorizontalDivider(
                                        thickness = 0.6.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    // ── Body ──────────────────────────────────
                                    Box(Modifier.weight(1f)) {
                                        androidx.compose.animation.Crossfade(
                                            targetState   = uiState,
                                            animationSpec = tween(200),
                                            label         = "content_crossfade"
                                        ) { st ->
                                            when (st) {
                                                is PopupUiState.Loading -> PopupSkeleton()
                                                is PopupUiState.Success -> PopupContent(
                                                    entry              = st.entry,
                                                    settings           = settings,
                                                    onWordChipClick    = { w -> viewModel.lookupWord(w) },
                                                    onSourceBadgeClick = { viewModel.showLayerPicker() }
                                                )
                                                is PopupUiState.Error -> PopupError(st.message)
                                                is PopupUiState.ManualSearch,
                                                is PopupUiState.Idle  -> ManualSearchContent(
                                                    query         = searchQuery,
                                                    suggestions   = suggestions,
                                                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                                                    onSearch      = { viewModel.lookupWord(it) },
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

                                    // ── Layer picker panel ────────────────────
                                    AnimatedVisibility(
                                        visible = showLayerPicker && uiState is PopupUiState.Success,
                                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                        exit  = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                                    ) {
                                        LayerPickerPanel(
                                            activeLayers   = activeLayers,
                                            forcingLayerId = forcingLayerId,
                                            onLayerClick   = { viewModel.lookupWithLayer(it) },
                                            onDismiss      = { viewModel.hideLayerPicker() }
                                        )
                                    }

                                    // ── Hybrid AI comparison ──────────────────
                                    if (uiState is PopupUiState.Success &&
                                        settings.hybridShowComparison &&
                                        hybridAiResult != null
                                    ) {
                                        HorizontalDivider(thickness = 0.5.dp)
                                        HybridComparisonPanel(hybridAiResult = hybridAiResult!!)
                                    }

                                    // ── Bottom action grid ────────────────────
                                    if (uiState is PopupUiState.Success) {
                                        HorizontalDivider(
                                            thickness = 0.6.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                        PopupActionGrid(
                                            settings       = settings,
                                            haptic         = haptic,
                                            onCopy         = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.copyToClipboard(context) },
                                            onOpenBrowser  = { viewModel.openInBrowser(context) },
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
                                            onAddFlashcard = { viewModel.addFlashcard() },
                                            onAiChat       = { showAiChat = true }
                                        )
                                    }

                                    // ── Resize corner handle ──────────────────
                                    if (settings.enableResizing) {
                                        Box(
                                            Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.BottomEnd
                                        ) {
                                            Icon(
                                                Icons.Default.OpenInFull,
                                                contentDescription = "Resize",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .padding(bottom = 3.dp, end = 3.dp)
                                                    .pointerInput(Unit) {
                                                        detectDragGestures { _, dragAmount ->
                                                            val dy = dragAmount.y / size.height.coerceAtLeast(1)
                                                            val dx = dragAmount.x / size.width.coerceAtLeast(1)
                                                            heightFraction = (heightFraction + dy).coerceIn(0.38f, 0.92f)
                                                            widthFraction  = (widthFraction  + dx * 0.5f).coerceIn(0.52f, 0.96f)
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                    } // end else (showAiChat)

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
                    } // end Box Center
                }
            } // when
        } // AnimatedContent
    } // root Box
}

// ── Header ─────────────────────────────────────────────────────────────────────
// Screenshot layout: [☰]  WORD 🔊  /pron/          [★][✕]

@Composable
fun PopupHeader(
    uiState: PopupUiState,
    settings: AppSettings,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    onSpeakWord: () -> Unit,
    onToggleFavorite: () -> Unit,
    onManualSearch: () -> Unit
) {
    val isFav = (uiState as? PopupUiState.Success)?.entry?.isFavorite ?: false
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 6.dp)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onDragEnd() }) { _, d -> onDrag(d.x, d.y) }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: menu/collapse button in rounded-square container
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Always allow collapsing to edge — onCollapse now always snaps to edge.
                    onCollapse()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        // Center: WORD [🔊] /pronunciation/ — all on ONE row (matching screenshot exactly)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when (val st = uiState) {
                is PopupUiState.Success -> {
                    // Word — bold, large
                    Text(
                        text = st.entry.word.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Speaker icon — inline, same row as word
                    if (settings.showSpeakWordButton) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.13f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSpeakWord() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Speak",
                                tint = primaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    // Pronunciation — same row, immediately after speaker icon
                    if (settings.showPronunciation && st.entry.pronunciation.isNotBlank()) {
                        Text(
                            text = st.entry.pronunciation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                is PopupUiState.Loading ->
                    Text(
                        "Looking up…",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                else ->
                    Text(
                        "LexiPopup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
            }
        }

        Spacer(Modifier.width(4.dp))

        // Right: star + close in rounded-square containers
        if (uiState is PopupUiState.Success && settings.showFavoriteButton) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(4.dp))
    }
}

// ── Word info sub-row (POS chip + frequency + difficulty) ─────────────────────

@Composable
fun WordInfoRow(entry: WordEntry, settings: AppSettings) {
    val posColor = Color(entry.partOfSpeechColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // POS chip — solid filled background (matches screenshot "Verb" chip style)
        if (settings.showPartOfSpeech && entry.partOfSpeech.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = posColor
            ) {
                Text(
                    text = entry.partOfSpeech.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Frequency mini bars
        if (settings.showFrequencyMeter) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Frequency: ${entry.frequencyRating}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
                // 5-bar frequency indicator
                val bars = (entry.frequencyRating / 20f).roundToInt().coerceIn(0, 5)
                val barColor = MaterialTheme.colorScheme.primary
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i ->
                        Box(
                            Modifier
                                .width(3.dp)
                                .height((8 + i * 2).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i < bars) barColor else barColor.copy(alpha = 0.22f))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Difficulty label
        if (settings.showDifficultyBadge) {
            Text(
                text = "Difficulty: ${entry.difficultyLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Body content ──────────────────────────────────────────────────────────────

@Composable
fun PopupContent(
    entry: WordEntry,
    settings: AppSettings,
    onWordChipClick: (String) -> Unit,
    onSourceBadgeClick: () -> Unit = {}
) {
    val scroll    = rememberScrollState()
    val context   = LocalContext.current
    val primary   = MaterialTheme.colorScheme.primary
    val tertiary  = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary

    // Helper: copy any text to clipboard instantly
    fun copyField(label: String, text: String) {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ── Short Meaning ─────────────────────────────────────────────────────
        if (entry.shortMeaning.isNotBlank()) {
            ContentBulletRow(
                bulletColor = primary,
                label  = "📖 Meaning",
                text   = entry.shortMeaning,
                italic = false,
                onCopy = { copyField("Meaning", entry.shortMeaning) }
            )
        }

        // ── Detailed Meaning (only if different from short) ───────────────────
        if (settings.showDetailedMeaning &&
            entry.detailedMeaning.isNotBlank() &&
            entry.detailedMeaning != entry.shortMeaning
        ) {
            ContentBulletRow(
                bulletColor = Color(0xFF7C4DFF),
                label   = "📖 Detailed Meaning",
                text    = entry.detailedMeaning,
                italic  = false,
                diamond = true,
                onCopy  = { copyField("Detailed Meaning", entry.detailedMeaning) }
            )
        }

        // ── Hindi ─────────────────────────────────────────────────────────────
        if (settings.showHindiMeaning && entry.hindiMeaning.isNotBlank()) {
            val hindiCopyText = buildString {
                append(entry.hindiMeaning)
                if (entry.hindiPronunciation.isNotBlank()) append(" (${entry.hindiPronunciation})")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "Hindi:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("🇮🇳", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            entry.hindiMeaning,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false)
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
                // Copy icon for Hindi field
                FieldCopyButton { copyField("Hindi", hindiCopyText) }
            }
        }

        // ── Example ───────────────────────────────────────────────────────────
        if (settings.showExampleSentence && entry.exampleSentence.isNotBlank()) {
            val cleanExample = entry.exampleSentence.trim('"', '\u201C', '\u201D')
            ContentBulletRow(
                bulletColor = tertiary,
                label  = "📝 Example",
                text   = "\"$cleanExample\"",
                italic = true,
                onCopy = { copyField("Example", cleanExample) }
            )
        }

        // ── Etymology ─────────────────────────────────────────────────────────
        if (settings.showEtymology && entry.etymology.isNotBlank()) {
            ContentBulletRow(
                bulletColor = secondary,
                label  = "🌱 Etymology",
                text   = entry.etymology,
                italic = false,
                onCopy = { copyField("Etymology", entry.etymology) }
            )
        }

        // ── Synonyms ──────────────────────────────────────────────────────────
        if (settings.showSynonyms && entry.synonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🔗 Synonyms",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        modifier = Modifier.weight(1f)
                    )
                    FieldCopyButton { copyField("Synonyms", entry.synonyms.joinToString(", ")) }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(entry.synonyms) { syn ->
                        WordChip(syn, MaterialTheme.colorScheme.primaryContainer) { onWordChipClick(syn) }
                    }
                }
            }
        }

        // ── Antonyms ──────────────────────────────────────────────────────────
        if (settings.showAntonyms && entry.antonyms.isNotEmpty()) {
            val errorColor = MaterialTheme.colorScheme.error
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚡ Antonyms",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor,
                        modifier = Modifier.weight(1f)
                    )
                    FieldCopyButton { copyField("Antonyms", entry.antonyms.joinToString(", ")) }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(entry.antonyms) { ant ->
                        WordChip(ant, MaterialTheme.colorScheme.errorContainer) { onWordChipClick(ant) }
                    }
                }
            }
        }

        // ── Difficulty + Frequency progress bars ──────────────────────────────
        val showDiff = settings.showDifficultyBadge
        val showFreq = settings.showFrequencyMeter
        if (showDiff || showFreq) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showDiff) {
                    val diffColors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFf44336))
                    val diffColor  = diffColors.getOrElse(entry.difficultyLevel - 1) { diffColors[0] }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🎯 Difficulty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.difficultyLabel, style = MaterialTheme.typography.labelSmall, color = diffColor, fontWeight = FontWeight.Medium)
                        }
                        LinearProgressIndicator(
                            progress = { entry.difficultyLevel / 4f },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                            color = diffColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
                if (showFreq) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📊 Frequency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${entry.frequencyRating}%", style = MaterialTheme.typography.labelSmall, color = primary, fontWeight = FontWeight.Medium)
                        }
                        LinearProgressIndicator(
                            progress = { entry.frequencyRating / 100f },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                            color = primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // ── Source badge (tap to open layer picker) ───────────────────────────
        SourceLayerBadge(source = entry.source, onClick = onSourceBadgeClick)
        Spacer(Modifier.height(4.dp))
    }
}

// ── Tiny copy icon button used next to each field ─────────────────────────────

@Composable
private fun FieldCopyButton(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    val scope  = rememberCoroutineScope()
    val tint   = if (copied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val sc by animateFloatAsState(if (copied) 1.18f else 1f, label = "copy_scale")

    Box(
        modifier = Modifier
            .size(28.dp)
            .scale(sc)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onCopy()
                copied = true
                scope.launch { delay(1400); copied = false }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (copied) "Copied" else "Copy",
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ── Bullet row helper (Why Important / Meaning / Example) ─────────────────────

@Composable
private fun ContentBulletRow(
    bulletColor: Color,
    label: String,
    text: String,
    italic: Boolean,
    onCopy: () -> Unit,
    diamond: Boolean = false   // true → ♦ rotated square; false → • circle (matches screenshot)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Bullet shape: ♦ diamond for "Why Important", • circle for the rest
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(7.dp)
                .rotate(if (diamond) 45f else 0f)
                .clip(if (diamond) RoundedCornerShape(1.5.dp) else CircleShape)
                .background(bulletColor)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$label:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = bulletColor
            )
            Text(
                text,
                style = if (italic)
                    MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic)
                else
                    MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        // Copy icon for this field
        FieldCopyButton(onCopy = onCopy)
    }
}

// ── Two-column small card helper ──────────────────────────────────────────────

@Composable
private fun TwoColCard(
    label: String,
    labelColor: Color,
    text: String,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit = {}
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                    modifier = Modifier.weight(1f)
                )
                FieldCopyButton(onCopy = onCopy)
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Source layer badge ────────────────────────────────────────────────────────

@Composable
private fun SourceLayerBadge(source: String, onClick: () -> Unit = {}) {
    if (source.isBlank()) return
    data class LI(val emoji: String, val label: String, val color: Color)
    val info = when (source.lowercase()) {
        "seed"       -> LI("🌱", "Seed DB",       Color(0xFF4CAF50))
        "minimal"    -> LI("📦", "Minimal Pack",  Color(0xFF2196F3))
        "standard"   -> LI("📚", "Standard Pack", Color(0xFF3F51B5))
        "full"       -> LI("🗄",  "Full Pack",     Color(0xFF9C27B0))
        "online"     -> LI("🌐", "Online API",    Color(0xFF009688))
        "groq"       -> LI("🤖", "Groq AI",       Color(0xFFFF9800))
        "openai"     -> LI("🤖", "OpenAI",        Color(0xFF43A047))
        "on_device"  -> LI("📱", "On-Device AI",  Color(0xFFE91E63))
        "rule_based" -> LI("🔧", "Rule-Based",    Color(0xFF607D8B))
        else         -> LI("📄", source.replaceFirstChar { it.uppercase() }, Color(0xFF78909C))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = info.color.copy(alpha = 0.12f),
            border = BorderStroke(0.5.dp, info.color.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${info.emoji} ${info.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = info.color,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = "Change source",
                    tint = info.color.copy(alpha = 0.7f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

// ── Layer picker panel (slides in between body and action grid) ───────────────

private val LAYER_PICKER_META: Map<String, Pair<String, String>> = mapOf(
    "word_history" to ("🕘" to "Word History"),
    "offline_db"   to ("📚" to "Offline DB"),
    "online_api"   to ("🌐" to "Online API"),
    "groq_ai"      to ("🤖" to "Groq AI"),
    "openai"       to ("💼" to "OpenAI"),
    "on_device_ai" to ("📱" to "On-Device AI"),
    "rule_based"   to ("🔧" to "Rule-Based")
)

@Composable
private fun LayerPickerPanel(
    activeLayers: List<String>,
    forcingLayerId: String?,
    onLayerClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Re-fetch with a different source:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(activeLayers) { layerId ->
                    val (emoji, name) = LAYER_PICKER_META[layerId] ?: ("⚙️" to layerId)
                    val isLoading = layerId == forcingLayerId
                    FilterChip(
                        selected = isLoading,
                        onClick = { if (forcingLayerId == null) onLayerClick(layerId) },
                        label = {
                            if (isLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp
                                    )
                                    Text("$emoji $name", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Text("$emoji $name", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                }
            }
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
    val src     = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.91f else 1f, label = "chip_scale")
    Surface(
        onClick = onClick,
        modifier = Modifier.scale(sc),
        shape = RoundedCornerShape(50),
        color = color,
        interactionSource = src
    ) {
        Text(word, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

// ── 2-row action grid  +  More overflow bottom sheet ──────────────────────────
// Layout: row1 = first 5 enabled, row2 = next 4 enabled + "More" at slot 5
// "More" opens a ModalBottomSheet showing every overflow button in a grid.

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PopupActionGrid(
    settings: AppSettings,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onCopy: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSpeakWord: () -> Unit,
    onSpeakMeaning: () -> Unit,
    onTranslate: () -> Unit,
    onShare: () -> Unit,
    onSaveNote: () -> Unit,
    onFullDetails: () -> Unit,
    onSearchWeb: () -> Unit,
    onAddFlashcard: () -> Unit,
    onAiChat: () -> Unit = {}
) {
    data class BtnDef(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val label: String,
        val action: () -> Unit
    )
    data class BtnDefWithId(
        val id: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val label: String,
        val action: () -> Unit,
        val enabled: Boolean
    )

    val allButtons = listOf(
        BtnDefWithId("ai",        Icons.Default.AutoAwesome,     "AI\nChat",      onAiChat,        true),
        BtnDefWithId("copy",      Icons.Default.ContentCopy,     "Copy",          onCopy,          settings.showCopyButton),
        BtnDefWithId("speak",     Icons.Default.VolumeUp,        "Speak\nWord",   onSpeakWord,     settings.showSpeakWordButton),
        BtnDefWithId("meaning",   Icons.Default.RecordVoiceOver, "Speak\nMeaning",onSpeakMeaning,  settings.showSpeakMeaningButton),
        BtnDefWithId("translate", Icons.Default.Translate,       "Translate",     onTranslate,     settings.showTranslateButton),
        BtnDefWithId("share",     Icons.Default.Share,           "Share",         onShare,         settings.showShareButton),
        BtnDefWithId("note",      Icons.Default.Edit,            "Save\nNote",    onSaveNote,      settings.showSaveNoteButton),
        BtnDefWithId("details",   Icons.Default.MenuBook,        "Full\nDetails", onFullDetails,   settings.showFullDetailsButton),
        BtnDefWithId("web",       Icons.Default.Language,        "Search\nWeb",   onSearchWeb,     settings.showSearchWebButton),
        BtnDefWithId("flashcard", Icons.Default.Style,           "Flashcard",     onAddFlashcard,  settings.showFlashcardButton),
        BtnDefWithId("browser",   Icons.Default.OpenInBrowser,   "Browser",       onOpenBrowser,   settings.showBrowserButton)
    )

    val orderIds = settings.buttonOrder.split(",").map { it.trim() }
    val sortedButtons = allButtons.sortedBy { btn ->
        val idx = orderIds.indexOf(btn.id)
        if (idx == -1) Int.MAX_VALUE else idx
    }
    val enabled = sortedButtons.filter { it.enabled }.map { b -> BtnDef(b.icon, b.label, b.action) }
    if (enabled.isEmpty()) return

    // Expanded grid splits: first 9 in 2 rows, rest in More sheet
    val visible  = enabled.take(9)
    val overflow = enabled.drop(9)
    val hasMore  = overflow.isNotEmpty()
    val row1     = visible.take(5)
    val row2     = visible.drop(5)

    var showSheet  by remember { mutableStateOf(false) }
    var expanded   by rememberSaveable { mutableStateOf(false) }

    val primary  = MaterialTheme.colorScheme.primary
    val surface  = MaterialTheme.colorScheme.surfaceVariant
    val onSurf   = MaterialTheme.colorScheme.onSurfaceVariant

    // ── Outer container — animates height between collapsed and expanded ───────
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface.copy(alpha = 0.18f))
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {

        if (!expanded) {
            // ── COLLAPSED: single thin scrollable icon row + ▼ expand button ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scrollable icon strip
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(enabled) { btn ->
                        TrayIconButton(
                            icon    = btn.icon,
                            label   = btn.label,
                            primary = primary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                btn.action()
                            }
                        )
                    }
                }
                // Thin vertical divider
                Box(
                    Modifier
                        .padding(vertical = 10.dp)
                        .width(0.6.dp)
                        .fillMaxHeight()
                        .background(onSurf.copy(alpha = 0.20f))
                )
                // ▼ expand button — small square, right-pinned
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(primary.copy(alpha = 0.10f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { expanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand toolbar",
                        tint     = primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

        } else {
            // ── EXPANDED: 2-row grid (current design) + ▲ collapse button ────

            // Row 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row1.forEach { btn ->
                    GridActionButton(
                        icon    = btn.icon,
                        label   = btn.label,
                        modifier = Modifier.weight(1f),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); btn.action() }
                    )
                }
                repeat(5 - row1.size) { Spacer(Modifier.weight(1f)) }
            }

            // Row 2 (if any row-2 buttons or More needed)
            if (row2.isNotEmpty() || hasMore) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row2.forEach { btn ->
                        GridActionButton(
                            icon    = btn.icon,
                            label   = btn.label,
                            modifier = Modifier.weight(1f),
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); btn.action() }
                        )
                    }
                    if (hasMore) {
                        GridActionButton(
                            icon    = Icons.Default.MoreHoriz,
                            label   = "More",
                            modifier = Modifier.weight(1f),
                            onClick = { showSheet = true }
                        )
                    }
                    val filled = row2.size + if (hasMore) 1 else 0
                    repeat(5 - filled) { Spacer(Modifier.weight(1f)) }
                }
            }

            // Bottom bar: page dots + ▲ collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page dots (centred in remaining space)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotActive   = primary
                    val dotInactive = onSurf.copy(alpha = 0.28f)
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == 0) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(if (i == 0) dotActive else dotInactive)
                        )
                    }
                }
                // ▲ collapse button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(primary.copy(alpha = 0.10f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { expanded = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Collapse toolbar",
                        tint     = primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // ── More overflow bottom sheet ────────────────────────────────────────────
    if (showSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    "More Actions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                )
                overflow.chunked(5).forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { btn ->
                            GridActionButton(
                                icon    = btn.icon,
                                label   = btn.label,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    btn.action()
                                    showSheet = false
                                }
                            )
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ── Compact tray icon button (used in collapsed toolbar row) ──────────────────
@Composable
private fun TrayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Color,
    onClick: () -> Unit
) {
    val src     = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.85f else 1f, label = "tray_sc")

    Box(
        modifier = Modifier
            .scale(sc)
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(primary.copy(alpha = if (pressed) 0.18f else 0.09f))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label.replace("\n", " "),
            tint               = primary,
            modifier           = Modifier.size(18.dp)
        )
    }
}

@Composable
fun GridActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val src     = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc  by animateFloatAsState(if (pressed) 0.88f else 1f,  label = "btn_scale")
    val bgAlpha by animateFloatAsState(if (pressed) 0.18f else 0f, label = "btn_bg")
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .scale(sc)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(primary.copy(alpha = 0.10f + bgAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

// ── Collapsed edge handle — small vertical rounded rectangle with app icon ─────
// Snaps to left or right screen edge (window uses WRAP_CONTENT + gravity, so
// only this rectangle covers the screen — everything else is fully touchable).

@Composable
fun CollapsedEdgeHandle(
    side: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLeft = side == "left"
    val primary   = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer

    val inf = rememberInfiniteTransition(label = "handle_inf")
    val glowAlpha by inf.animateFloat(
        initialValue = 0.30f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "handle_glow"
    )

    // Rounded rect: left side has full rounded right corners; right side has full rounded left corners
    val shape = if (isLeft)
        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 6.dp, bottomStart = 6.dp)
    else
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 6.dp, bottomEnd = 6.dp)

    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.93f else 1f, label = "handle_sc")

    Box(
        modifier = modifier
            .scale(sc)
            .width(44.dp)
            .height(84.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                spotColor = primary.copy(alpha = 0.45f),
                ambientColor = primary.copy(alpha = 0.20f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        container.copy(alpha = 0.96f),
                        primary.copy(alpha = 0.88f)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glowAlpha),
                        primary.copy(alpha = glowAlpha * 0.5f),
                        Color.White.copy(alpha = glowAlpha * 0.7f)
                    )
                ),
                shape = shape
            )
            .clickable(interactionSource = src, indication = null) { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = "Expand LexiPopup",
            tint = Color.Unspecified,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

// ── Kept for backward-compat call sites ──────────────────────────────────────

@Composable
fun EdgeCollapsedTab(
    side: String,
    word: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) = CollapsedEdgeHandle(side = side, onExpand = onExpand, modifier = modifier)

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
                color  = colors[i % colors.size].copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                radius = 6f * (1f - progress * 0.5f),
                center = Offset(center.x + r * kotlin.math.cos(angle), center.y + r * kotlin.math.sin(angle))
            )
        }
    }
}

// ── Bubble mode (legacy — redirected to edge on appearance) ───────────────────

@Composable
fun BubbleMode(uiState: PopupUiState, modifier: Modifier) {
    // BubbleMode is no longer shown to users; CollapsedEdgeHandle replaced it.
    // This stub exists only for backward-compat compilation.
    CollapsedEdgeHandle(side = "right", onExpand = {}, modifier = modifier)
}

// ── Manual search — full glassmorphic redesign ────────────────────────────────

@Composable
fun ManualSearchContent(
    query: String,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onVoiceSearch: () -> Unit = {}
) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary  = MaterialTheme.colorScheme.tertiary
    val surface   = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfVar   = MaterialTheme.colorScheme.surfaceVariant

    val inf = rememberInfiniteTransition(label = "ms_inf")

    // Shimmer sweep on accent bar
    val shimmerX by inf.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "ms_shimmer"
    )

    // Floating glow pulse on icon
    val iconPulse by inf.animateFloat(
        initialValue = 0.90f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ms_icon_pulse"
    )

    // Glow intensity when search box is focused
    var isFocused by remember { mutableStateOf(false) }
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.85f else 0.28f,
        animationSpec = tween(280),
        label = "ms_glow"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = tween(280),
        label = "ms_border"
    )
    val searchShadowElev by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 4.dp,
        animationSpec = tween(280),
        label = "ms_shadow"
    )

    // Curated quick-search words shown when no suggestions yet
    val quickWords = remember {
        listOf("ephemeral", "serendipity", "eloquent", "resilient", "whimsical",
               "ponder", "aesthetic", "profound", "ethereal", "melancholy")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        // ── Hero / branding section ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(
                        primary.copy(alpha = 0.13f),
                        primary.copy(alpha = 0.05f),
                        Color.Transparent
                    ))
                )
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Animated shimmer accent bar
                Box(
                    Modifier
                        .width(44.dp).height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primary.copy(alpha = 0.9f),
                                    secondary.copy(alpha = 0.85f),
                                    Color.Transparent
                                ),
                                startX = shimmerX * 300f,
                                endX   = (shimmerX + 1f) * 300f
                            )
                        )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Glassmorphic icon bubble
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(iconPulse)
                            .shadow(8.dp, CircleShape,
                                spotColor = primary.copy(alpha = 0.30f),
                                ambientColor = primary.copy(alpha = 0.15f))
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                    primary.copy(alpha = 0.80f)
                                ))
                            )
                            .border(
                                1.5.dp,
                                Brush.sweepGradient(listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    primary.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.55f)
                                )),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📖", fontSize = 22.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "LexiPopup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = onSurface
                        )
                        Text(
                            "Instant word dictionary",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurface.copy(alpha = 0.52f),
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
        }

        // ── Search box + content ─────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Glassmorphic animated search bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation  = searchShadowElev,
                        shape      = RoundedCornerShape(20.dp),
                        spotColor  = primary.copy(alpha = glowAlpha * 0.40f),
                        ambientColor = primary.copy(alpha = glowAlpha * 0.15f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            surface.copy(alpha = 0.88f)
                        else
                            surfVar.copy(alpha = 0.70f)
                    )
                    .border(
                        width = borderWidth,
                        brush = Brush.linearGradient(listOf(
                            primary.copy(alpha = glowAlpha),
                            secondary.copy(alpha = glowAlpha * 0.60f),
                            primary.copy(alpha = glowAlpha * 0.40f)
                        )),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                BasicTextField(
                    value            = query,
                    onValueChange    = onQueryChange,
                    modifier         = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    singleLine       = true,
                    textStyle        = TextStyle(
                        color      = onSurface,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    keyboardOptions  = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions  = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) onSearch(query.trim().split(" ").first())
                    }),
                    decorationBox = { innerField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Search icon with animated tint
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = primary.copy(alpha = if (isFocused) 1f else 0.55f),
                                modifier = Modifier.size(20.dp)
                            )

                            // Input field with placeholder
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        "Search any word…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = onSurface.copy(alpha = 0.38f)
                                    )
                                }
                                innerField()
                            }

                            // Mic button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(primary.copy(alpha = 0.10f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onVoiceSearch() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Mic, "Voice search",
                                    tint = primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // GO button — only visible when query is non-empty
                            AnimatedVisibility(
                                visible = query.isNotBlank(),
                                enter = fadeIn(tween(160)) + scaleIn(tween(160), 0.7f),
                                exit  = fadeOut(tween(120)) + scaleOut(tween(120), 0.7f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(primary)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onSearch(query.trim().split(" ").first()) }
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "GO",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // ── Autocomplete suggestions (appear as user types) ───────────────
            if (suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp).height(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(primary)
                        )
                        Text(
                            "Suggestions",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primary
                        )
                    }
                    suggestions.take(6).forEach { word ->
                        val src = remember { MutableInteractionSource() }
                        val pressed by src.collectIsPressedAsState()
                        val sc by animateFloatAsState(
                            if (pressed) 0.97f else 1f, label = "sug_sc_$word"
                        )
                        Surface(
                            onClick = { onSearch(word) },
                            modifier = Modifier.fillMaxWidth().scale(sc),
                            shape = RoundedCornerShape(14.dp),
                            color = surfVar.copy(alpha = 0.45f),
                            border = BorderStroke(
                                0.6.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                            ),
                            interactionSource = src
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search, null,
                                    tint = primary.copy(alpha = 0.55f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    word,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.NorthWest, null,
                                    tint = onSurface.copy(alpha = 0.30f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Quick-explore words (shown when no active suggestions) ─────────
            if (suggestions.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp).height(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(secondary)
                        )
                        Text(
                            "Quick explore",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = secondary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "tap any word",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurface.copy(alpha = 0.35f)
                        )
                    }

                    // Two-row chip grid
                    val row1 = quickWords.take(5)
                    val row2 = quickWords.drop(5)
                    listOf(row1, row2).forEachIndexed { rowIdx, rowWords ->
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(rowWords) { word ->
                                val src = remember { MutableInteractionSource() }
                                val pressed by src.collectIsPressedAsState()
                                val sc by animateFloatAsState(
                                    if (pressed) 0.90f else 1f, label = "qw_sc_$rowIdx$word"
                                )
                                val chipColors = listOf(
                                    primary.copy(alpha = 0.12f),
                                    secondary.copy(alpha = 0.12f),
                                    tertiary.copy(alpha = 0.12f)
                                )
                                val chipBorderColors = listOf(
                                    primary.copy(alpha = 0.35f),
                                    secondary.copy(alpha = 0.35f),
                                    tertiary.copy(alpha = 0.35f)
                                )
                                val colorIdx = (word.length + rowIdx) % 3
                                Surface(
                                    onClick = { onSearch(word) },
                                    modifier = Modifier.scale(sc),
                                    shape = RoundedCornerShape(50),
                                    color = chipColors[colorIdx],
                                    border = BorderStroke(0.8.dp, chipBorderColors[colorIdx]),
                                    interactionSource = src
                                ) {
                                    Text(
                                        word,
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = onSurface.copy(alpha = 0.82f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Tip card ─────────────────────────────────────────────────
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = tertiary.copy(alpha = 0.08f),
                    border = BorderStroke(0.7.dp, tertiary.copy(alpha = 0.22f))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("💡", fontSize = 16.sp)
                        Text(
                            "Select any word in Moon+ Reader or any app — LexiPopup pops up instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurface.copy(alpha = 0.60f),
                            lineHeight = 17.sp
                        )
                    }
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
                    .height(if (it == 0) 28.dp else 14.dp)
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
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
                    @Composable fun AiCol(emoji: String, label: String, color: Color, text: String?) {
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            color = color.copy(alpha = 0.10f), border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f))) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("$emoji $label", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                                Text(text ?: "No result", style = MaterialTheme.typography.bodySmall,
                                    color = if (text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    AiCol("🤖", "Groq",      Color(0xFFFF9800), hybridAiResult.groqEntry?.shortMeaning)
                    AiCol("📱", "On-Device", Color(0xFFE91E63), hybridAiResult.onDeviceEntry?.shortMeaning)
                }
            }
        }
    }
}

// ── ActionButton (kept for backward compat with any external call sites) ──────

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    GridActionButton(icon = icon, label = label, onClick = onClick)
}

// ── Inline AI chat panel (shown inside popup card when AI button is tapped) ───

@Composable
private fun PopupAiChatContent(
    word: String,
    viewModel: AiChatViewModel,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val primary   = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(Modifier.fillMaxSize()) {

        // ── Mini top bar: back · title · close ────────────────────────────────
        Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back to word",
                        tint = primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "AI Chat",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    if (word.isNotBlank()) {
                        Text(
                            " · $word",
                            style = MaterialTheme.typography.labelMedium,
                            color = onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close popup",
                        tint = onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ── Full AiChatScreen embedded — same UI, all features ─────────────────
        AiChatScreen(
            viewModel       = viewModel,
            onWordSelected  = {},
            isFullscreenMode = false,
            onExitFullscreen = {}
        )
    }
}
