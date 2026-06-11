package com.lexipopup.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lexipopup.data.local.dao.FlashcardDao
import com.lexipopup.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that fires a flashcard review reminder every morning
 * at ~08:00 when there are cards due for spaced-repetition review.
 *
 * Scheduling: call [FlashcardReminderWorker.schedule] once (e.g. in Application or on first
 * app launch). WorkManager keeps the work alive across device reboots automatically.
 */
@HiltWorker
class FlashcardReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val flashcardDao: FlashcardDao,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dueCount = flashcardDao.getDueCountOnce(System.currentTimeMillis())
        if (dueCount > 0) {
            notificationHelper.showFlashcardReminderNotification(dueCount)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "lexipopup_flashcard_daily_reminder"

        /**
         * Enqueues a unique daily periodic work request targeting 08:00.
         * Safe to call multiple times — [ExistingPeriodicWorkPolicy.KEEP] is used so
         * existing schedules are not disturbed.
         */
        fun schedule(context: Context) {
            val initialDelay = calculateDelayUntilMorning()
            val request = PeriodicWorkRequestBuilder<FlashcardReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancel any pending daily reminder (e.g. when the user disables reminders). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun calculateDelayUntilMorning(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
