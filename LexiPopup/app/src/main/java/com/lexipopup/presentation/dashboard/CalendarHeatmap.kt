package com.lexipopup.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * GitHub-style calendar heatmap showing word lookups per day for the last 12 weeks.
 * Data format: Map<LocalDate, Int> where Int is the lookup count that day.
 */
@Composable
fun CalendarHeatmap(
    data: Map<LocalDate, Int>,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val maxValue = data.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    // Last 12 weeks = 84 days
    val weeks = 12
    val startDate = today.minusDays((weeks * 7 - 1).toLong())

    Column(modifier = modifier) {
        Text("Activity — Last 12 Weeks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // Day-of-week labels
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.width(20.dp)) // label column
            DayOfWeek.values().take(7).forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val cellSize = size.width / (weeks + 1)
            val cellGap = 3f
            val cornerRadius = 3f

            for (week in 0 until weeks) {
                for (dayOfWeek in 0 until 7) {
                    val date = startDate.plusDays((week * 7 + dayOfWeek).toLong())
                    if (date.isAfter(today)) continue

                    val count = data[date] ?: 0
                    val intensity = (count.toFloat() / maxValue).coerceIn(0f, 1f)
                    val cellColor = if (intensity == 0f) surface else primary.copy(alpha = 0.2f + intensity * 0.8f)

                    val x = (week + 1) * cellSize + cellGap
                    val y = dayOfWeek * (size.height / 7) + cellGap

                    drawRoundRect(
                        color = cellColor,
                        topLeft = Offset(x, y),
                        size = Size(cellSize - cellGap * 2, size.height / 7 - cellGap * 2),
                        cornerRadius = CornerRadius(cornerRadius)
                    )
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { intensity ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .padding(1.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = if (intensity == 0f) surface else primary.copy(alpha = 0.2f + intensity * 0.8f),
                            cornerRadius = CornerRadius(2f)
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
