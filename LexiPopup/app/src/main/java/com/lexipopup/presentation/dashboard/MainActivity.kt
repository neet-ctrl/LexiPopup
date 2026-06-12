package com.lexipopup.presentation.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lexipopup.presentation.permissions.PermissionSetupScreen
import com.lexipopup.presentation.permissions.PermissionStates
import com.lexipopup.presentation.popup.PopupService
import com.lexipopup.presentation.theme.LexiPopupTheme
import com.lexipopup.utils.NotificationHelper
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var settingsDataStore: SettingsDataStore

    // ── Reactive permission states — backed by mutableStateOf so Compose recomposes ──
    private var hasOverlay          by mutableStateOf(false)
    private var hasNotificationPerm by mutableStateOf(false)
    private var hasMicrophone       by mutableStateOf(false)
    private var hasStorageRead      by mutableStateOf(false)
    private var startWord           by mutableStateOf<String?>(null)

    /**
     * true  → show PermissionSetupScreen (some critical permission is missing)
     * false → show DashboardScreen (all critical permissions granted or user skipped)
     */
    private var showPermissionSetup by mutableStateOf(false)

    // ── Runtime permission launchers ──────────────────────────────────────────

    /** POST_NOTIFICATIONS — required on API 33+ for foreground-service notification. */
    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPerm = granted
        if (granted && hasOverlay) startPopupService()
    }

    /** RECORD_AUDIO — optional, used for voice search. */
    private val microphonePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicrophone = granted
    }

    /** READ_EXTERNAL_STORAGE — optional, used for importing backup on API ≤ 32. */
    private val storageReadPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStorageRead = granted
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionStates()

        startWord           = intent?.getStringExtra("lookup_word")
        // Show the permission setup screen on first run or whenever a critical
        // permission is missing. User can also tap "Skip for now" to bypass it.
        showPermissionSetup = !hasOverlay || !hasNotificationPerm

        lifecycleScope.launch {
            val settings = settingsDataStore.settings.first()
            if (settings.showPersistentNotification && hasOverlay && hasNotificationPerm) {
                startPopupService()
                notificationHelper.showPersistentNotification()
            }
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            LexiPopupTheme(
                darkTheme = if (settings.useSystemTheme) isSystemDark() else settings.useDarkMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showPermissionSetup) {
                        PermissionSetupScreen(
                            permissionStates = PermissionStates(
                                hasOverlay       = hasOverlay,
                                hasNotifications = hasNotificationPerm,
                                hasMicrophone    = hasMicrophone,
                                hasStorageRead   = hasStorageRead
                            ),
                            onRequestNotification = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onRequestOverlay    = { requestOverlayPermission() },
                            onRequestMicrophone = {
                                microphonePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRequestStorageRead = {
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                    storageReadPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            },
                            onContinue = {
                                showPermissionSetup = false
                                if (hasOverlay && hasNotificationPerm) startPopupService()
                            }
                        )
                    } else {
                        DashboardScreen(
                            viewModel                  = viewModel,
                            onRequestOverlayPermission = { requestOverlayPermission() },
                            hasOverlayPermission       = hasOverlay,
                            startWord                  = startWord
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh states when returning from system Settings (SYSTEM_ALERT_WINDOW) or
        // after any permission change in another flow.
        refreshPermissionStates()
        // If the user returned from the overlay Settings page and all critical permissions
        // are now satisfied, auto-advance straight to the dashboard without requiring an
        // extra tap on "Start Using LexiPopup".
        if (showPermissionSetup && hasOverlay && hasNotificationPerm) {
            showPermissionSetup = false
            startPopupService()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // startWord update triggers Compose recomposition → LaunchedEffect → WordDetail navigation
        startWord = intent.getStringExtra("lookup_word")
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Snapshot all permission states into the reactive mutableStateOf holders.
     * Called in onCreate and onResume.
     */
    private fun refreshPermissionStates() {
        hasOverlay = Settings.canDrawOverlays(this)
        hasNotificationPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        else true
        hasMicrophone = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasStorageRead = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start PopupService as a foreground service. Uses startForegroundService() on
     * API 26+ (required to allow the service to call startForeground() within 5 s).
     */
    private fun startPopupService() {
        val intent = Intent(this, PopupService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * SYSTEM_ALERT_WINDOW is a special permission that cannot be requested via the
     * normal runtime-permission flow — it must open the system Settings page.
     */
    private fun requestOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }
}

@Composable
fun isSystemDark(): Boolean {
    val config = LocalConfiguration.current
    return config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}
