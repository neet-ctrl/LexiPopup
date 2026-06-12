package com.lexipopup.presentation.flashcards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.Flashcard
import com.lexipopup.utils.ModeStrings
import kotlin.math.abs

private val BioGreen     = Color(0xFF2E7D32)
private val BioGreenLight = Color(0xFFE8F5E9)

@Composable
fun FlashcardsScreen(
    viewModel: FlashcardsViewModel = hiltViewModel(),
    historyCount: Int = 0,
    onNavigateToHistory: () -> Unit = {}
) {
    val activeMode   by viewModel.activeMode.collectAsState()
    val dueCards     by viewModel.dueCards.collectAsState()
    val stats        by viewModel.stats.collectAsState()
    val appSettings  by viewModel.appSettings.collectAsState()
    val isBiology    = activeMode == AppMode.BIOLOGY

    var showCardSettings by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // ── Biology mode deck indicator ─────────────────────────────────────
        if (isBiology) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BioGreenLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🧬", style = MaterialTheme.typography.titleMedium)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Biology Term Deck",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = BioGreen
                        )
                        Text(
                            "Cards are built from your biology term lookups",
                            style = MaterialTheme.typography.bodySmall,
                            color = BioGreen.copy(alpha = 0.75f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BioGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "AUTO",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BioGreen
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Word History entry card ─────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToHistory),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ModeStrings.historyTitle(activeMode),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        if (historyCount > 0) ModeStrings.historySubtitle(activeMode, historyCount)
                        else if (isBiology) "All biology terms you've ever looked up"
                        else "All words you've ever looked up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Stats row ───────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatChip("Due",      stats.due,      Color(0xFFFF9800))
            StatChip("Learning", stats.learning, Color(0xFF2196F3))
            StatChip("Mastered", stats.mastered, Color(0xFF4CAF50))
        }

        // ── Biology card display settings ──────────────────────────────────
        if (isBiology) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCardSettings = !showCardSettings }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Tune, null, tint = BioGreen, modifier = Modifier.size(20.dp))
                        Text(
                            "Card Display Settings",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (showCardSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = showCardSettings) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            BioCardToggleRow(
                                label = "Show category on front  (e.g. Mitochondria [Organelle])",
                                checked = appSettings.bioCardShowCategory,
                                onCheckedChange = { viewModel.toggleBioCardSetting("category", it) }
                            )
                            BioCardToggleRow(
                                label = "Include example sentence on back",
                                checked = appSettings.bioCardShowExample,
                                onCheckedChange = { viewModel.toggleBioCardSetting("example", it) }
                            )
                            BioCardToggleRow(
                                label = "Include primary function on back",
                                checked = appSettings.bioCardShowFunction,
                                onCheckedChange = { viewModel.toggleBioCardSetting("function", it) }
                            )
                            BioCardToggleRow(
                                label = "Include Hindi meaning on back",
                                checked = appSettings.bioCardShowHindi,
                                onCheckedChange = { viewModel.toggleBioCardSetting("hindi", it) }
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "⚡ Changes apply to newly looked-up terms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Flashcard reviewer or empty state ──────────────────────────────
        if (dueCards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (isBiology) "🧬" else "✅",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        if (isBiology) "Biology deck all caught up!" else "All caught up!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (isBiology)
                            "Look up more biology terms to add new cards automatically."
                        else
                            "Come back later for new reviews.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val currentCard = dueCards.first()
            FlashcardReviewer(
                card        = currentCard,
                isBiology   = isBiology,
                onReview    = { quality -> viewModel.reviewCard(currentCard.id, quality) },
                onSkip      = { viewModel.skipCard(currentCard.id) },
                onDelete    = { showDeleteDialog = true }
            )
        }
    }

    // ── Delete confirmation dialog ──────────────────────────────────────────
    if (showDeleteDialog && dueCards.isNotEmpty()) {
        val cardToDelete = dueCards.first()
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Flashcard?") },
            text  = {
                Text(
                    "\"${cardToDelete.word}\" will be removed from your " +
                    (if (isBiology) "biology" else "vocabulary") +
                    " deck. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCard(cardToDelete.id)
                    showDeleteDialog = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun BioCardToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(24.dp),
            colors = SwitchDefaults.colors(checkedThumbColor = BioGreen, checkedTrackColor = BioGreen.copy(alpha = 0.4f))
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────

@Composable
fun FlashcardReviewer(
    card: Flashcard,
    isBiology: Boolean = false,
    onReview: (Int) -> Unit,
    onSkip: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var isFlipped     by remember(card.id) { mutableStateOf(false) }
    var swipeOffsetX  by remember(card.id) { mutableStateOf(0f) }
    var swipeOffsetY  by remember(card.id) { mutableStateOf(0f) }

    val rotationY by animateFloatAsState(
        targetValue  = if (isFlipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label        = "card_flip"
    )

    // accent colour based on mode & flip state
    val frontColor = if (isBiology) BioGreenLight else MaterialTheme.colorScheme.primaryContainer
    val backColor  = if (isBiology)
        Color(0xFFE3F2FD)   // light blue tint for biology answer side
    else
        MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ── Card ────────────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .graphicsLayer {
                    this.rotationY  = rotationY
                    cameraDistance  = 16f * density
                    translationX    = swipeOffsetX
                    translationY    = swipeOffsetY.coerceIn(-160f, 0f)
                    alpha = 1f - (
                        abs(swipeOffsetX) / 1000f +
                        (-swipeOffsetY.coerceIn(-500f, 0f)) / 1500f
                    ).coerceIn(0f, 0.8f)
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            when {
                                swipeOffsetX > 200f  -> { onReview(4); isFlipped = false }
                                swipeOffsetX < -200f -> { onReview(1); isFlipped = false }
                                swipeOffsetY < -200f -> { onSkip();    isFlipped = false }
                            }
                            swipeOffsetX = 0f; swipeOffsetY = 0f
                        }
                    ) { _, dragAmount ->
                        swipeOffsetX += dragAmount.x
                        swipeOffsetY += dragAmount.y
                    }
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (rotationY < 90f) frontColor else backColor
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.rotationY = if (rotationY > 90f) 180f else 0f },
                contentAlignment = Alignment.Center
            ) {
                // ── FRONT ──
                if (rotationY < 90f) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (isBiology) {
                            Surface(
                                shape  = RoundedCornerShape(8.dp),
                                color  = BioGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "🧬 Biology Term",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BioGreen
                                )
                            }
                        }
                        Text(
                            card.word,
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign  = TextAlign.Center,
                            color      = if (isBiology) BioGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Tap to reveal answer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    // ── BACK ──
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // frontText = the "title" shown on the answer side (term + category)
                        Text(
                            card.frontText,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                            color      = if (isBiology) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                        Text(
                            card.backText,
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color     = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Swipe right = Easy  •  Swipe left = Hard  •  Swipe up = Skip",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // ── Action buttons ──────────────────────────────────────────────────
        if (!isFlipped) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick  = { isFlipped = true },
                    modifier = Modifier.weight(1f),
                    colors   = if (isBiology)
                        ButtonDefaults.buttonColors(containerColor = BioGreen)
                    else
                        ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Default.Flip, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Show Answer")
                }
                // Delete button (outline icon)
                OutlinedButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove card", modifier = Modifier.size(20.dp))
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onReview(1); isFlipped = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) { Text("Hard", color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { onReview(3); isFlipped = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) { Text("Good") }
                Button(
                    onClick = { onReview(5); isFlipped = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("Easy", color = Color.White) }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────

@Composable
fun StatChip(label: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
