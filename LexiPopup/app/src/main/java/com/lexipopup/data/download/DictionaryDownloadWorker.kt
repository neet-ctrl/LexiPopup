package com.lexipopup.data.download

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.entities.WordEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

/**
 * Downloads a dictionary pack with resume support, GZip decompression,
 * SHA-256 checksum verification, and batch import into Room.
 *
 * Phase progression → progress %:
 *   DOWNLOADING  → 0–65%
 *   DECOMPRESSING→ 65–75%
 *   VERIFYING    → 75–80%
 *   IMPORTING    → 80–99%
 *   DONE         → 100%
 */
@HiltWorker
class DictionaryDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val wordDao: WordDao,
    private val downloadStateStore: DownloadStateStore
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PACK          = "pack_type"
        const val KEY_RESUME_BYTES  = "resume_bytes"
        const val KEY_PROGRESS      = "download_progress"
        const val KEY_PHASE         = "phase"
        const val KEY_BYTES_DL      = "bytes_downloaded"
        const val KEY_TOTAL_BYTES   = "total_bytes"
        const val KEY_SPEED_KBPS    = "speed_kbps"
        const val KEY_ETA_SECONDS   = "eta_seconds"
        const val KEY_WORDS_DONE    = "words_imported"
        const val KEY_ERROR         = "error_message"
        const val KEY_LOG           = "download_log"

        const val PHASE_DOWNLOADING  = "downloading"
        const val PHASE_DECOMPRESS   = "decompressing"
        const val PHASE_VERIFYING    = "verifying"
        const val PHASE_IMPORTING    = "importing"
        const val PHASE_DONE         = "done"

        // ── Download URLs — GitHub Releases, tag dict-v1 ─────────────────────
        // Keys use plain strings (not DatabasePack.X.name) so the generate-dicts.yml
        // sed auto-patch for PACK_CHECKSUMS does NOT accidentally overwrite these.
        private val PACK_URLS = mapOf(
            "MINIMAL"  to "https://github.com/neet-ctrl/LexiPopup/releases/download/dict-v1/dict_minimal_v1.db.gz",
            "STANDARD" to "https://github.com/neet-ctrl/LexiPopup/releases/download/dict-v1/dict_standard_v1.db.gz",
            "FULL"     to "https://github.com/neet-ctrl/LexiPopup/releases/download/dict-v1/dict_full_v1.db.gz"
        )

        // SHA-256 of each .db.gz file — auto-patched by the "Generate Dictionary Packs"
        // GitHub Actions workflow after each run. Leave as "SKIP" to disable during dev.
        private val PACK_CHECKSUMS = mapOf(
            DatabasePack.MINIMAL.name  to "f9e588517fd5a668939f8886d776764c3f2ec82b9029a933a547e7377b229c54",
            DatabasePack.STANDARD.name to "cb8f507a8643214a2a9c10eafdd45dbcaa04e832d3c1d00ba79085246102ca3e",
            DatabasePack.FULL.name     to "53104834a4aa1e1124c2436d8f20555290b68071456638d6483abb1b41892fef"
        )

        fun uniqueWorkName(pack: DatabasePack) = "dict_download_${pack.name}"

        fun buildRequest(pack: DatabasePack, resumeBytes: Long = 0L): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DictionaryDownloadWorker>()
                .setInputData(workDataOf(
                    KEY_PACK         to pack.name,
                    KEY_RESUME_BYTES to resumeBytes
                ))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("lexipopup_download")
                .addTag("lexipopup_download_${pack.name}")
                .build()
    }

    // Accumulates log lines; capped at MAX_LOG_CHARS to stay inside WorkManager Data limit.
    private val logBuf = StringBuilder()
    private val MAX_LOG_CHARS = 6_000
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun log(msg: String) {
        val line = "[${timeFmt.format(Date())}] $msg\n"
        logBuf.append(line)
        // Keep only the last MAX_LOG_CHARS characters so we don't exceed WorkManager limits
        if (logBuf.length > MAX_LOG_CHARS) {
            logBuf.delete(0, logBuf.length - MAX_LOG_CHARS)
        }
    }

    private suspend fun emitProgress(vararg pairs: Pair<String, Any?>) {
        val data = workDataOf(*pairs, KEY_LOG to logBuf.toString())
        setProgress(data)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val packName = inputData.getString(KEY_PACK) ?: DatabasePack.STANDARD.name
        val pack = DatabasePack.entries.firstOrNull { it.name == packName }
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Unknown pack: $packName"))

        val url = PACK_URLS[packName]
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "No URL configured for pack $packName"))
        val expectedChecksum = PACK_CHECKSUMS[packName]

        log("=== LexiPopup Dictionary Download ===")
        log("Pack: ${pack.displayName} ($packName)")
        log("URL: $url")

        val gzFile = File(applicationContext.filesDir, "dict_${packName}.db.gz")
        val dbFile = File(applicationContext.filesDir, "dict_${packName}.db")

        return@withContext try {
            // ─── Phase 1: Download (with resume) ───────────────────────────
            val downloaded = downloadWithResume(pack, url, gzFile)
            if (downloaded < 0) {
                log("❌ Network error — will retry")
                gzFile.delete()
                return@withContext Result.retry()
            }

            // ─── Phase 2: Decompress ────────────────────────────────────────
            log("⚙ Decompressing .gz → .db …")
            emitProgress(KEY_PHASE to PHASE_DECOMPRESS, KEY_PROGRESS to 66)
            decompressGzip(gzFile, dbFile)
            log("✓ Decompressed: ${dbFile.length() / 1_048_576}MB uncompressed")

            // ─── Phase 3: Verify checksum on .gz ───────────────────────────
            log("🔍 Verifying SHA-256 checksum…")
            emitProgress(KEY_PHASE to PHASE_VERIFYING, KEY_PROGRESS to 76)
            val skipVerify = expectedChecksum.isNullOrBlank()
                || expectedChecksum.startsWith("PLACEHOLDER")
                || expectedChecksum.equals("SKIP", ignoreCase = true)
            if (!skipVerify) {
                val actual = sha256(gzFile)
                log("  Expected: $expectedChecksum")
                log("  Actual:   $actual")
                if (actual != expectedChecksum) {
                    log("❌ Checksum MISMATCH — file corrupt, deleting")
                    gzFile.delete(); dbFile.delete()
                    downloadStateStore.clearDownloadProgress(pack)
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR to "Checksum mismatch. File may be corrupt. Please retry.",
                                   KEY_LOG to logBuf.toString())
                    )
                }
                log("✓ Checksum OK")
            } else {
                log("⚠ Checksum verification skipped")
            }
            gzFile.delete()

            // ─── Phase 4: Import into Room ─────────────────────────────────
            log("📥 Importing into database…")
            emitProgress(KEY_PHASE to PHASE_IMPORTING, KEY_PROGRESS to 80)
            val wordCount = importDatabase(pack, dbFile)
            dbFile.delete()
            log("✓ Imported $wordCount words")

            // ─── Phase 5: Mark done ────────────────────────────────────────
            downloadStateStore.markInstalled(pack, wordCount)
            log("🎉 Done! ${pack.displayName} is ready.")
            emitProgress(KEY_PHASE to PHASE_DONE, KEY_PROGRESS to 100, KEY_WORDS_DONE to wordCount)
            Result.success(workDataOf(KEY_PHASE to PHASE_DONE, KEY_WORDS_DONE to wordCount,
                                      KEY_LOG to logBuf.toString()))

        } catch (e: Exception) {
            log("❌ Unexpected error: ${e.javaClass.simpleName}: ${e.message}")
            dbFile.delete()
            Result.retry()
        }
    }

    /**
     * Downloads [url] to [dest] with HTTP Range resume support.
     * Returns the total bytes downloaded, or -1 on network error.
     */
    private suspend fun downloadWithResume(pack: DatabasePack, url: String, dest: File): Long {
        val existingBytes = if (dest.exists()) dest.length() else 0L
        if (existingBytes > 0) log("▶ Resuming from ${existingBytes / 1024}KB")

        val conn: HttpURLConnection
        try {
            conn = URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            log("❌ Failed to open connection: ${e.message}")
            return -1L
        }

        conn.connectTimeout = 15_000
        conn.readTimeout    = 30_000
        conn.setRequestProperty("Accept-Encoding", "identity")
        conn.setRequestProperty("User-Agent", "LexiPopup/1.0")
        if (existingBytes > 0) {
            conn.setRequestProperty("Range", "bytes=$existingBytes-")
            log("  Range: bytes=$existingBytes-")
        }

        log("🌐 Connecting to: $url")
        try {
            conn.connect()
        } catch (e: Exception) {
            log("❌ Connection failed: ${e.message}")
            return -1L
        }

        val responseCode = conn.responseCode
        val responseMsg  = conn.responseMessage ?: ""
        log("  HTTP $responseCode $responseMsg")

        val resumeSupported = responseCode == HttpURLConnection.HTTP_PARTIAL
        val fullRestart     = responseCode == HttpURLConnection.HTTP_OK

        if (!resumeSupported && !fullRestart) {
            log("❌ Unexpected HTTP $responseCode — cannot download")
            conn.disconnect()
            return -1L
        }

        val serverLength = conn.contentLengthLong
        val totalBytes   = if (resumeSupported) existingBytes + serverLength else serverLength
        log("  Content-Length: ${serverLength / 1024}KB  |  Total expected: ${totalBytes / 1024}KB")
        if (resumeSupported) log("  Resume: server supports Range (206 Partial Content)")

        downloadStateStore.setTotalBytes(pack, totalBytes)

        val appendMode = resumeSupported && existingBytes > 0 && dest.exists()
        var downloaded = if (appendMode) existingBytes else 0L

        if (!appendMode && dest.exists()) dest.delete()

        val speedWindow = ArrayDeque<Pair<Long, Long>>()
        var lastProgressUpdate = System.currentTimeMillis()
        var lastLogUpdate      = System.currentTimeMillis()

        emitProgress(
            KEY_PHASE to PHASE_DOWNLOADING,
            KEY_PROGRESS to 0,
            KEY_BYTES_DL to downloaded,
            KEY_TOTAL_BYTES to totalBytes
        )

        try {
            conn.inputStream.use { input: InputStream ->
                FileOutputStream(dest, appendMode).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        downloadStateStore.setDownloadedBytes(pack, downloaded)

                        val now = System.currentTimeMillis()
                        speedWindow.add(now to downloaded)
                        while (speedWindow.size > 1 && now - speedWindow.first().first > 3_000) {
                            speedWindow.removeFirst()
                        }

                        if (now - lastProgressUpdate >= 300) {
                            lastProgressUpdate = now
                            val rawProgress = if (totalBytes > 0) ((downloaded * 65) / totalBytes).toInt() else 0
                            val speedKbps = if (speedWindow.size >= 2) {
                                val dt = (speedWindow.last().first - speedWindow.first().first).coerceAtLeast(1)
                                val db = speedWindow.last().second - speedWindow.first().second
                                ((db * 1000) / (dt * 1024)).toInt()
                            } else 0
                            val etaSec = if (speedKbps > 0 && totalBytes > downloaded) {
                                val remaining = (totalBytes - downloaded) / 1024
                                (remaining / speedKbps).toInt()
                            } else -1

                            // Log a progress line every ~5 seconds
                            if (now - lastLogUpdate >= 5_000 && totalBytes > 0) {
                                lastLogUpdate = now
                                val pct = (downloaded * 100 / totalBytes).toInt()
                                val dlMb  = downloaded / 1_048_576.0
                                val totMb = totalBytes / 1_048_576.0
                                val speedStr = if (speedKbps >= 1024) "%.1f MB/s".format(speedKbps/1024.0) else "$speedKbps KB/s"
                                log("  ↓ $pct%  %.1fMB / %.1fMB  @ $speedStr".format(dlMb, totMb))
                            }

                            emitProgress(
                                KEY_PHASE    to PHASE_DOWNLOADING,
                                KEY_PROGRESS to rawProgress,
                                KEY_BYTES_DL to downloaded,
                                KEY_TOTAL_BYTES to totalBytes,
                                KEY_SPEED_KBPS  to speedKbps,
                                KEY_ETA_SECONDS to etaSec
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log("❌ Download stream error: ${e.message}")
            conn.disconnect()
            return -1L
        }

        conn.disconnect()
        log("✓ Download complete: ${downloaded / 1024}KB received")
        return downloaded
    }

    /** Decompresses a gzipped file to [dest]. */
    private fun decompressGzip(src: File, dest: File) {
        GZIPInputStream(src.inputStream().buffered(64 * 1024)).use { gz ->
            FileOutputStream(dest).use { out ->
                gz.copyTo(out, bufferSize = 64 * 1024)
            }
        }
    }

    /**
     * Opens the downloaded SQLite [dbFile] and batch-inserts all rows into Room.
     * Handles missing columns gracefully (uses defaults for absent fields).
     */
    private suspend fun importDatabase(pack: DatabasePack, dbFile: File): Int {
        val srcDb = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        val cursor = srcDb.rawQuery("SELECT * FROM dictionary_cache", null)
        val total  = cursor.count
        log("  Source DB has $total rows to import")
        val batch  = mutableListOf<WordEntity>()
        var done   = 0

        fun col(name: String): Int = cursor.getColumnIndex(name)

        fun getString(name: String, default: String = ""): String {
            val idx = col(name); return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else default
        }
        fun getInt(name: String, default: Int = 0): Int {
            val idx = col(name); return if (idx >= 0 && !cursor.isNull(idx)) cursor.getInt(idx) else default
        }

        while (cursor.moveToNext()) {
            batch += WordEntity(
                id                = 0,
                word              = getString("word"),
                pronunciation     = getString("pronunciation"),
                partOfSpeech      = getString("part_of_speech"),
                shortMeaning      = getString("short_meaning"),
                detailedMeaning   = getString("detailed_meaning"),
                hindiMeaning      = getString("hindi_meaning"),
                hindiPronunciation= getString("hindi_pronunciation"),
                exampleSentence   = getString("example_sentence"),
                synonyms          = getString("synonyms", "[]"),
                antonyms          = getString("antonyms", "[]"),
                etymology         = getString("etymology"),
                difficultyLevel   = getInt("difficulty_level", 1),
                frequencyRating   = getInt("frequency_rating", 50),
                source            = pack.name.lowercase()
            )

            if (batch.size >= 500) {
                wordDao.insertAll(batch)
                done += batch.size
                batch.clear()
                val progress = 80 + ((done * 19) / total.coerceAtLeast(1))
                emitProgress(
                    KEY_PHASE    to PHASE_IMPORTING,
                    KEY_PROGRESS to progress,
                    KEY_WORDS_DONE to done
                )
            }
        }
        if (batch.isNotEmpty()) {
            wordDao.insertAll(batch)
            done += batch.size
        }
        cursor.close()
        srcDb.close()
        return done
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
