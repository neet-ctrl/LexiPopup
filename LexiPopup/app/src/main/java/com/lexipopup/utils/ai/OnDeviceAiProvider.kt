package com.lexipopup.utils.ai

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.lexipopup.domain.models.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * On-device AI provider using MediaPipe LLM Inference Task.
 *
 * Model download URLs (public, no auth required):
 *   Gemma 2B Tiny (900 MB):
 *     https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-cpu-int4/float32/1/gemma-2b-it-cpu-int4.bin
 *   Phi-2 Standard (1.6 GB):
 *     https://storage.googleapis.com/mediapipe-models/llm_inference/phi-2-default-cpu/float32/1/phi-2-default-cpu.task
 *
 * If in-app download keeps failing (battery optimisation, poor signal), download the file
 * manually in a browser and use "Pick from Storage" in the AI Settings screen.
 */
class OnDeviceAiProvider(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val modelsDir = File(context.filesDir, "ai_models").also { it.mkdirs() }

    private val _modelStatus = MutableStateFlow<OnDeviceModelStatus>(OnDeviceModelStatus.NotDownloaded)
    val modelStatus: StateFlow<OnDeviceModelStatus> = _modelStatus.asStateFlow()

    private val _downloadLogs = MutableStateFlow<List<String>>(emptyList())
    val downloadLogs: StateFlow<List<String>> = _downloadLogs.asStateFlow()

    var selectedModel: OnDeviceModel = OnDeviceModel.TINY
        private set

    // Dedicated download client — no read timeout for large files.
    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    init { refreshStatus() }

    fun selectModel(model: OnDeviceModel) {
        selectedModel = model
        refreshStatus()
    }

    fun refreshStatus() {
        val f = modelFile()
        _modelStatus.value = if (f.exists() && f.length() > 100_000L)
            OnDeviceModelStatus.Downloaded
        else
            OnDeviceModelStatus.NotDownloaded
    }

    fun modelFile(model: OnDeviceModel = selectedModel) = File(modelsDir, model.fileName)

    fun isModelReady(): Boolean {
        val f = modelFile()
        return f.exists() && f.length() > 100_000L
    }

    fun clearLogs() { _downloadLogs.value = emptyList() }

    private fun log(msg: String) {
        val ts = timeFmt.format(Date())
        _downloadLogs.value = _downloadLogs.value + "[$ts] $msg"
    }

    // ── Download from URL ─────────────────────────────────────────────────────

    suspend fun downloadModel(
        model: OnDeviceModel = selectedModel,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        selectedModel = model
        val dest = modelFile(model)
        _downloadLogs.value = emptyList()
        _modelStatus.value = OnDeviceModelStatus.Downloading(0f, 0L, 0L)

        // Resume support
        val existingBytes = if (dest.exists()) dest.length() else 0L
        val requestBuilder = Request.Builder().url(model.downloadUrl)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            log("Resuming from ${existingBytes / (1024 * 1024)} MB already saved")
        } else {
            log("Starting download: ${model.displayName}")
        }
        log("URL: ${model.downloadUrl}")
        log("Saving to: ${dest.absolutePath}")

        try {
            log("Connecting to server…")
            val response = downloadClient.newCall(requestBuilder.build()).execute()
            log("Server replied: HTTP ${response.code} ${response.message}")

            when (response.code) {
                200, 206 -> log("Connection OK — beginning transfer")
                401, 403 -> {
                    val msg = "HTTP ${response.code} — server denied access.\nTry the manual browser download link shown below."
                    log("ERROR: $msg"); response.close()
                    _modelStatus.value = OnDeviceModelStatus.Error(msg); return@withContext
                }
                404 -> {
                    val msg = "HTTP 404 — file not found at this URL.\nThe model may have moved. Try picking the file from storage."
                    log("ERROR: $msg"); response.close()
                    _modelStatus.value = OnDeviceModelStatus.Error(msg); return@withContext
                }
                else -> {
                    val msg = "HTTP ${response.code} — unexpected server error: ${response.message}"
                    log("ERROR: $msg"); response.close()
                    _modelStatus.value = OnDeviceModelStatus.Error(msg); return@withContext
                }
            }

            val body = response.body ?: run {
                log("ERROR: Server returned an empty body")
                _modelStatus.value = OnDeviceModelStatus.Error("Empty response body from server")
                return@withContext
            }

            val appendMode   = response.code == 206 && existingBytes > 0
            var received     = if (appendMode) existingBytes else 0L
            val serverLength = body.contentLength()
            val total        = if (appendMode) existingBytes + serverLength else serverLength
            log("File size: ${if (total > 0) "${total / (1024 * 1024)} MB" else "unknown (server didn't report)"}")
            log("Writing to disk…")

            var lastLoggedMb = received / (1024 * 1024)
            FileOutputStream(dest, appendMode).use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int = -1
                    while (isActive && inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        received += n
                        val pct = if (total > 0) received.toFloat() / total else 0f
                        _modelStatus.value = OnDeviceModelStatus.Downloading(pct, received, total)
                        onProgress(pct)
                        // Log every 50 MB
                        val mb = received / (1024 * 1024)
                        if (mb >= lastLoggedMb + 50) {
                            log("Downloaded ${mb} MB${if (total > 0) " / ${total / (1024 * 1024)} MB (${(pct * 100).toInt()}%)" else ""}")
                            lastLoggedMb = mb
                        }
                    }
                }
            }

            // Check cancellation — keep partial file so Retry can resume
            if (!isActive) {
                val savedMb = dest.length() / (1024 * 1024)
                log("Download paused by user at ${savedMb} MB — tap Retry to resume")
                _modelStatus.value = OnDeviceModelStatus.Error("Download paused at ${savedMb} MB — tap Retry to resume from here")
                return@withContext
            }

            log("✓ Download complete! Saved ${dest.length() / (1024 * 1024)} MB")
            _modelStatus.value = OnDeviceModelStatus.Downloaded

        } catch (e: Exception) {
            val savedMb = if (dest.exists()) dest.length() / (1024 * 1024) else 0L
            log("EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            if (savedMb == 0L) dest.delete()   // Only wipe if nothing saved — keep partial for resume
            val msg = when {
                e.message?.contains("cancel", ignoreCase = true) == true ->
                    "Download paused at ${savedMb} MB — tap Retry to resume from here"
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Connection timed out.${if (savedMb > 0) " ${savedMb} MB saved — tap Retry to resume." else " Check your network and retry."}"
                e.message?.contains("ECONNREFUSED") == true ->
                    "Connection refused. Check your internet connection and retry."
                else -> "${e.message ?: "Download failed"}${if (savedMb > 0) "\n${savedMb} MB already saved — Retry will resume." else ""}"
            }
            _modelStatus.value = OnDeviceModelStatus.Error(msg)
        }
    }

    // ── Import via SAF (Storage Access Framework) ─────────────────────────────

    suspend fun importFromUri(uri: Uri, model: OnDeviceModel = selectedModel) = withContext(Dispatchers.IO) {
        selectedModel = model
        val dest = modelFile(model)
        _downloadLogs.value = emptyList()
        log("Importing model from local storage…")
        log("Saving to: ${dest.absolutePath}")

        try {
            _modelStatus.value = OnDeviceModelStatus.Downloading(0f, 0L, -1L)
            context.contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    var received = 0L
                    var lastLoggedMb = 0L
                    while (inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        received += n
                        _modelStatus.value = OnDeviceModelStatus.Downloading(0f, received, -1L)
                        val mb = received / (1024 * 1024)
                        if (mb >= lastLoggedMb + 100) {
                            log("Copied ${mb} MB so far…")
                            lastLoggedMb = mb
                        }
                    }
                }
            } ?: run {
                log("ERROR: Cannot open file — check Files permission")
                _modelStatus.value = OnDeviceModelStatus.Error("Cannot open file. Grant storage permission and retry.")
                return@withContext
            }

            val size = dest.length()
            log("Import complete — file size: ${size / (1024 * 1024)} MB")
            if (size > 100_000L) {
                _modelStatus.value = OnDeviceModelStatus.Downloaded
            } else {
                dest.delete()
                val msg = "File too small (${size / 1024} KB) — not a valid model. Select the correct .bin or .task file."
                log("ERROR: $msg")
                _modelStatus.value = OnDeviceModelStatus.Error(msg)
            }
        } catch (e: Exception) {
            dest.delete()
            val msg = "Import failed: ${e.message}"
            log("ERROR: $msg")
            _modelStatus.value = OnDeviceModelStatus.Error(msg)
        }
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    suspend fun explainWord(word: String): WordEntry? = withContext(Dispatchers.Default) {
        if (!isModelReady()) return@withContext null
        try {
            _modelStatus.value = OnDeviceModelStatus.Loading
            val options = com.google.mediapipe.tasks.genai.llminference.LlmInference
                .LlmInferenceOptions.builder()
                .setModelPath(modelFile().absolutePath)
                .setMaxTokens(400)
                .setTopK(40)
                .setTemperature(0.2f)
                .setRandomSeed(42)
                .build()
            val llm = com.google.mediapipe.tasks.genai.llminference.LlmInference
                .createFromOptions(context, options)
            val response = llm.generateResponse(buildPrompt(word))
            llm.close()
            _modelStatus.value = OnDeviceModelStatus.Ready
            val start = response.indexOf('{')
            val end   = response.lastIndexOf('}')
            if (start < 0 || end < 0 || end <= start) return@withContext null
            parseWordEntryFromJson(word, response.substring(start, end + 1), "on_device", gson)
        } catch (e: Exception) {
            _modelStatus.value = OnDeviceModelStatus.Error(e.message ?: "Inference failed")
            null
        }
    }

    fun deleteModel() {
        modelFile().delete()
        _downloadLogs.value = emptyList()
        _modelStatus.value = OnDeviceModelStatus.NotDownloaded
    }

    private fun buildPrompt(word: String) =
        """Explain the English word "$word". Reply with ONLY this JSON object (no markdown):
{"part_of_speech":"noun/verb/etc","meaning":"1-2 sentence definition","hindi_meaning":"Devanagari or empty string","example":"Natural example sentence","synonyms":["s1","s2"],"antonyms":["a1"],"etymology":"Brief origin or empty string"}"""
}
