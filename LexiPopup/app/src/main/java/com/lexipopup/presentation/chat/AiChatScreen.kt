package com.lexipopup.presentation.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.Gson
import com.lexipopup.data.local.entities.ChatMessageEntity
import com.lexipopup.data.local.entities.ChatSessionEntity
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.utils.ai.AiProviderType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Colours ──────────────────────────────────────────────────────────────────
private val GradientStart    = Color(0xFF1565C0)
private val GradientEnd      = Color(0xFF0D47A1)
private val UserBubble       = Color(0xFF1E88E5)
private val AiBubbleBg       = Color(0xFFF8F9FF)
private val AiBubbleBgDark   = Color(0xFF1A237E).copy(alpha = 0.14f)
private val ErrorBubble      = Color(0xFFFFEBEE)
private val ErrorText        = Color(0xFFB71C1C)

// ── Main Screen ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel = hiltViewModel(),
    onWordSelected: (String) -> Unit = {},
    isFullscreenMode: Boolean = false,
    onExitFullscreen: () -> Unit = {}
) {
    val sessions         by viewModel.sessions.collectAsState()
    val currentSession   by viewModel.currentSession.collectAsState()
    val messages         by viewModel.messages.collectAsState()
    val isTyping         by viewModel.isTyping.collectAsState()
    val typingText       by viewModel.typingText.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val settings         by viewModel.settings.collectAsState()
    val rateLimitInfo    by viewModel.rateLimitInfo.collectAsState()
    val extractionResult by viewModel.extractionResult.collectAsState()
    val wordLookup       by viewModel.wordLookup.collectAsState()
    // TTS state
    val isSpeaking       by viewModel.isSpeaking.collectAsState()
    val ttsActive        by viewModel.ttsActive.collectAsState()
    val speakingMsgId    by viewModel.speakingMessageId.collectAsState()
    val speakingIndex    by viewModel.speakingIndex.collectAsState()
    val speakTotal       by viewModel.speakTotal.collectAsState()
    val speakSpeed       by viewModel.speakSpeed.collectAsState()

    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()
    val clipboard  = LocalClipboardManager.current
    val haptic     = LocalHapticFeedback.current

    var inputText         by remember { mutableStateOf("") }
    var showSessions      by remember { mutableStateOf(false) }
    var longPressedWord   by remember { mutableStateOf<Pair<String, Long>?>(null) }  // word, messageId
    var isFullscreen      by remember { mutableStateOf(false) }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size, typingText.length) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    // Auto-speak: when a new AI message arrives and the setting is on,
    // speak it immediately without requiring any manual action.
    // We track the last spoken message ID so rapid responses don't double-speak.
    val lastAutoSpokenId = remember { mutableStateOf<Long?>(-1L) }
    LaunchedEffect(messages.size) {
        if (!settings.autoSpeakAiResponse) return@LaunchedEffect
        val latest = messages.lastOrNull { it.role == "assistant" && !it.isError }
            ?: return@LaunchedEffect
        if (latest.id == lastAutoSpokenId.value) return@LaunchedEffect
        // Wait briefly in case the typing animation is still finishing
        kotlinx.coroutines.delay(300)
        lastAutoSpokenId.value = latest.id
        viewModel.autoSpeakMessage(latest)
    }

    // Provider availability flags
    val groqAvail   = settings.groqApiKey.isNotBlank()
    val openAiAvail = settings.openAiApiKey.isNotBlank()

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Provider badge strip ─────────────────────────────────────────────
        ProviderBadgeRow(
            selected       = selectedProvider,
            groqAvail      = groqAvail,
            openAiAvail    = openAiAvail,
            onSelect       = { viewModel.setProvider(it) }
        )

        // ── Rate-limit warning ───────────────────────────────────────────────
        AnimatedVisibility(visible = rateLimitInfo != null) {
            rateLimitInfo?.let { rli ->
                RateLimitBanner(info = rli, onDismiss = viewModel::clearRateLimitInfo)
            }
        }

        // ── Session info bar ─────────────────────────────────────────────────
        Surface(shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Forum,
                        null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = currentSession?.title ?: "New Chat",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (currentSession != null) {
                        Text(
                            "${messages.size} msgs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { viewModel.startNewSession() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, "New chat", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showSessions = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.History, "Chat history", modifier = Modifier.size(18.dp))
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.extractVocabulary() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                "Extract vocabulary",
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFFFF6F00)
                            )
                        }
                        // ── Global read-aloud button ──────────────────────────
                        IconButton(
                            onClick = {
                                if (ttsActive) viewModel.stopSpeaking()
                                else viewModel.speakAllMessages(messages)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (ttsActive) Icons.Default.StopCircle else Icons.Default.RecordVoiceOver,
                                if (ttsActive) "Stop reading" else "Read all aloud",
                                modifier = Modifier.size(18.dp),
                                tint = if (ttsActive) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = if (isFullscreenMode) onExitFullscreen else ({ isFullscreen = true }),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isFullscreenMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            if (isFullscreenMode) "Exit fullscreen" else "Open fullscreen",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ── Message list ─────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty() && typingText.isBlank()) {
                EmptyState(
                    provider = selectedProvider,
                    groqAvail = groqAvail,
                    openAiAvail = openAiAvail
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(
                            message           = msg,
                            isCurrentlySpeaking = msg.id == speakingMsgId,
                            onSpeak           = { viewModel.speakMessage(msg) },
                            onWordLongPress   = { word ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                longPressedWord = word to msg.id
                            },
                            onWordTap = { word -> onWordSelected(word) }
                        )
                    }

                    // Live typing animation bubble
                    if (typingText.isNotBlank()) {
                        item {
                            TypingAnimationBubble(text = typingText)
                        }
                    }

                    // Thinking dots
                    if (isTyping && typingText.isBlank()) {
                        item { ThinkingIndicator() }
                    }

                    item { Spacer(Modifier.height(4.dp)) }
                }
            }

            // FAB extract vocab shortcut (visible when scrolled down)
            val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
            androidx.compose.animation.AnimatedVisibility(
                visible = showFab && messages.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
                enter = fadeIn() + scaleIn(),
                exit  = fadeOut() + scaleOut()
            ) {
                SmallFloatingActionButton(
                    onClick = { viewModel.extractVocabulary() },
                    containerColor = Color(0xFFFF6F00),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.AutoAwesome, "Extract vocab", modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── TTS playback controller ──────────────────────────────────────────
        AnimatedVisibility(
            visible = ttsActive,
            enter = slideInVertically { it } + fadeIn(),
            exit  = slideOutVertically { it } + fadeOut()
        ) {
            val aiMessages = remember(messages) {
                messages.filter { it.role == "assistant" && !it.isError }
            }
            TtsController(
                currentIndex   = speakingIndex,
                total          = speakTotal,
                isSpeaking     = isSpeaking,
                speed          = speakSpeed,
                currentText    = aiMessages.getOrNull(speakingIndex)?.content ?: "",
                onSeek         = { viewModel.seekToIndex(it) },
                onPlayPause    = {
                    if (isSpeaking) viewModel.pauseSpeaking()
                    else viewModel.resumeSpeaking()
                },
                onStop         = { viewModel.stopSpeaking() },
                onSpeedChange  = { viewModel.setSpeakSpeed(it) }
            )
        }

        // ── Input bar ────────────────────────────────────────────────────────
        InputBar(
            value = inputText,
            isTyping = isTyping,
            onValueChange = { inputText = it },
            onSend = {
                val txt = inputText.trim()
                if (txt.isNotBlank()) {
                    viewModel.sendMessage(txt)
                    inputText = ""
                }
            }
        )
    }

    // ── Sessions bottom sheet ────────────────────────────────────────────────
    if (showSessions) {
        SessionsBottomSheet(
            sessions = sessions,
            currentId = currentSession?.id,
            onSelect = { id ->
                viewModel.selectSession(id)
                showSessions = false
            },
            onDelete = { id -> viewModel.deleteSession(id) },
            onNewSession = {
                viewModel.startNewSession()
                showSessions = false
            },
            onDismiss = { showSessions = false }
        )
    }

    // ── Word options bottom sheet ────────────────────────────────────────────
    longPressedWord?.let { (word, messageId) ->
        WordOptionsSheet(
            word       = word,
            messageId  = messageId,
            wordLookup = wordLookup,
            onLookup   = { viewModel.lookupWord(word) },
            onSave     = { entry -> viewModel.saveWordToHistory(entry) },
            onUnderline = { viewModel.toggleUnderline(messageId, word) },
            onCopy     = { clipboard.setText(AnnotatedString(word)) },
            onGoToDetail = { onWordSelected(word) },
            onDismiss  = {
                longPressedWord = null
                viewModel.clearWordLookup()
            }
        )
    }

    // ── Extraction result dialog ─────────────────────────────────────────────
    extractionResult?.let { result ->
        ExtractionResultDialog(
            result = result,
            onWordSelected = { word ->
                onWordSelected(word)
                viewModel.clearExtractionResult()
            },
            onDismiss = viewModel::clearExtractionResult
        )
    }

    // ── Fullscreen dialog ────────────────────────────────────────────────────
    if (isFullscreen && !isFullscreenMode) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AiChatScreen(
                    viewModel = viewModel,
                    onWordSelected = onWordSelected,
                    isFullscreenMode = true,
                    onExitFullscreen = { isFullscreen = false }
                )
            }
        }
    }
}

// ── Provider badge row ────────────────────────────────────────────────────────
@Composable
private fun ProviderBadgeRow(
    selected: AiProviderType,
    groqAvail: Boolean,
    openAiAvail: Boolean,
    onSelect: (AiProviderType) -> Unit
) {
    val providers = listOf(
        AiProviderType.GROQ     to groqAvail,
        AiProviderType.OPENAI   to openAiAvail,
        AiProviderType.ON_DEVICE to true,
        AiProviderType.HYBRID   to (groqAvail || openAiAvail)
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SmartToy, null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            providers.forEach { (type, available) ->
                ProviderChip(
                    label    = type.label,
                    selected = selected == type,
                    available = available,
                    onClick  = { onSelect(type) }
                )
            }
        }
    }
}

@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit
) {
    val bg    = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg    = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = when {
        selected && available   -> Color(0xFF69F0AE)
        available               -> Color(0xFF4CAF50)
        else                    -> Color(0xFF9E9E9E)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = fg)
    }
}

// ── Chat bubble ───────────────────────────────────────────────────────────────
@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    isCurrentlySpeaking: Boolean = false,
    onSpeak: () -> Unit = {},
    onWordLongPress: (String) -> Unit,
    onWordTap: (String) -> Unit
) {
    val isUser = message.role == "user"
    val gson = remember { Gson() }
    val underlined = remember(message.underlinedWords) {
        runCatching {
            gson.fromJson(message.underlinedWords, Array<String>::class.java).toSet()
        }.getOrDefault(emptySet())
    }

    val time = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    // Pulsing glow animation when currently speaking
    val inf = rememberInfiniteTransition(label = "tts_glow_${message.id}")
    val glowAlpha by inf.animateFloat(
        initialValue = 0.25f, targetValue = 0.70f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "tts_glow_alpha"
    )
    val speakBorderColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrentlySpeaking)
                            Brush.verticalGradient(listOf(GradientStart, GradientEnd))
                        else
                            Brush.verticalGradient(listOf(GradientStart, GradientEnd))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentlySpeaking) {
                    Icon(Icons.Default.VolumeUp, null,
                        modifier = Modifier.size(16.dp), tint = Color.White)
                } else {
                    Icon(Icons.Default.AutoAwesome, null,
                        modifier = Modifier.size(16.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            if (isUser) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                        .background(UserBubble)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        message.content,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                val cardBorder = if (isCurrentlySpeaking)
                    androidx.compose.foundation.BorderStroke(1.5.dp, speakBorderColor.copy(alpha = glowAlpha))
                else null

                Card(
                    shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.isError) ErrorBubble
                        else if (isCurrentlySpeaking) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = cardBorder,
                    elevation = CardDefaults.cardElevation(if (isCurrentlySpeaking) 4.dp else 2.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        LongPressableText(
                            text = message.content,
                            underlinedWords = underlined,
                            onWordLongPress = onWordLongPress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.isError) ErrorText else MaterialTheme.colorScheme.onSurface
                        )
                        if (message.tokensUsed > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${message.tokensUsed} tokens · ${message.provider}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
            // Bottom row: timestamp + speak icon (AI only)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(
                        top = 2.dp,
                        start = if (isUser) 0.dp else 4.dp,
                        end = if (isUser) 4.dp else 0.dp
                    )
                )
                if (!isUser) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrentlySpeaking)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { onSpeak() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isCurrentlySpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeUp,
                            contentDescription = "Speak message",
                            tint = if (isCurrentlySpeaking) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Long-pressable text with per-word detection ───────────────────────────────
@Composable
private fun LongPressableText(
    text: String,
    underlinedWords: Set<String>,
    onWordLongPress: (String) -> Unit,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified
) {
    val annotated = remember(text, underlinedWords) {
        buildAnnotatedString {
            val regex = Regex("""[\w''\-]+|[^\w''\-]+""")
            regex.findAll(text).forEach { match ->
                val token = match.value
                val isWord = token[0].isLetterOrDigit() || token[0] == '\''
                if (isWord) {
                    val needsUnderline = underlinedWords.any { it.equals(token, ignoreCase = true) }
                    pushStringAnnotation("W", token)
                    if (needsUnderline) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold, color = Color(0xFF1565C0))) {
                            append(token)
                        }
                    } else {
                        append(token)
                    }
                    pop()
                } else {
                    append(token)
                }
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = style,
        color = color,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(text) {
            detectTapGestures(
                onLongPress = { offset ->
                    layoutResult?.let { layout ->
                        val pos = layout.getOffsetForPosition(offset)
                        val annotations = annotated.getStringAnnotations("W", pos, pos)
                        annotations.firstOrNull()?.item?.let { word ->
                            if (word.length >= 2) onWordLongPress(word)
                        }
                    }
                }
            )
        }
    )
}

// ── Typing animation bubble ───────────────────────────────────────────────────
@Composable
private fun TypingAnimationBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Card(
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── Thinking dots ─────────────────────────────────────────────────────────────
@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotScale1 by infiniteTransition.animateFloat(1f, 1.4f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "d1")
    val dotScale2 by infiniteTransition.animateFloat(1f, 1.4f, infiniteRepeatable(tween(400, delayMillis = 130), RepeatMode.Reverse), label = "d2")
    val dotScale3 by infiniteTransition.animateFloat(1f, 1.4f, infiniteRepeatable(tween(400, delayMillis = 260), RepeatMode.Reverse), label = "d3")

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Card(
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(dotScale1, dotScale2, dotScale3).forEach { scale ->
                    Box(
                        modifier = Modifier
                            .size((8 * scale).dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(0.6f))
                    )
                }
            }
        }
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────
@Composable
private fun InputBar(
    value: String,
    isTyping: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask anything… vocabulary, translation, grammar…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (value.isNotBlank() && !isTyping) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = value.isNotBlank() && !isTyping, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                if (isTyping) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Send, "Send",
                        modifier = Modifier.size(20.dp),
                        tint = if (value.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(provider: AiProviderType, groqAvail: Boolean, openAiAvail: Boolean) {
    val hasKey = when (provider) {
        AiProviderType.GROQ    -> groqAvail
        AiProviderType.OPENAI  -> openAiAvail
        AiProviderType.HYBRID  -> groqAvail || openAiAvail
        AiProviderType.ON_DEVICE -> true
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(40.dp), tint = Color.White)
        }
        Spacer(Modifier.height(20.dp))
        Text("LexiPopup AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask anything — vocabulary, grammar, translation, etymology, or just have a conversation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (!hasKey) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Text("Add a ${provider.label} API key in\nSettings → AI Settings to start chatting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        val suggestions = listOf("What does 'petrichor' mean?", "Translate 'serendipity' to Hindi", "Explain the etymology of 'philosophy'", "What are synonyms for 'melancholy'?")
        suggestions.forEach { suggestion ->
            Spacer(Modifier.height(6.dp))
            SuggestionChip(
                onClick = {},
                label = { Text(suggestion, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(14.dp)) }
            )
        }
    }
}

// ── Rate-limit banner ─────────────────────────────────────────────────────────
@Composable
private fun RateLimitBanner(info: RateLimitInfo, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, modifier = Modifier.size(18.dp), tint = Color(0xFFE65100))
            Text(
                "⚡ ${info.provider} rate limit reached. Wait ${info.retryAfterSeconds}s or switch provider.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBF360C),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Sessions bottom sheet ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsBottomSheet(
    sessions: List<ChatSessionEntity>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chat History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNewSession) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Chat")
                }
            }
            HorizontalDivider()
            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No saved chats yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        val isActive = session.id == currentId
                        ListItem(
                            headlineContent = {
                                Text(session.title, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    "${session.messageCount} messages · ${fmt.format(Date(session.updatedAt))} · ${session.provider}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Forum,
                                    null,
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onDelete(session.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, "Delete session", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(0.7f))
                                }
                            },
                            modifier = Modifier
                                .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) else Color.Transparent)
                                .clickable { onSelect(session.id) }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ── Word options bottom sheet ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordOptionsSheet(
    word: String,
    messageId: Long,
    wordLookup: WordLookupState?,
    onLookup: () -> Unit,
    onSave: (WordEntry) -> Unit,
    onUnderline: () -> Unit,
    onCopy: () -> Unit,
    onGoToDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(word) { onLookup() }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {

            // Word header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    when (val state = wordLookup) {
                        is WordLookupState.Loading -> Text("Looking up…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is WordLookupState.Found -> {
                            Text(state.entry.partOfSpeech, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(state.entry.shortMeaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
                            if (state.entry.hindiMeaning.isNotBlank()) {
                                Text("🇮🇳 ${state.entry.hindiMeaning}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, fontStyle = FontStyle.Italic)
                            }
                        }
                        is WordLookupState.NotFound -> Text("Definition not found. Open full details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        null -> {}
                    }
                }
                if (wordLookup is WordLookupState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            HorizontalDivider()

            // Action list
            val foundEntry = (wordLookup as? WordLookupState.Found)?.entry

            WordOption(Icons.Default.OpenInNew, "Open full dictionary entry", Color(0xFF1565C0)) {
                onGoToDetail(); onDismiss()
            }
            if (foundEntry != null) {
                WordOption(Icons.Default.BookmarkAdd, "Save to word history", Color(0xFF2E7D32)) {
                    onSave(foundEntry); onDismiss()
                }
            }
            WordOption(Icons.Default.ContentCopy, "Copy word", MaterialTheme.colorScheme.onSurface) {
                onCopy(); onDismiss()
            }
            WordOption(Icons.Default.FormatUnderlined, "Toggle permanent underline", Color(0xFF6A1B9A)) {
                onUnderline(); onDismiss()
            }
            if (foundEntry?.hindiMeaning?.isNotBlank() == true) {
                WordOption(Icons.Default.Translate, "Hindi: ${foundEntry.hindiMeaning}", Color(0xFF00838F)) {
                    clipboard.setText(AnnotatedString(foundEntry.hindiMeaning)); onDismiss()
                }
            }
            if (foundEntry?.exampleSentence?.isNotBlank() == true) {
                WordOption(Icons.Default.FormatQuote, "Copy example sentence", Color(0xFF546E7A)) {
                    clipboard.setText(AnnotatedString(foundEntry.exampleSentence)); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun WordOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, iconTint: Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ── Extraction result dialog ───────────────────────────────────────────────────
@Composable
private fun ExtractionResultDialog(
    result: ExtractionResult,
    onWordSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    when (result) {
        is ExtractionResult.Loading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Extracting Vocabulary…") },
                text = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                confirmButton = {}
            )
        }
        is ExtractionResult.Err -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Extraction Failed") },
                text = { Text(result.message) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
            )
        }
        is ExtractionResult.Done -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFF6F00))
                        Text("Vocabulary Extracted!")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StatPill("${result.total}", "found", Color(0xFF1565C0))
                            StatPill("${result.saved}", "saved", Color(0xFF2E7D32))
                            if (result.skipped > 0) StatPill("${result.skipped}", "skipped\n(already in history)", Color(0xFF9E9E9E))
                        }
                        HorizontalDivider()
                        if (result.words.isNotEmpty()) {
                            Text("Tap a word to see its full entry:", style = MaterialTheme.typography.labelMedium)
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(result.words) { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { onWordSelected(entry.word) }.padding(vertical = 5.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.word, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(entry.shortMeaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (entry.partOfSpeech.isNotBlank()) {
                                            Text(entry.partOfSpeech, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
            )
        }
    }
}

@Composable
private fun StatPill(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── TTS playback controller bar ───────────────────────────────────────────────

@Composable
private fun TtsController(
    currentIndex: Int,
    total: Int,
    isSpeaking: Boolean,
    speed: Float,
    currentText: String,
    onSeek: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    var showMore by remember { mutableStateOf(false) }
    val primary   = MaterialTheme.colorScheme.primary
    val surface   = MaterialTheme.colorScheme.surfaceVariant
    val onSurf    = MaterialTheme.colorScheme.onSurfaceVariant

    // Pulsing dot when speaking
    val inf = rememberInfiniteTransition(label = "tts_dot")
    val dotAlpha by inf.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "tts_dot_alpha"
    )

    Surface(
        color = surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Current message preview ────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isSpeaking) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = dotAlpha))
                    )
                }
                Text(
                    text = currentText.take(72).let { if (currentText.length > 72) "$it…" else it },
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurf,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${currentIndex + 1} / $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Progress slider ────────────────────────────────────────────────
            val sliderVal = if (total > 0) currentIndex.toFloat() / total.toFloat() else 0f
            Slider(
                value = sliderVal,
                onValueChange = { v ->
                    val target = (v * total).toInt().coerceIn(0, (total - 1).coerceAtLeast(0))
                    onSeek(target)
                },
                colors = SliderDefaults.colors(
                    thumbColor = primary,
                    activeTrackColor = primary,
                    inactiveTrackColor = primary.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )

            // ── Controls row ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Prev
                IconButton(
                    onClick = { onSeek((currentIndex - 1).coerceAtLeast(0)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, "Previous message",
                        modifier = Modifier.size(20.dp), tint = onSurf)
                }

                // Play / Pause
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = primary)
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isSpeaking) "Pause" else "Resume",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }

                // Stop
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.StopCircle, "Stop",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.80f))
                }

                // Next
                IconButton(
                    onClick = { onSeek((currentIndex + 1).coerceAtMost((total - 1).coerceAtLeast(0))) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.SkipNext, "Next message",
                        modifier = Modifier.size(20.dp), tint = onSurf)
                }

                // More
                IconButton(
                    onClick = { showMore = !showMore },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.MoreVert, "More options",
                        modifier = Modifier.size(20.dp),
                        tint = if (showMore) primary else onSurf)
                }
            }

            // ── More options: speed control ────────────────────────────────────
            AnimatedVisibility(visible = showMore) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Speed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurf
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "${String.format("%.1f", speed)}×",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Slider(
                        value = speed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = primary,
                            activeTrackColor = primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { sp ->
                            val isSelected = kotlin.math.abs(speed - sp) < 0.05f
                            Surface(
                                onClick = { onSpeedChange(sp) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) primary else primary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    "${sp}×",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) Color.White else primary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
