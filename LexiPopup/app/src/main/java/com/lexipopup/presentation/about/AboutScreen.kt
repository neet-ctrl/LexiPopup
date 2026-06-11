package com.lexipopup.presentation.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About & Licenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App header
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("LexiPopup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("The most advanced offline popup dictionary for Android readers.", style = MaterialTheme.typography.bodyMedium)
                    Text("Version 1.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Data sources — LEGALLY REQUIRED attributions
            Text("Data Sources & Licenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            LicenseCard(
                source = "Wiktionary",
                description = "4.7 million+ word definitions, IPA pronunciations, etymologies, and example sentences.",
                license = "Creative Commons CC BY-SA 3.0",
                url = "https://en.wiktionary.org/wiki/Wiktionary:Copyrights",
                accentColor = Color(0xFF4CAF50)
            ) { uriHandler.openUri("https://en.wiktionary.org") }

            LicenseCard(
                source = "WordNet 3.1",
                description = "Princeton University's lexical database: synonyms, antonyms, hypernyms, hyponyms for 155,000+ words.",
                license = "WordNet 3.1 License (free for all use)",
                url = "https://wordnet.princeton.edu/license-and-commercial-use",
                accentColor = Color(0xFF2196F3)
            ) { uriHandler.openUri("https://wordnet.princeton.edu") }

            LicenseCard(
                source = "Hindi WordNet",
                description = "Developed at Center for Indian Language Technology (CFILT), IIT Bombay, under supervision of Prof. Pushpak Bhattacharyya. Copyright © 2006, IIT Bombay.",
                license = "GNU Free Documentation License (FDL) — Non-commercial research use",
                url = "http://www.cfilt.iitb.ac.in/hindiwordnet/",
                accentColor = Color(0xFFFF9800)
            ) { uriHandler.openUri("http://www.cfilt.iitb.ac.in/hindiwordnet/") }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(
                        "Hindi WordNet is used for non-commercial research purposes. If you wish to use LexiPopup commercially, a separate licensing agreement with IIT Bombay is required.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider()

            // Open source libraries
            Text("Open Source Libraries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val libs = listOf(
                "Jetpack Compose" to "Apache 2.0",
                "Room Database" to "Apache 2.0",
                "Hilt (Dependency Injection)" to "Apache 2.0",
                "Retrofit / OkHttp" to "Apache 2.0",
                "Kotlin Coroutines" to "Apache 2.0",
                "DataStore Preferences" to "Apache 2.0",
                "Material 3" to "Apache 2.0"
            )

            libs.forEach { (lib, license) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(lib, style = MaterialTheme.typography.bodySmall)
                    Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            // App policy
            Text("Privacy Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "LexiPopup does NOT collect, transmit, or store any personal data externally. All dictionary lookups happen on your device. Search history is stored locally in your device's database and can be cleared at any time from the Vocabulary tab. No analytics or advertising SDKs are included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun LicenseCard(
    source: String,
    description: String,
    license: String,
    url: String,
    accentColor: Color,
    onVisit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = accentColor,
                    modifier = Modifier.size(10.dp)
                ) {}
                Text(source, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(license, style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Medium)
                TextButton(onClick = onVisit) {
                    Text("Visit", style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}
