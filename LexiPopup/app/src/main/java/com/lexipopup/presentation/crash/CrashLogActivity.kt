package com.lexipopup.presentation.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexipopup.presentation.dashboard.MainActivity
import com.lexipopup.presentation.theme.LexiPopupTheme
import com.lexipopup.utils.CrashHandler

class CrashLogActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REPORT    = "crash_report"
        const val EXTRA_FILE_PATH = "crash_file_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report   = intent.getStringExtra(EXTRA_REPORT)    ?: CrashHandler.lastReport(this) ?: "No crash report found."
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""

        setContent {
            LexiPopupTheme {
                CrashLogScreen(
                    report   = report,
                    filePath = filePath,
                    onRestart = {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                        )
                        finish()
                    },
                    onClose = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(
    report: String,
    filePath: String,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var copied  by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(copied) {
        if (copied) {
            snackbarHostState.showSnackbar("Crash log copied to clipboard")
            copied = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0D0D0D),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFF3B30).copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = Color(0xFFFF3B30),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "App Crashed",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                "Full crash log below",
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1C1E)),
                actions = {
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("LexiPopup Crash Log", report))
                        copied = true
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy log", tint = Color(0xFF0A84FF))
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "LexiPopup Crash Report")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share crash log"))
                    }) {
                        Icon(Icons.Default.Share, "Share log", tint = Color(0xFF30D158))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Red error banner ─────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFF3B30).copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "LexiPopup encountered an unexpected error and had to stop. " +
                        "Copy or share this log and send it to the developer to help fix the issue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6B60),
                        lineHeight = 18.sp
                    )
                }
            }

            // ── Crash log scroll area ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(Color(0xFF1C1C1E), RoundedCornerShape(12.dp))
            ) {
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()

                Text(
                    text = report,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFE5E5EA),
                    lineHeight = 17.sp
                )
            }

            // ── Action buttons ───────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1C1C1E),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Big copy button for easy access
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("LexiPopup Crash Log", report))
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Full Crash Log", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8E8E93))
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Close")
                        }
                        Button(
                            onClick = onRestart,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158))
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restart App", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (filePath.isNotBlank()) {
                        Text(
                            "Saved: $filePath",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF48484A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
