package com.lexipopup.presentation.flashcards

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexipopup.domain.models.Flashcard
import kotlin.math.abs

@Composable
fun FlashcardsScreen(viewModel: FlashcardsViewModel = hiltViewModel()) {
    val dueCards by viewModel.dueCards.collectAsState()
    val stats by viewModel.stats.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Stats header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatChip("Due", stats.due, Color(0xFFFF9800))
            StatChip("Learning", stats.learning, Color(0xFF2196F3))
            StatChip("Mastered", stats.mastered, Color(0xFF4CAF50))
        }

        Spacer(Modifier.height(16.dp))

        if (dueCards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50))
                    Text("All caught up!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Come back later for new reviews.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            FlashcardReviewer(
                card = dueCards.first(),
                onReview = { quality -> viewModel.reviewCard(dueCards.first().id, quality) }
            )
        }
    }
}

@Composable
fun FlashcardReviewer(card: Flashcard, onReview: (Int) -> Unit) {
    var isFlipped by remember { mutableStateOf(false) }
    var swipeOffsetX by remember { mutableStateOf(0f) }

    val rotationY by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "card_flip"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // The flashcard
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .graphicsLayer {
                    this.rotationY = rotationY
                    cameraDistance = 16f * density
                    translationX = swipeOffsetX
                    alpha = 1f - (abs(swipeOffsetX) / 1000f).coerceIn(0f, 0.8f)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeOffsetX > 200f) onReview(4) // right = easy
                            else if (swipeOffsetX < -200f) onReview(1) // left = hard
                            swipeOffsetX = 0f
                        }
                    ) { _, dragAmount -> swipeOffsetX += dragAmount }
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (rotationY < 90f) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.rotationY = if (rotationY > 90f) 180f else 0f },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        if (rotationY < 90f) card.word else card.frontText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    if (rotationY > 90f) {
                        Text(
                            card.backText,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            "Tap to reveal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Swipe right = Easy  •  Swipe left = Hard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))

        if (!isFlipped) {
            Button(onClick = { isFlipped = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Flip, null)
                Spacer(Modifier.width(8.dp))
                Text("Show Answer")
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

@Composable
fun StatChip(label: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
