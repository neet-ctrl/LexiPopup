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
        const val CHANNEL_ID          = "lexipopup_quick_search"
        const val CHANNEL_FLASHCARD_ID = "lexipopup_flashcard_review"
        const val NOTIFICATION_ID          = 1001
        const val NOTIFICATION_FLASHCARD_ID = 1002
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val searchChannel = NotificationChannel(
            CHANNEL_ID, "Quick Dictionary Search", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Tap to open dictionary popup from anywhere"; setShowBadge(false) }

        val flashcardChannel = NotificationChannel(
            CHANNEL_FLASHCARD_ID, "Flashcard Review Reminder", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Daily reminder when flashcards are due for spaced-repetition review"
            setShowBadge(true)
        }

        manager.createNotificationChannel(searchChannel)
        manager.createNotificationChannel(flashcardChannel)
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
            .addAction(android.R.drawable.ic_menu_search, "Search Word", pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun dismissPersistentNotification() {
        manager.cancel(NOTIFICATION_ID)
    }

    /**
     * Fires the daily flashcard reminder. Called by [FlashcardReminderWorker] when
     * there are due cards. The notification is auto-cancelled when tapped.
     */
    fun showFlashcardReminderNotification(dueCount: Int) {
        if (!manager.areNotificationsEnabled()) return

        val intent = Intent(context, com.lexipopup.presentation.MainActivity::class.java).apply {
            putExtra("tab", "flashcards")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_FLASHCARD_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_FLASHCARD_ID)
            .setContentTitle("Flashcard Review Due \uD83E\uDDEA")
            .setContentText(
                if (dueCount == 1) "1 flashcard is due for review"
                else "$dueCount flashcards are due for review"
            )
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        manager.notify(NOTIFICATION_FLASHCARD_ID, notification)
    }

    fun cancelFlashcardReminder() {
        manager.cancel(NOTIFICATION_FLASHCARD_ID)
    }
}
