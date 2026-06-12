package com.lexipopup.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.utils.NotificationHelper
import com.lexipopup.utils.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily WorkManager worker that fires a Word of the Day push notification at the
 * user-configured hour (default 09:00). Skipped silently if notifications are
 * disabled in WOTD settings.
 *
 * Scheduling: call [schedule] once on first app launch. Use [reschedule] whenever
 * the user changes the notification hour in WOTD settings. [cancel] disables it.
 */
@HiltWorker
class WotdNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val dictionaryRepository: DictionaryRepository,
    private val notificationHelper: NotificationHelper,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settings = settingsDataStore.settings.first()
        if (!settings.wotdNotificationEnabled) return Result.success()

        val word = dictionaryRepository.getWordOfDay(settings.wotdMode, settings.wotdUserLevel)
            ?: return Result.success()

        notificationHelper.showWotdNotification(
            word = word.word,
            shortMeaning = word.shortMeaning,
            hindiMeaning = word.hindiMeaning,
            source = word.source
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "lexipopup_wotd_daily"

        /**
         * Enqueues the daily WOTD work targeting [hour]:00 local time.
         * Uses [ExistingPeriodicWorkPolicy.KEEP] — safe to call multiple times on app start
         * without disrupting an already-scheduled chain.
         */
        fun schedule(context: Context, hour: Int = 9) {
            val request = buildRequest(hour, ExistingPeriodicWorkPolicy.KEEP)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /**
         * Cancels any existing schedule and re-enqueues with the new [hour].
         * Call this after the user changes the notification time in settings.
         */
        fun reschedule(context: Context, hour: Int) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME)
            val request = buildRequest(hour, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
            wm.enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request
            )
        }

        /** Permanently cancels the daily WOTD notification. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun buildRequest(
            hour: Int,
            @Suppress("UNUSED_PARAMETER") policy: ExistingPeriodicWorkPolicy
        ): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<WotdNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayUntilHour(hour), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()
        }

        private fun calculateDelayUntilHour(hour: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
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
