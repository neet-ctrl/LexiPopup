package com.lexipopup.presentation.popup

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.lexipopup.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps LexiPopup alive for faster popup launch.
 * Runs at IMPORTANCE_MIN — invisible in the status bar, no sound/vibration.
 * Started by BootReceiver on device restart and by MainActivity on first launch.
 */
@AndroidEntryPoint
class PopupService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            notificationHelper.buildServiceNotification()
        )
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
