package com.lexipopup.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lexipopup.presentation.popup.PopupService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Started automatically by Android after device boot (BOOT_COMPLETED broadcast).
 * Re-starts PopupService and the persistent notification so the app is ready
 * for instant word lookup without the user having to open the app first.
 *
 * Manifest entry: android.permission.RECEIVE_BOOT_COMPLETED
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val settings = settingsDataStore.settings.first()
            if (!settings.showPersistentNotification) return@launch

            // Start PopupService as foreground (API 26+ requires startForegroundService).
            // BOOT_COMPLETED is a system-whitelisted event so background start restrictions
            // do not apply here.
            val serviceIntent = Intent(context, PopupService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Also show the quick-search persistent notification (separate from the
            // silent foreground-service notification that PopupService manages itself).
            notificationHelper.showPersistentNotification()
        }
    }
}
