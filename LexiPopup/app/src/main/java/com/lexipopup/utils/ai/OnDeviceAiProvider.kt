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
 * On-device AI provider using llama.cpp (via llama.android JNI wrapper).
 *
 * Supports any standard GGUF model from HuggingFace. MediaPipe was removed because it only
 * supports Google's proprietary .task format from Kaggle and fails on all HuggingFace GGUF files.
 *
 * Model download URLs (public HuggingFace — no login required):
 *   Gemma 1.1 2B IT Q4_K_M (1.5 GB):
 *     https://huggingface.co/bartowski/gemma-1.1-2b-it-GGUF/resolve/main/gemma-1.1-2b-it-Q4_K_M.gguf
 *   Phi-2 Q4_K_M (1.7 GB):
 *     https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf
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

    /** Stores the most recent inference exception message so callers can surface it in chat. */
    var lastInferenceError: String? = null
        private set

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

        // ── Pre-check: already fully downloaded? ─────────────────────────────
        // Allow up to 15 MB slack for gzip/header overhead differences.
        val expectedBytes = (model.sizeGb * 1024L * 1024L * 1024L).toLong()
        if (existingBytes > 0L && existingBytes >= expectedBytes - 15_000_000L) {
            log("✓ File already fully downloaded (${existingBytes / (1024 * 1024)} MB — expected ~${expectedBytes / (1024 * 1024)} MB)")
            _modelStatus.value = OnDeviceModelStatus.Downloaded
            return@withContext
        }

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
                // 416 = Range Not Satisfiable: our resume offset is >= server file size,
                // which means the local file already contains the full content.
                416 -> {
                    response.close()
                    if (existingBytes > 100_000L) {
                        log("HTTP 416: range start is beyond file end — local copy is already complete (${existingBytes / (1024 * 1024)} MB)")
                        _modelStatus.value = OnDeviceModelStatus.Downloaded
                    } else {
                        val msg = "HTTP 416 — server rejected range request (file may have moved or changed)."
                        log("ERROR: $msg")
                        _modelStatus.value = OnDeviceModelStatus.Error(msg)
                    }
                    return@withContext
                }
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

        // Bug fix: query the file size first so we can show real progress (not just indeterminate)
        val fileSize: Long = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else -1L
            } ?: -1L
        } catch (_: Exception) { -1L }

        if (fileSize > 0) log("File size: ${fileSize / (1024 * 1024)} MB")

        try {
            _modelStatus.value = OnDeviceModelStatus.Downloading(0f, 0L, fileSize)
            context.contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    var received = 0L
                    var lastLoggedMb = 0L
                    while (inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        received += n
                        val progress = if (fileSize > 0) received.toFloat() / fileSize else 0f
                        _modelStatus.value = OnDeviceModelStatus.Downloading(progress, received, fileSize)
                        val mb = received / (1024 * 1024)
                        if (mb >= lastLoggedMb + 100) {
                            log("Copied ${mb} MB${if (fileSize > 0) " / ${fileSize / (1024 * 1024)} MB (${(progress * 100).toInt()}%)" else ""}…")
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
                val msg = "File too small (${size / 1024} KB) — not a valid model. Select the .gguf file you downloaded."
                log("ERROR: $msg")
                _modelStatus.value = OnDeviceModelStatus.Error(msg)
            }
        } catch (e: Exception) {
            // Bug fix: keep partial file on exception (same as URL download) so a re-import
            // only needs to re-copy from the user's file, not re-download anything.
            val savedMb = if (dest.exists()) dest.length() / (1024 * 1024) else 0L
            val msg = "Import failed: ${e.message}${if (savedMb > 0) " (${savedMb} MB partially copied — try again)" else ""}"
            log("ERROR: $msg")
            _modelStatus.value = OnDeviceModelStatus.Error(msg)
        }
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    /**
     * Low-level text generation via llama.cpp (replaces MediaPipe which only supports
     * Google's proprietary .task format and fails on all HuggingFace GGUF files).
     *
     * Errors are logged into [downloadLogs] (visible in AI Settings) and surfaced
     * via [modelStatus] so users can see what went wrong rather than a silent failure.
     */
    suspend fun generateText(prompt: String, maxTokens: Int = 600): String? =
        withContext(Dispatchers.IO) {
            if (!isModelReady()) return@withContext null
            try {
                _modelStatus.value = OnDeviceModelStatus.Loading
                // LlamaJni calls into libllama_jni.so — built from llama.cpp source via NDK.
                // Returns "ERROR: ..." on failure, plain text on success.
                val raw = LlamaJni.runInferenceNative(
                    modelFile().absolutePath, prompt, maxTokens
                )
                if (raw.startsWith("ERROR:")) {
                    log("INFERENCE ERROR: $raw")
                    lastInferenceError = raw
                    _modelStatus.value = OnDeviceModelStatus.Downloaded
                    null
                } else {
                    _modelStatus.value = OnDeviceModelStatus.Ready
                    raw.trim()
                }
            } catch (e: Exception) {
                val detail = "${e.javaClass.simpleName}: ${e.message}"
                log("INFERENCE ERROR: $detail")
                lastInferenceError = detail
                _modelStatus.value = OnDeviceModelStatus.Downloaded
                null
            }
        }

    suspend fun explainWord(word: String): WordEntry? = withContext(Dispatchers.Default) {
        val response = generateText(buildWordPrompt(word), maxTokens = 400) ?: return@withContext null
        val start = response.indexOf('{')
        val end   = response.lastIndexOf('}')
        if (start < 0 || end < 0 || end <= start) return@withContext null
        parseWordEntryFromJson(word, response.substring(start, end + 1), "on_device", gson)
    }

    /**
     * Multi-turn free-form chat.  Formats the conversation history into a simple
     * Instruct/Output prompt that works for both Phi-2 and Gemma instruction-tuned models.
     */
    suspend fun chat(messages: List<ChatMessage>): String? = withContext(Dispatchers.Default) {
        val prompt = buildChatPrompt(messages)
        // Log prompt length for debugging context-window issues.
        log("Chat prompt: ${prompt.length} chars → sending to model…")
        val raw = generateText(prompt, maxTokens = 512) ?: return@withContext null
        // Strip any repeated prompt echo or "Assistant:" prefix that leaks into output
        val stripped = raw.removePrefix("Assistant:").trimStart()
        // Cut off at a second "User:" or "Human:" turn if the model generates extra
        val cutOff = Regex("(?m)^(User|Human):\\s").find(stripped)?.range?.first ?: stripped.length
        stripped.substring(0, cutOff).trim()
    }

    fun deleteModel() {
        modelFile().delete()
        _downloadLogs.value = emptyList()
        _modelStatus.value = OnDeviceModelStatus.NotDownloaded
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    private fun buildWordPrompt(word: String) =
        """Explain the English word "$word". Reply with ONLY this JSON object (no markdown):
{"part_of_speech":"noun/verb/etc","meaning":"1-2 sentence definition","hindi_meaning":"Devanagari or empty string","example":"Natural example sentence","synonyms":["s1","s2"],"antonyms":["a1"],"etymology":"Brief origin or empty string"}"""

    private fun buildChatPrompt(messages: List<ChatMessage>): String = buildString {
        // Context-window budget for on-device models:
        //   Phi-2  — 2048 tokens total.  With maxTokens=512 output, input budget ≈ 1536 tokens ≈ 6000 chars.
        //   Gemma 2B — 8192 tokens total.  Much more headroom.
        // We use 3500 chars as the safe hard cap (fits both models with room to spare).
        val PROMPT_CHAR_LIMIT = 3500

        // 1. System context — very short, always included first.
        val sys = messages.firstOrNull { it.role == "system" }
        if (sys != null) {
            append("Context: ${sys.content.take(300)}\n\n")
        }

        // 2. Conversation turns — newest turns take priority.
        //    We build in reverse so we always keep the latest exchange, then prepend
        //    older turns as space allows.
        val turns = messages.filter { it.role != "system" }
        val suffix = "Assistant:"
        val reserved = length + suffix.length + 4   // existing header + suffix
        val budget = PROMPT_CHAR_LIMIT - reserved

        // Collect lines from newest → oldest, then reverse to chronological order.
        val lines = mutableListOf<String>()
        var used = 0
        for (msg in turns.asReversed()) {
            val line = when (msg.role) {
                "user"      -> "User: ${msg.content}"
                "assistant" -> "Assistant: ${msg.content}"
                else        -> continue
            }
            // Truncate very long individual messages (e.g. file-attachment text)
            val truncated = if (line.length > 1200) line.take(1200) + "…" else line
            if (used + truncated.length + 1 > budget) break
            lines.add(0, truncated)
            used += truncated.length + 1
        }
        lines.forEach { append(it); append('\n') }
        append(suffix)
    }

    data class ChatMessage(val role: String, val content: String)
}
