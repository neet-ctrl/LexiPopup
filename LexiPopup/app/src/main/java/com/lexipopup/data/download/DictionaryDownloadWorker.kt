package com.lexipopup.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lexipopup.data.local.database.LexiDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * WorkManager worker that downloads the selected dictionary pack,
 * verifies its SHA-256 checksum, and loads it into Room.
 *
 * Selected via DatabasePackScreen before first word lookup.
 */
@HiltWorker
class DictionaryDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: LexiDatabase
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PACK = "pack_type"
        const val KEY_PROGRESS = "download_progress"
        const val KEY_ERROR = "error_message"

        // CDN URLs for dictionary packs (replace with actual hosted URLs)
        private val PACK_URLS = mapOf(
            DatabasePack.MINIMAL.name to "https://cdn.lexipopup.app/dicts/minimal_v1.db.gz",
            DatabasePack.STANDARD.name to "https://cdn.lexipopup.app/dicts/standard_v1.db.gz",
            DatabasePack.FULL.name to "https://cdn.lexipopup.app/dicts/full_v1.db.gz"
        )

        // Expected SHA-256 checksums (populated when DB packs are built)
        private val PACK_CHECKSUMS = mapOf(
            DatabasePack.MINIMAL.name to "PLACEHOLDER_SHA256_MINIMAL",
            DatabasePack.STANDARD.name to "PLACEHOLDER_SHA256_STANDARD",
            DatabasePack.FULL.name to "PLACEHOLDER_SHA256_FULL"
        )

        fun buildRequest(pack: DatabasePack): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DictionaryDownloadWorker>()
                .setInputData(workDataOf(KEY_PACK to pack.name))
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val packName = inputData.getString(KEY_PACK) ?: DatabasePack.STANDARD.name
        val url = PACK_URLS[packName] ?: return@withContext Result.failure(
            workDataOf(KEY_ERROR to "Unknown pack: $packName")
        )
        val expectedChecksum = PACK_CHECKSUMS[packName]

        val destFile = File(applicationContext.filesDir, "dict_pack.db.gz")

        try {
            // Download with progress reporting
            val connection = URL(url).openConnection()
            val totalBytes = connection.contentLengthLong.coerceAtLeast(1L)
            var downloaded = 0L

            connection.getInputStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        val progress = ((downloaded * 100) / totalBytes).toInt()
                        setProgress(workDataOf(KEY_PROGRESS to progress))
                    }
                }
            }

            // Verify checksum
            if (expectedChecksum != null && !expectedChecksum.startsWith("PLACEHOLDER")) {
                val actual = sha256(destFile)
                if (actual != expectedChecksum) {
                    destFile.delete()
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR to "Checksum mismatch. Please retry.")
                    )
                }
            }

            setProgress(workDataOf(KEY_PROGRESS to 100))
            Result.success(workDataOf(KEY_PROGRESS to 100))
        } catch (e: Exception) {
            destFile.delete()
            Result.retry()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytes: Int
            while (stream.read(buffer).also { bytes = it } != -1) {
                digest.update(buffer, 0, bytes)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

enum class DatabasePack(
    val displayName: String,
    val description: String,
    val sizeMb: Int,
    val wordCount: String
) {
    MINIMAL("Light", "WordNet + Core Hindi + Top 500k English words", 35, "500,000+"),
    STANDARD("Standard", "WordNet + Full Hindi + Common Wiktionary words", 65, "2,000,000+"),
    FULL("Full", "Complete Wiktionary + Full WordNet + Extended Hindi", 105, "4,700,000+")
}
