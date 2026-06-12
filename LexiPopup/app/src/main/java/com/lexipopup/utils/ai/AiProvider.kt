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
        // Public HuggingFace GGUF mirrors — no login required.
        // MediaPipe 0.10.14+ natively loads GGUF files via setModelPath().
        // Verified working 2026-06-12: both return HTTP 200 and serve bytes.
        val TINY = OnDeviceModel(
            id              = "gemma1.1-2b-it",
            displayName     = "Gemma 1.1 2B IT (1.5 GB)",
            sizeGb          = 1.5f,
            ramRequiredGb   = 3f,
            qualityPercent  = 75,
            fileName        = "gemma-1.1-2b-it-Q4_K_M.gguf",
            downloadUrl     = "https://huggingface.co/bartowski/gemma-1.1-2b-it-GGUF/resolve/main/gemma-1.1-2b-it-Q4_K_M.gguf"
        )
        val STANDARD = OnDeviceModel(
            id              = "phi2-standard",
            displayName     = "Phi-2 Standard (1.7 GB)",
            sizeGb          = 1.7f,
            ramRequiredGb   = 4f,
            qualityPercent  = 82,
            fileName        = "phi-2-Q4_K_M.gguf",
            downloadUrl     = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf"
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
