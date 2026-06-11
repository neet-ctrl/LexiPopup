package com.lexipopup.presentation.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lexipopup.data.download.DatabasePack

@Composable
fun DatabasePackScreen(
    downloadProgress: Int,
    isDownloading: Boolean,
    onSelectPack: (DatabasePack) -> Unit,
    onSkip: () -> Unit
) {
    var selectedPack by remember { mutableStateOf(DatabasePack.STANDARD) }

    val progressAnim by animateFloatAsState(
        targetValue = downloadProgress / 100f,
        animationSpec = tween(300),
        label = "dl_progress"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Choose Dictionary Pack",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Download once, works 100% offline. All sources are free & open (Wiktionary CC BY-SA, WordNet, Hindi WordNet IIT Bombay GNU FDL).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DatabasePack.entries.forEach { pack ->
            PackCard(
                pack = pack,
                selected = selectedPack == pack,
                onClick = { if (!isDownloading) selectedPack = pack }
            )
        }

        Spacer(Modifier.weight(1f))

        if (isDownloading) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Downloading ${selectedPack.displayName} pack…", style = MaterialTheme.typography.bodyMedium)
                    Text("$downloadProgress%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { progressAnim },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Verifying checksum after download…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Button(
                onClick = { onSelectPack(selectedPack) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Download ${selectedPack.displayName} (${selectedPack.sizeMb}MB)")
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Skip — use bundled 1000 words only")
            }
        }

        // Legal attribution required by data sources
        Text(
            "Data: Wiktionary (CC BY-SA 3.0) · WordNet (Princeton, free) · Hindi WordNet (CFILT, IIT Bombay, GNU FDL — non-commercial)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun PackCard(pack: DatabasePack, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) CardDefaults.outlinedCardBorder() else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pack.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val chipColor = when (pack) {
                        DatabasePack.MINIMAL -> Color(0xFF4CAF50)
                        DatabasePack.STANDARD -> Color(0xFF2196F3)
                        DatabasePack.FULL -> Color(0xFF9C27B0)
                    }
                    Surface(shape = RoundedCornerShape(50), color = chipColor.copy(alpha = 0.15f)) {
                        Text(
                            pack.wordCount,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = chipColor
                        )
                    }
                }
                Text(pack.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${pack.sizeMb}MB", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
