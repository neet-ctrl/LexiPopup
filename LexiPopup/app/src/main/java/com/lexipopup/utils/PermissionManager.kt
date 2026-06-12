package com.lexipopup.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all runtime and special permission checks.
 *
 * Permission taxonomy:
 *  - Install-time:   FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, VIBRATE, etc.
 *                    → always granted after install, no runtime prompt needed
 *  - Special:        SYSTEM_ALERT_WINDOW
 *                    → must redirect user to Settings.ACTION_MANAGE_OVERLAY_PERMISSION
 *  - Runtime:        POST_NOTIFICATIONS (API 33+), RECORD_AUDIO, READ_EXTERNAL_STORAGE (≤32)
 *                    → use ActivityResultContracts.RequestPermission / RequestMultiplePermissions
 *  - Special (battery): REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 *                    → direct intent Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS or
 *                      fallback Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** SYSTEM_ALERT_WINDOW — required for popup overlay (special permission). */
    fun hasOverlay(): Boolean = Settings.canDrawOverlays(context)

    /** POST_NOTIFICATIONS — required on API 33+ for foreground-service notification. */
    fun hasNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        else true   // Granted implicitly below API 33

    /** RECORD_AUDIO — optional, used for voice search. */
    fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    /** READ_EXTERNAL_STORAGE — optional, used for importing backup files on API ≤ 32. */
    fun hasStorageRead(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — required on OEMs (Xiaomi, OPPO, Huawei, Samsung)
     * that aggressively kill background services. Returns true when our app is already
     * whitelisted from battery optimisation.
     */
    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Returns true when every permission required for the popup to work is granted.
     * Optional permissions (microphone, storage) are not checked here.
     */
    fun allCriticalGranted(): Boolean = hasOverlay() && hasNotifications()
}
