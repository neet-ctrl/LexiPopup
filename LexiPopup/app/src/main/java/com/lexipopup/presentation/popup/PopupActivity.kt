package com.lexipopup.presentation.popup

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import com.lexipopup.presentation.theme.LexiPopupTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PopupActivity : ComponentActivity() {

    private val viewModel: PopupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = 22 }
        }

        processIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()
            LexiPopupTheme(
                darkTheme = if (settings.useSystemTheme) isSystemDark() else settings.useDarkMode
            ) {
                PopupScreen(
                    viewModel = viewModel,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        val mode = intent.getStringExtra("mode")
        if (mode == "manual_search") {
            viewModel.setManualSearchMode()
            return
        }

        // Direct word lookup from widgets — uses lookup_word extra to survive Glance PendingIntent
        // MIME stripping. Widget intents may carry a "mode" extra ("english" or "biology") to
        // bypass the mode selection sheet and go straight to the right mode.
        val directWord = intent.getStringExtra("lookup_word")
        if (!directWord.isNullOrBlank()) {
            val widgetMode = intent.getStringExtra("lookup_mode")
            if (!widgetMode.isNullOrBlank()) {
                val appMode = com.lexipopup.domain.models.AppMode.fromId(widgetMode)
                viewModel.lookupWord(directWord.trim(), mode = appMode)
            } else {
                viewModel.lookupWord(directWord.trim())
            }
            return
        }

        // PROCESS_TEXT from Moon+ Reader / any text-selection source
        // Show mode selection sheet so the user can choose English or Biology lookup
        val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (!processText.isNullOrBlank()) {
            viewModel.requestModeSelection(processText.trim())
            return
        }

        // SEND intent (shared text)
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                viewModel.requestModeSelection(text.trim().split(" ").first())
                return
            }
        }

        // Deep link: lexipopup://lookup?word=ephemeral
        intent.data?.let { uri ->
            val word = uri.getQueryParameter("word")
            if (!word.isNullOrBlank()) {
                viewModel.lookupWord(word)
                return
            }
        }

        // Fallback to manual search
        viewModel.setManualSearchMode()
    }
}

@Composable
fun isSystemDark(): Boolean {
    val config = LocalConfiguration.current
    return config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
