package com.lexipopup.presentation.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexipopup.domain.models.AppMode

/**
 * Pill tab switcher shown at the top-centre of the Dashboard home tab when both modes are enabled.
 *
 *  ┌─────────────────────────────────────┐
 *  │  [ 📚 English ]   [ 🧬 Biology ]   │
 *  └─────────────────────────────────────┘
 *
 * Active pill: filled primary colour.
 * Inactive pill: transparent.
 */
@Composable
fun DashboardModeSwitcher(
    activeMode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppMode.values().forEach { mode ->
            val isActive = mode == activeMode
            val bg by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = spring(),
                label = "dash_mode_bg_${mode.id}"
            )
            val textColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(),
                label = "dash_mode_text_${mode.id}"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable(enabled = !isActive) { onModeChange(mode) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(mode.emoji, fontSize = 14.sp)
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }
    }
}
