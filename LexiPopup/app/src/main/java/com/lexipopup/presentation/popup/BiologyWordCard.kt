package com.lexipopup.presentation.popup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.BiologyData
import com.lexipopup.domain.models.WordEntry

// ── Colors for Biology mode ──────────────────────────────────────────────────

private val BioGreen = Color(0xFF2E7D32)
private val BioGreenLight = Color(0xFFE8F5E9)
private val BioTeal = Color(0xFF00838F)
private val BioTealLight = Color(0xFFE0F7FA)
private val BioPurple = Color(0xFF6A1B9A)
private val BioPurpleLight = Color(0xFFF3E5F5)
private val BioRed = Color(0xFFB71C1C)
private val BioRedLight = Color(0xFFFFEBEE)
private val BioBlue = Color(0xFF1565C0)
private val BioBlueLight = Color(0xFFE3F2FD)
private val BioAmber = Color(0xFFF57F17)
private val BioAmberLight = Color(0xFFFFF8E1)

/**
 * Full biology term card — shown in the popup when mode = BIOLOGY.
 * Displays classification, definition, functions, structure, related terms, diseases.
 */
@Composable
fun BiologyWordCard(
    entry: WordEntry,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val bioData = remember(entry.bioExtData) { entry.biologyData() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── Category badge + pronunciation ────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (settings.bioShowCategory && entry.partOfSpeech.isNotBlank()) {
                BioCategoryBadge(entry.partOfSpeech)
            }
            if (settings.bioShowPronunciation && entry.pronunciation.isNotBlank()) {
                Text(
                    text = entry.pronunciation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }

        // ── Definition ────────────────────────────────────────────────────────
        if (settings.bioShowDefinition && entry.shortMeaning.isNotBlank()) {
            BioSection(
                icon = "📖",
                label = "Definition",
                color = BioBlue,
                bgColor = BioBlueLight
            ) {
                Text(
                    text = entry.shortMeaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ── Hindi name ────────────────────────────────────────────────────────
        if (settings.bioShowHindi && entry.hindiMeaning.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🇮🇳", fontSize = 14.sp)
                Text(
                    text = entry.hindiMeaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Example context ───────────────────────────────────────────────────
        if (settings.bioShowExample && entry.exampleSentence.isNotBlank()) {
            Text(
                text = "\"${entry.exampleSentence}\"",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // ── Scientific classification ─────────────────────────────────────────
        if (settings.bioShowClassification && bioData.scientificClassification.isNotEmpty()) {
            BioExpandableSection(
                icon = "🔬",
                label = "Scientific Classification",
                color = BioPurple,
                bgColor = BioPurpleLight,
                initiallyExpanded = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    bioData.scientificClassification.entries.forEach { (rank, value) ->
                        if (value.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "$rank:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BioPurple,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(60.dp)
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Functions ─────────────────────────────────────────────────────────
        if (settings.bioShowFunctions && bioData.functions.isNotEmpty()) {
            BioSection(icon = "⚙️", label = "Functions", color = BioTeal, bgColor = BioTealLight) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    bioData.functions.forEach { fn ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", color = BioTeal, fontWeight = FontWeight.Bold)
                            Text(fn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // ── Structure ─────────────────────────────────────────────────────────
        if (settings.bioShowStructure && bioData.structure.isNotEmpty()) {
            BioSection(icon = "🏗️", label = "Structure", color = BioAmber, bgColor = BioAmberLight) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bioData.structure) { part ->
                        BioChip(text = part, color = BioAmber, bgColor = BioAmberLight)
                    }
                }
            }
        }

        // ── Related terms ─────────────────────────────────────────────────────
        if (settings.bioShowRelatedTerms && bioData.relatedTerms.isNotEmpty()) {
            BioSection(icon = "🔗", label = "Related Terms", color = BioGreen, bgColor = BioGreenLight) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bioData.relatedTerms) { term ->
                        BioChip(text = term, color = BioGreen, bgColor = BioGreenLight)
                    }
                }
            }
        }

        // ── Diseases / Disorders ──────────────────────────────────────────────
        if (settings.bioShowDiseases && bioData.diseases.isNotEmpty()) {
            BioSection(icon = "🏥", label = "Associated Diseases", color = BioRed, bgColor = BioRedLight) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bioData.diseases) { disease ->
                        BioChip(text = disease, color = BioRed, bgColor = BioRedLight)
                    }
                }
            }
        }

        // ── Etymology ─────────────────────────────────────────────────────────
        if (settings.bioShowEtymology && entry.etymology.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📜", fontSize = 13.sp)
                Text(
                    text = "Origin: ${entry.etymology}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }

        // ── Difficulty + Frequency row ────────────────────────────────────────
        if (settings.bioShowDifficulty || settings.bioShowFrequency) {
            val bioData2 = bioData
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (settings.bioShowDifficulty && bioData2.difficultyLabel.isNotBlank()) {
                    val diffColor = when (bioData2.difficultyLabel.lowercase()) {
                        "basic" -> Color(0xFF2E7D32)
                        "intermediate" -> Color(0xFFE65100)
                        "advanced" -> Color(0xFFB71C1C)
                        else -> Color(0xFF546E7A)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(diffColor.copy(alpha = 0.12f))
                            .border(1.dp, diffColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = bioData2.difficultyLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = diffColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (settings.bioShowFrequency && bioData2.frequencyPercent > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📊", fontSize = 11.sp)
                        Text(
                            text = "${bioData2.frequencyPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "common",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
fun BioCategoryBadge(category: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (category.lowercase()) {
        "organelle" -> BioTealLight to BioTeal
        "hormone" -> BioPurpleLight to BioPurple
        "organ" -> BioBlueLight to BioBlue
        "process" -> BioAmberLight to BioAmber
        "disease" -> BioRedLight to BioRed
        "tissue" -> Color(0xFFF1F8E9) to Color(0xFF558B2F)
        "molecule" -> Color(0xFFEDE7F6) to Color(0xFF4527A0)
        "cell" -> Color(0xFFE0F2F1) to Color(0xFF00695C)
        "system" -> Color(0xFFECEFF1) to Color(0xFF37474F)
        else -> BioGreenLight to BioGreen
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "🧬 $category",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BioSection(
    icon: String,
    label: String,
    color: Color,
    bgColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, fontSize = 13.sp)
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun BioExpandableSection(
    icon: String,
    label: String,
    color: Color,
    bgColor: Color,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { expanded = !expanded }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(icon, fontSize = 13.sp)
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(content = content)
        }
    }
}

@Composable
fun BioChip(text: String, color: Color, bgColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}
