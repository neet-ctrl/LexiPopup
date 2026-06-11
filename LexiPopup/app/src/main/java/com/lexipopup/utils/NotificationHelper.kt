package com.lexipopup.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lexipopup.presentation.popup.PopupActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "lexipopup_quick_search"
        const val NOTIFICATION_ID = 1001
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Quick Dictionary Search",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tap to open dictionary popup from anywhere"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun showPersistentNotification() {
        val intent = Intent(context, PopupActivity::class.java).apply {
            putExtra("mode", "manual_search")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("LexiPopup")
            .setContentText("Tap to search any word")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_search,
                "Search Word",
                pendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun dismissPersistentNotification() {
        manager.cancel(NOTIFICATION_ID)
    }
}
