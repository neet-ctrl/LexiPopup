package com.lexipopup.presentation.permissions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class PermissionStates(
    val hasOverlay: Boolean,
    val hasNotifications: Boolean,
    val hasMicrophone: Boolean,
    val hasStorageRead: Boolean,
    val isBatteryOptimizationIgnored: Boolean
)

private data class PermInfo(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val reason: String,
    val isRequired: Boolean,
    val isGranted: (PermissionStates) -> Boolean,
    val actionLabel: String,
    val onAction: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupScreen(
    permissionStates: PermissionStates,
    onRequestNotification: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestStorageRead: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onContinue: () -> Unit
) {
    val allCriticalGranted = permissionStates.hasOverlay && permissionStates.hasNotifications
    var animVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        animVisible = true
    }

    val perms = remember(permissionStates) {
        listOf(
            PermInfo(
                icon = Icons.Default.Layers,
                iconTint = Color(0xFF6C63FF),
                title = "Display over other apps",
                reason = "Required — shows the dictionary popup when you select text in Moon+ Reader or any other app. Without this the popup cannot appear.",
                isRequired = true,
                isGranted = { it.hasOverlay },
                actionLabel = "Open Settings",
                onAction = onRequestOverlay
            ),
            PermInfo(
                icon = Icons.Default.Notifications,
                iconTint = Color(0xFFFF6B35),
                title = "Send notifications",
                reason = "Required on Android 13+ — keeps the background service alive so the popup responds instantly. The service notification is silent and uses minimal battery.",
                isRequired = true,
                isGranted = { it.hasNotifications },
                actionLabel = "Allow",
                onAction = onRequestNotification
            ),
            PermInfo(
                icon = Icons.Default.BatteryFull,
                iconTint = Color(0xFF00BCD4),
                title = "Disable battery optimisation",
                reason = "Recommended — prevents aggressive OEMs (Xiaomi, OPPO, Samsung, Huawei) from killing the popup service in the background. Without this the popup may stop responding after the screen turns off.",
                isRequired = false,
                isGranted = { it.isBatteryOptimizationIgnored },
                actionLabel = "Disable",
                onAction = onRequestBatteryOptimization
            ),
            PermInfo(
                icon = Icons.Default.Mic,
                iconTint = Color(0xFF4CAF50),
                title = "Microphone",
                reason = "Optional — lets you search words by speaking them. Tap the mic icon anywhere in the app to use voice search.",
                isRequired = false,
                isGranted = { it.hasMicrophone },
                actionLabel = "Allow",
                onAction = onRequestMicrophone
            ),
            PermInfo(
                icon = Icons.Default.Folder,
                iconTint = Color(0xFFFFA726),
                title = "Read storage (Android 12 and below)",
                reason = "Optional — needed to import backup files on Android 12 and below. Not required on Android 13+.",
                isRequired = false,
                isGranted = { it.hasStorageRead },
                actionLabel = "Allow",
                onAction = onRequestStorageRead
            )
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                // Hero header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Security,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Text(
                            "App Permissions",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "LexiPopup needs a few permissions to work properly.\nRequired permissions are needed for the popup to function.\nOptional ones unlock extra features.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                Text(
                    "Required",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(perms.filter { it.isRequired }) { index, perm ->
                AnimatedVisibility(
                    visible = animVisible,
                    enter = fadeIn() + slideInVertically(
                        animationSpec = spring(),
                        initialOffsetY = { it / 2 + index * 60 }
                    )
                ) {
                    PermissionCard(
                        perm = perm,
                        states = permissionStates,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Optional",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(perms.filter { !it.isRequired }) { index, perm ->
                AnimatedVisibility(
                    visible = animVisible,
                    enter = fadeIn() + slideInVertically(
                        animationSpec = spring(),
                        initialOffsetY = { it / 2 + (index + 2) * 60 }
                    )
                ) {
                    PermissionCard(
                        perm = perm,
                        states = permissionStates,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Sticky bottom CTA
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!allCriticalGranted) {
                        Text(
                            "⚠️ Grant the required permissions above to enable the popup dictionary.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = allCriticalGranted,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Using LexiPopup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    if (!allCriticalGranted) {
                        TextButton(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Skip for now (popup won't work)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    perm: PermInfo,
    states: PermissionStates,
    modifier: Modifier = Modifier
) {
    val granted = perm.isGranted(states)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (granted) 0.dp else 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon bubble
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = perm.iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(perm.icon, null, tint = perm.iconTint, modifier = Modifier.size(26.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(perm.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = when {
                            granted       -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                            perm.isRequired -> MaterialTheme.colorScheme.errorContainer
                            else          -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            when {
                                granted       -> "✓ Granted"
                                perm.isRequired -> "Required"
                                else          -> "Optional"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                granted       -> Color(0xFF2E7D32)
                                perm.isRequired -> MaterialTheme.colorScheme.error
                                else          -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    perm.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!granted) {
                    Spacer(Modifier.height(2.dp))
                    FilledTonalButton(
                        onClick = perm.onAction,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(perm.actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
