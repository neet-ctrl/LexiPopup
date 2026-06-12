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

/**
 * On-device AI provider using MediaPipe LLM Inference Task.
 *
 * Supported model formats: .task files (Gemma 2B, Phi-2).
 * Model download is managed here via OkHttp; no Play Store dependency.
 *
 * Inference requires 3–6 GB RAM depending on model; uses CPU backend.
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

    fun modelFile(model: OnDeviceModel = selectedModel) = File(modelsDir, "${model.id}.task")

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
        try {
            val response = okHttpClient.newCall(
                Request.Builder().url(model.downloadUrl).build()
            ).execute()
            if (!response.isSuccessful) {
                _modelStatus.value = OnDeviceModelStatus.Error("HTTP ${response.code}")
                return@withContext
            }
            val body = response.body ?: run {
                _modelStatus.value = OnDeviceModelStatus.Error("Empty response body")
                return@withContext
            }
            val total = body.contentLength()
            var received = 0L
            FileOutputStream(dest).use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(8_192)
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
            _modelStatus.value = OnDeviceModelStatus.Error(e.message ?: "Download failed")
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
            // Extract the JSON object from possible preamble text
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
