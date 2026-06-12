package com.lexipopup.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.utils.ai.parseBiologyEntryFromJson
import com.lexipopup.utils.ai.parseWordEntryFromJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiExplanationHelper @Inject constructor(
    okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val endpoint = "https://api.openai.com/v1/chat/completions"
    private val listType = TypeToken.getParameterized(List::class.java, String::class.java).type

    private val aiClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun explainWord(word: String, apiKey: String): WordEntry? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val prompt = """You are a dictionary. For the word "$word", respond ONLY with a JSON object (no markdown, no code fences) with exactly these fields:
{
  "part_of_speech": "noun/verb/adjective/adverb/etc",
  "meaning": "A concise definition in 1-2 sentences.",
  "hindi_meaning": "Hindi translation in Devanagari script if known, else empty string",
  "example": "A natural example sentence using the word.",
  "synonyms": ["syn1", "syn2", "syn3"],
  "antonyms": ["ant1", "ant2"],
  "etymology": "Brief word origin, e.g. Latin/Greek root, or empty string if unknown."
}"""
            val content = callOpenAI(prompt, apiKey, maxTokens = 400) ?: return@withContext null
            parseWordEntryFromJson(word, content, "openai", gson)
        } catch (_: Exception) { null }
    }

    suspend fun explainBiologyTerm(term: String, apiKey: String): WordEntry? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val prompt = """You are a biology expert. For the biology term "$term", respond ONLY with this JSON (no markdown, no code fences):
{
  "category": "Organelle/Hormone/Tissue/Organ/Process/Molecule/Cell/System/Disease/other",
  "pronunciation": "/phonetic/ or empty string",
  "definition": "Clear 1-2 sentence biology definition",
  "hindi_name": "Hindi name in Devanagari or empty string",
  "example_context": "One sentence showing it in a biological context",
  "scientific_classification": {"Domain":"","Kingdom":"","Phylum":"","Class":"","Order":"","Family":"","Genus":"","Species":""},
  "functions": ["function1","function2"],
  "structure": ["component1","component2"],
  "related_terms": ["term1","term2"],
  "diseases": ["disease1","disease2"],
  "etymology": "Brief word origin or empty string",
  "synonyms": ["synonym1"],
  "difficulty_label": "Basic/Intermediate/Advanced",
  "difficulty_percent": 60,
  "frequency_percent": 50
}"""
            val content = callOpenAI(prompt, apiKey, maxTokens = 700) ?: return@withContext null
            parseBiologyEntryFromJson(term, content, "openai_bio", gson)
        } catch (_: Exception) { null }
    }

    private suspend fun callOpenAI(prompt: String, apiKey: String, maxTokens: Int): String? {
        val requestBody = gson.toJson(
            mapOf(
                "model" to "gpt-4o-mini",
                "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
                "max_tokens" to maxTokens,
                "temperature" to 0.2
            )
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = aiClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val root = gson.fromJson(body, JsonObject::class.java)
        return root["choices"]?.asJsonArray
            ?.get(0)?.asJsonObject
            ?.get("message")?.asJsonObject
            ?.get("content")?.asString?.trim()
    }
}
