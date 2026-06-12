package com.lexipopup.utils.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.lexipopup.domain.models.WordEntry

// ── Provider selection ──────────────────────────────────────────────────────

enum class AiProviderType(val id: String, val label: String) {
    GROQ("groq", "Groq Cloud"),
    OPENAI("openai", "OpenAI"),
    ON_DEVICE("on_device", "On-Device AI"),
    HYBRID("hybrid", "Hybrid (Both)");

    companion object {
        fun fromId(id: String): AiProviderType = values().find { it.id == id } ?: GROQ
    }
}

// ── Hybrid result ───────────────────────────────────────────────────────────

data class HybridAiResult(
    val groqEntry: WordEntry?,
    val onDeviceEntry: WordEntry?
) {
    val bestEntry: WordEntry? get() = groqEntry ?: onDeviceEntry
}

// ── On-device model status ──────────────────────────────────────────────────

sealed class OnDeviceModelStatus {
    object NotDownloaded : OnDeviceModelStatus()
    data class Downloading(val progress: Float, val bytesReceived: Long, val totalBytes: Long) : OnDeviceModelStatus()
    object Downloaded : OnDeviceModelStatus()
    object Loading : OnDeviceModelStatus()
    object Ready : OnDeviceModelStatus()
    data class Error(val message: String) : OnDeviceModelStatus()
}

// ── Available on-device models ──────────────────────────────────────────────

data class OnDeviceModel(
    val id: String,
    val displayName: String,
    val sizeGb: Float,
    val ramRequiredGb: Float,
    val qualityPercent: Int,
    val fileName: String,     // actual filename saved to disk (extension matters for MediaPipe)
    val downloadUrl: String
) {
    companion object {
        // Both URLs point to Google's official public MediaPipe model bucket —
        // no authentication required, no HuggingFace account needed.
        val TINY = OnDeviceModel(
            id              = "gemma-2b-tiny",
            displayName     = "Gemma 2B Tiny (900 MB)",
            sizeGb          = 0.9f,
            ramRequiredGb   = 3f,
            qualityPercent  = 72,
            fileName        = "gemma-2b-it-cpu-int4.bin",
            downloadUrl     = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-cpu-int4/float32/1/gemma-2b-it-cpu-int4.bin"
        )
        val STANDARD = OnDeviceModel(
            id              = "phi2-standard",
            displayName     = "Phi-2 Standard (1.6 GB)",
            sizeGb          = 1.6f,
            ramRequiredGb   = 4f,
            qualityPercent  = 82,
            fileName        = "phi-2-default-cpu.task",
            downloadUrl     = "https://storage.googleapis.com/mediapipe-models/llm_inference/phi-2-default-cpu/float32/1/phi-2-default-cpu.task"
        )
        val ALL = listOf(TINY, STANDARD)
    }
}

// ── Shared JSON → WordEntry parser ──────────────────────────────────────────

private val listType = TypeToken.getParameterized(List::class.java, String::class.java).type

fun parseWordEntryFromJson(word: String, json: String, source: String, gson: Gson): WordEntry? = try {
    val clean = json.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()
    val obj = gson.fromJson(clean, JsonObject::class.java)
    val meaning = obj["meaning"]?.asString?.takeIf { it.isNotBlank() } ?: return null
    WordEntry(
        word = word,
        partOfSpeech = obj["part_of_speech"]?.asString ?: "",
        shortMeaning = meaning,
        hindiMeaning = obj["hindi_meaning"]?.asString ?: "",
        exampleSentence = obj["example"]?.asString ?: "",
        synonyms = runCatching { gson.fromJson<List<String>>(obj["synonyms"], listType) }.getOrDefault(emptyList()),
        antonyms = runCatching { gson.fromJson<List<String>>(obj["antonyms"], listType) }.getOrDefault(emptyList()),
        etymology = obj["etymology"]?.asString ?: "",
        source = source
    )
} catch (_: Exception) { null }
