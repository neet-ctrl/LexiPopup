package com.lexipopup.utils.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
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
 *
 * NOTE: Despite community misconceptions, Groq does NOT have a no-auth public endpoint.
 * Every request must carry a Bearer token; requests without one return HTTP 401.
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
                        mapOf("role" to "user", "content" to buildPrompt(word))
                    ),
                    "max_tokens" to 400,
                    "temperature" to 0.2
                )
            )
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val root = gson.fromJson(
                response.body?.string() ?: return@withContext null,
                JsonObject::class.java
            )
            val content = root["choices"]?.asJsonArray?.get(0)?.asJsonObject
                ?.get("message")?.asJsonObject?.get("content")?.asString?.trim()
                ?: return@withContext null

            parseWordEntryFromJson(word, content, "groq", gson)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPrompt(word: String) =
        """For "$word", respond with ONLY this JSON (no markdown):
{"part_of_speech":"noun/verb/adjective/etc","meaning":"1-2 sentence definition","hindi_meaning":"Devanagari script or empty string","example":"Natural example sentence using the word","synonyms":["s1","s2","s3"],"antonyms":["a1","a2"],"etymology":"Brief origin or empty string"}"""
}
