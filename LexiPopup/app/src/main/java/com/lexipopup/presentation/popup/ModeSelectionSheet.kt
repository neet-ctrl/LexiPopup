package com.lexipopup.presentation.popup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexipopup.domain.models.AppMode

/**
 * Floating bottom sheet shown when a word arrives from Moon+ Reader / PROCESS_TEXT.
 * Lets the user pick English or Biology mode for this lookup.
 * Only shown when both modes are enabled; if one is disabled it's skipped automatically.
 */
@Composable
fun ModeSelectionSheet(
    word: String,
    onModeSelected: (AppMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Semi-transparent scrim + card
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss)
        )

        // Sheet card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Look up in…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "\"$word\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeCard(
                        mode = AppMode.ENGLISH,
                        onClick = { onModeSelected(AppMode.ENGLISH) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeCard(
                        mode = AppMode.BIOLOGY,
                        onClick = { onModeSelected(AppMode.BIOLOGY) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: AppMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (cardBg, borderColor, textColor) = when (mode) {
        AppMode.ENGLISH -> Triple(
            Color(0xFFE3F2FD),
            Color(0xFF1565C0),
            Color(0xFF0D47A1)
        )
        AppMode.BIOLOGY -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            Color(0xFF1B5E20)
        )
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(mode.emoji, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (mode) {
                    AppMode.ENGLISH -> "Dictionary"
                    AppMode.BIOLOGY -> "Biology"
                },
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Compact inline mode switcher shown in the popup header.
 * Tapping a pill re-looks up the current word in the selected mode.
 */
@Composable
fun PopupModeSwitcher(
    activeMode: AppMode,
    englishEnabled: Boolean,
    biologyEnabled: Boolean,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!englishEnabled || !biologyEnabled) return // Only show when both are enabled

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AppMode.values().forEach { mode ->
            val isActive = mode == activeMode
            val bgColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = spring(),
                label = "mode_bg_${mode.id}"
            )
            val textColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(),
                label = "mode_text_${mode.id}"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .background(bgColor)
                    .clickable(enabled = !isActive) { onModeChange(mode) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(mode.emoji, fontSize = 11.sp)
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
