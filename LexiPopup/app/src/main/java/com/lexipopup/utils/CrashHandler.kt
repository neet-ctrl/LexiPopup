package com.lexipopup.utils

import android.content.Context
import android.content.Intent
import com.lexipopup.presentation.crash.CrashLogActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a global uncaught-exception handler that:
 *  1. Formats the full stack trace + device/build info into a readable crash report
 *  2. Appends the report to a rotating log file (keeps last 5 crashes)
 *  3. Launches CrashLogActivity so the user sees the details immediately
 *
 * Install once in Application.onCreate() via [CrashHandler.install].
 */
object CrashHandler {

    private const val LOG_DIR  = "crash_logs"
    private const val MAX_LOGS = 5

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(context, thread, throwable)
                val file   = saveReport(context, report)

                val intent = Intent(context, CrashLogActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra(CrashLogActivity.EXTRA_REPORT, report)
                    putExtra(CrashLogActivity.EXTRA_FILE_PATH, file.absolutePath)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                // Never let our handler crash — fall through to the default
            }

            // Let the system also know (writes to logcat, shows ANR dialog, etc.)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val pkg  = context.packageName
        val info = try {
            context.packageManager.getPackageInfo(pkg, 0)
        } catch (_: Exception) { null }
        val versionName = info?.versionName ?: "?"
        val versionCode = info?.longVersionCode ?: 0L

        return buildString {
            appendLine("══════════════════════════════════════════")
            appendLine("  LexiPopup Crash Report")
            appendLine("══════════════════════════════════════════")
            appendLine("Time         : $ts")
            appendLine("App version  : $versionName ($versionCode)")
            appendLine("Package      : $pkg")
            appendLine("Thread       : ${thread.name}")
            appendLine()
            appendLine("── Device ─────────────────────────────────")
            appendLine("Brand        : ${android.os.Build.BRAND}")
            appendLine("Model        : ${android.os.Build.MODEL}")
            appendLine("Android      : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Manufacturer : ${android.os.Build.MANUFACTURER}")
            appendLine()
            appendLine("── Exception ───────────────────────────────")
            appendLine(stackTrace)
            appendLine("══════════════════════════════════════════")
        }
    }

    private fun saveReport(context: Context, report: String): File {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }

        // Rotate: keep only the last MAX_LOGS files
        dir.listFiles()
            ?.sortedBy { it.lastModified() }
            ?.dropLast(MAX_LOGS - 1)
            ?.forEach { it.delete() }

        val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$ts.txt")
        file.writeText(report)
        return file
    }

    /** Returns the most recent saved crash report text, or null if none. */
    fun lastReport(context: Context): String? {
        val dir = File(context.filesDir, LOG_DIR)
        return dir.listFiles()
            ?.maxByOrNull { it.lastModified() }
            ?.readText()
    }

    /** Returns all saved crash log files, newest first. */
    fun allReports(context: Context): List<File> {
        val dir = File(context.filesDir, LOG_DIR)
        return (dir.listFiles()?.toList() ?: emptyList())
            .sortedByDescending { it.lastModified() }
    }
}
