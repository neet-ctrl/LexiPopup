package com.lexipopup.utils.ai

import android.content.Context
import com.google.gson.Gson
import com.lexipopup.domain.models.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * On-device AI provider using MediaPipe LLM Inference Task.
 *
 * Model files are downloaded from Google's official public MediaPipe model bucket
 * (storage.googleapis.com/mediapipe-models) — no auth or account required.
 *
 * IMPORTANT: A dedicated OkHttpClient with long timeouts is used for model downloads.
 * The shared app OkHttpClient has a 5s read timeout which would kill any large download.
 *
 * Supported model formats: .bin (flat TFLite) and .task (MediaPipe bundle).
 * Works fully offline once the model is downloaded.
 */
class OnDeviceAiProvider(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val modelsDir = File(context.filesDir, "ai_models").also { it.mkdirs() }

    private val _modelStatus = MutableStateFlow<OnDeviceModelStatus>(OnDeviceModelStatus.NotDownloaded)
    val modelStatus: StateFlow<OnDeviceModelStatus> = _modelStatus.asStateFlow()

    var selectedModel: OnDeviceModel = OnDeviceModel.TINY
        private set

    // Dedicated download client — long timeouts for 900MB–1.6GB model files.
    // connectTimeout: 30s to handle cold-start server delays.
    // readTimeout:    0  = no timeout; Google Storage streams steadily so we never
    //                     want to cut the connection just because a chunk was slow.
    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    init {
        refreshStatus()
    }

    fun selectModel(model: OnDeviceModel) {
        selectedModel = model
        refreshStatus()
    }

    fun refreshStatus() {
        val f = modelFile()
        _modelStatus.value = if (f.exists() && f.length() > 100_000L) {
            OnDeviceModelStatus.Downloaded
        } else {
            OnDeviceModelStatus.NotDownloaded
        }
    }

    /** Returns the file where the model is stored on disk.
     *  Uses model.fileName (which preserves the correct extension for MediaPipe). */
    fun modelFile(model: OnDeviceModel = selectedModel) = File(modelsDir, model.fileName)

    fun isModelReady(): Boolean {
        val f = modelFile()
        return f.exists() && f.length() > 100_000L
    }

    suspend fun downloadModel(
        model: OnDeviceModel = selectedModel,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        selectedModel = model
        val dest = modelFile(model)
        _modelStatus.value = OnDeviceModelStatus.Downloading(0f, 0L, 0L)

        // Resume support — Range header if a partial file already exists
        val existingBytes = if (dest.exists()) dest.length() else 0L
        val requestBuilder = Request.Builder().url(model.downloadUrl)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        try {
            val response = downloadClient.newCall(requestBuilder.build()).execute()

            when (response.code) {
                200, 206 -> { /* OK or Partial Content — proceed */ }
                401, 403 -> {
                    _modelStatus.value = OnDeviceModelStatus.Error(
                        "HTTP ${response.code} — model requires authentication. " +
                        "Visit ai.google.dev/edge to download manually."
                    )
                    response.close()
                    return@withContext
                }
                404 -> {
                    _modelStatus.value = OnDeviceModelStatus.Error(
                        "HTTP 404 — model file not found at this URL. " +
                        "Update the app to get the correct download link."
                    )
                    response.close()
                    return@withContext
                }
                else -> {
                    _modelStatus.value = OnDeviceModelStatus.Error("HTTP ${response.code} — download failed")
                    response.close()
                    return@withContext
                }
            }

            val body = response.body ?: run {
                _modelStatus.value = OnDeviceModelStatus.Error("Empty response body")
                return@withContext
            }

            val appendMode   = response.code == 206 && existingBytes > 0
            var received     = if (appendMode) existingBytes else 0L
            val serverLength = body.contentLength()
            val total        = if (appendMode) existingBytes + serverLength else serverLength

            FileOutputStream(dest, appendMode).use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(64 * 1024)   // 64 KB chunks
                    var n: Int
                    while (inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        received += n
                        val pct = if (total > 0) received.toFloat() / total else 0f
                        _modelStatus.value = OnDeviceModelStatus.Downloading(pct, received, total)
                        onProgress(pct)
                    }
                }
            }
            _modelStatus.value = OnDeviceModelStatus.Downloaded
        } catch (e: Exception) {
            dest.delete()
            _modelStatus.value = OnDeviceModelStatus.Error(
                when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Connection timed out. Check your internet connection and retry."
                    e.message?.contains("ECONNREFUSED") == true ->
                        "Connection refused. Check your internet connection."
                    else -> e.message ?: "Download failed"
                }
            )
        }
    }

    /**
     * Run inference using MediaPipe LLM Inference Task.
     * Returns null if model is not downloaded or inference fails.
     */
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
            val end = response.lastIndexOf('}')
            if (start < 0 || end < 0 || end <= start) return@withContext null
            parseWordEntryFromJson(word, response.substring(start, end + 1), "on_device", gson)
        } catch (e: Exception) {
            _modelStatus.value = OnDeviceModelStatus.Error(e.message ?: "Inference failed")
            null
        }
    }

    fun deleteModel() {
        modelFile().delete()
        _modelStatus.value = OnDeviceModelStatus.NotDownloaded
    }

    private fun buildPrompt(word: String) =
        """Explain the English word "$word". Reply with ONLY this JSON object (no markdown):
{"part_of_speech":"noun/verb/etc","meaning":"1-2 sentence definition","hindi_meaning":"Devanagari or empty string","example":"Natural example sentence","synonyms":["s1","s2"],"antonyms":["a1"],"etymology":"Brief origin or empty string"}"""
}
