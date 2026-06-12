package com.lexipopup.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.lexipopup.presentation.dashboard.MainActivity
import com.lexipopup.presentation.popup.PopupActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID           = "lexipopup_quick_search"
        const val CHANNEL_FLASHCARD_ID = "lexipopup_flashcard_review"
        const val CHANNEL_SERVICE_ID   = "lexipopup_service"
        const val CHANNEL_WOTD_ID      = "lexipopup_wotd"
        const val NOTIFICATION_ID           = 1001
        const val NOTIFICATION_FLASHCARD_ID = 1002
        const val SERVICE_NOTIFICATION_ID   = 1003
        const val NOTIFICATION_WOTD_ID      = 1004
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

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE_ID, "LexiPopup Background Service", NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps LexiPopup ready for instant word lookup"
            setShowBadge(false)
            setSound(null, null)
        }

        val wotdChannel = NotificationChannel(
            CHANNEL_WOTD_ID, "Word of the Day", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily word to expand your vocabulary"
            setShowBadge(true)
        }

        manager.createNotificationChannel(searchChannel)
        manager.createNotificationChannel(flashcardChannel)
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(wotdChannel)
    }

    fun buildServiceNotification(): android.app.Notification {
        val intent = Intent(context, PopupActivity::class.java).apply {
            putExtra("mode", "manual_search")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, SERVICE_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle("LexiPopup")
            .setContentText("Ready for instant word lookup")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
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

        val intent = Intent(context, com.lexipopup.presentation.dashboard.MainActivity::class.java).apply {
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

    /**
     * Fires the daily Word of the Day notification. Tapping it opens the same
     * glassmorphism floating popup (PopupActivity) via the deep link URI so the
     * UI is identical to looking up a word from Moon+ Reader or the persistent
     * notification. A secondary action lets the user open the full dashboard.
     */
    fun showWotdNotification(word: String, shortMeaning: String, hindiMeaning: String, source: String = "") {
        if (!manager.areNotificationsEnabled()) return

        // Open the same glassmorphism floating popup (PopupActivity) via deep link
        val intent = Intent(context, PopupActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data   = Uri.parse("lexipopup://lookup?word=${Uri.encode(word)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_WOTD_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sourceLabel = when (source.lowercase()) {
            "seed"      -> "Seed DB"
            "minimal"   -> "Minimal Pack"
            "standard"  -> "Standard Pack"
            "full"      -> "Full Pack"
            "online"    -> "Online API"
            "groq"      -> "Groq AI"
            "openai"    -> "OpenAI"
            "on_device" -> "On-Device AI"
            else        -> source.replaceFirstChar { it.uppercase() }
        }

        val bodyText = buildString {
            append(shortMeaning.take(80))
            if (hindiMeaning.isNotBlank()) append(" • ${hindiMeaning.take(30)}")
            if (sourceLabel.isNotBlank()) append(" [${sourceLabel}]")
        }

        // Secondary action: open full dashboard (for users who want the complete view)
        val fullAppIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("lookup_word", word)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullAppPendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_WOTD_ID + 100, fullAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WOTD_ID)
            .setContentTitle("\uD83D\uDCC5 Word of the Day: ${word.replaceFirstChar { it.uppercase() }}")
            .setContentText(bodyText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_search, "Quick Look", pendingIntent)
            .addAction(android.R.drawable.ic_menu_info_details, "Full Details", fullAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .build()

        manager.notify(NOTIFICATION_WOTD_ID, notification)
    }

    fun cancelWotdNotification() {
        manager.cancel(NOTIFICATION_WOTD_ID)
    }
}
