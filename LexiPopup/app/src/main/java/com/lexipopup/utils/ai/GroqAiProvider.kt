package com.lexipopup.utils.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lexipopup.domain.models.BiologyData
import com.lexipopup.domain.models.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Groq Cloud API provider using llama-3.3-70b-versatile (best free Groq model).
 *
 * Requires a FREE API key from https://console.groq.com — registration takes ~2 min.
 * Free tier: 1,000 requests/day, 30 req/min.  No credit card required.
 */
class GroqAiProvider(
    okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val MODEL = "llama-3.3-70b-versatile"
        const val SIGN_UP_URL = "https://console.groq.com"
        const val DAILY_FREE_LIMIT = 1_000
    }

    private val client: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun explainWord(word: String, apiKey: String): WordEntry? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val bodyJson = gson.toJson(
                mapOf(
                    "model" to MODEL,
                    "messages" to listOf(
                        mapOf("role" to "system", "content" to "You are a dictionary assistant. Respond ONLY with a JSON object — no markdown, no code fences."),
                        mapOf("role" to "user", "content" to buildEnglishPrompt(word))
                    ),
                    "max_tokens" to 400,
                    "temperature" to 0.2
                )
            )
            val response = doRequest(bodyJson, apiKey) ?: return@withContext null
            parseWordEntryFromJson(word, response, "groq", gson)
        } catch (_: Exception) { null }
    }

    suspend fun explainBiologyTerm(term: String, apiKey: String): WordEntry? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val bodyJson = gson.toJson(
                mapOf(
                    "model" to MODEL,
                    "messages" to listOf(
                        mapOf("role" to "system", "content" to "You are a biology expert. Respond ONLY with a JSON object — no markdown, no code fences."),
                        mapOf("role" to "user", "content" to buildBiologyPrompt(term))
                    ),
                    "max_tokens" to 700,
                    "temperature" to 0.15
                )
            )
            val response = doRequest(bodyJson, apiKey) ?: return@withContext null
            parseBiologyEntryFromJson(term, response, "groq_bio", gson)
        } catch (_: Exception) { null }
    }

    private suspend fun doRequest(bodyJson: String, apiKey: String): String? {
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val root = gson.fromJson(
            response.body?.string() ?: return null,
            JsonObject::class.java
        )
        return root["choices"]?.asJsonArray?.get(0)?.asJsonObject
            ?.get("message")?.asJsonObject?.get("content")?.asString?.trim()
    }

    private fun buildEnglishPrompt(word: String) =
        """For "$word", respond with ONLY this JSON (no markdown):
{"part_of_speech":"noun/verb/adjective/etc","meaning":"1-2 sentence definition","hindi_meaning":"Devanagari script or empty string","example":"Natural example sentence using the word","synonyms":["s1","s2","s3"],"antonyms":["a1","a2"],"etymology":"Brief origin or empty string"}"""

    private fun buildBiologyPrompt(term: String) =
        """For the biology term "$term", respond with ONLY this JSON (no markdown, no code fences):
{
  "category": "Organelle/Hormone/Tissue/Organ/Process/Molecule/Cell/System/Disease/other",
  "pronunciation": "/phonetic/ or empty string",
  "definition": "Clear 1-2 sentence biology definition",
  "hindi_name": "Hindi name in Devanagari or empty string",
  "example_context": "One sentence showing it in a biological context",
  "scientific_classification": {"Domain":"", "Kingdom":"", "Phylum":"", "Class":"", "Order":"", "Family":"", "Genus":"", "Species":""},
  "functions": ["function1", "function2", "function3"],
  "structure": ["component1", "component2"],
  "related_terms": ["term1", "term2", "term3"],
  "diseases": ["disease1", "disease2"],
  "etymology": "Brief word origin or empty string",
  "synonyms": ["synonym1"],
  "difficulty_label": "Basic/Intermediate/Advanced",
  "difficulty_percent": 60,
  "frequency_percent": 50
}
Omit any field that does not apply (use empty string or empty array). scientific_classification — only fill fields that apply."""
}
