package com.lexipopup

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lexipopup.utils.CrashHandler
import com.lexipopup.workers.FlashcardReminderWorker
import com.lexipopup.workers.WotdNotificationWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LexiPopupApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Install crash handler before anything else — catches all uncaught exceptions
        // and shows CrashLogActivity with a copy button so the log can be shared instantly.
        CrashHandler.install(this)
        FlashcardReminderWorker.schedule(this)
        WotdNotificationWorker.schedule(this)
    }
}
