package com.lexipopup.presentation.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lexipopup.presentation.dashboard.MainActivity
import com.lexipopup.presentation.theme.LexiPopupTheme
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LexiPopupTheme {
                OnboardingScreen(
                    onRequestOverlay = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    },
                    onDownloadPacks = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            settingsDataStore.markOnboardingDone()
                        }
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            putExtra("navigate_to", "download_packs")
                        })
                        finish()
                    },
                    onFinish = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            settingsDataStore.markOnboardingDone()
                        }
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

data class OnboardingPage(val icon: ImageVector, val title: String, val description: String)

@Composable
fun OnboardingScreen(
    onRequestOverlay: () -> Unit,
    onDownloadPacks: () -> Unit,
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            Icons.Default.AutoStories,
            "Instant Definitions",
            "Long-press any word in Moon+ Reader, tap 'Dictionary', and select LexiPopup. A beautiful popup appears in under 150ms."
        ),
        OnboardingPage(
            Icons.Default.Translate,
            "Hindi + English",
            "Get definitions, Hindi meanings, synonyms, antonyms, examples, and etymology — all in one gorgeous popup."
        ),
        OnboardingPage(
            Icons.Default.Notifications,
            "Quick Search Anywhere",
            "A persistent notification lets you search any word at any time, from any app — without switching screens."
        ),
        OnboardingPage(
            Icons.Default.CloudDownload,
            "Download Full Dictionary",
            "The app comes with 1,000 seed words. Download the full Wiktionary + WordNet + Hindi pack (offline) to unlock 100,000+ definitions."
        )
    )
    var currentPage by remember { mutableIntStateOf(0) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(targetState = currentPage, label = "onboard") { page ->
                val p = pages[page]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(p.icon, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text(p.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (i == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(width = if (i == currentPage) 24.dp else 8.dp, height = 8.dp)
                    ) {}
                }
            }

            Spacer(Modifier.height(32.dp))

            when {
                currentPage < pages.size - 2 -> {
                    Button(onClick = { currentPage++ }, modifier = Modifier.fillMaxWidth()) {
                        Text("Next")
                        Icon(Icons.Default.ArrowForward, null)
                    }
                }
                currentPage == pages.size - 2 -> {
                    Button(onClick = onRequestOverlay, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Layers, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Overlay Permission")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { currentPage++ }, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip for now")
                    }
                }
                else -> {
                    Button(onClick = onDownloadPacks, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download Dictionary Packs")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                        Text("Start with seed words only")
                    }
                }
            }
        }
    }
}
