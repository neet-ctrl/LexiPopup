package com.lexipopup.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.lexipopup.domain.models.WordEntry
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

            val requestBody = gson.toJson(
                mapOf(
                    "model" to "gpt-4o-mini",
                    "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
                    "max_tokens" to 400,
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
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val root = gson.fromJson(body, JsonObject::class.java)
            val content = root["choices"]?.asJsonArray
                ?.get(0)?.asJsonObject
                ?.get("message")?.asJsonObject
                ?.get("content")?.asString ?: return@withContext null

            parseResponse(word, content.trim())
        } catch (_: Exception) {
            null
        }
    }

    private fun parseResponse(word: String, json: String): WordEntry? {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val meaning = obj["meaning"]?.asString?.takeIf { it.isNotBlank() } ?: return null
            WordEntry(
                word = word,
                partOfSpeech = obj["part_of_speech"]?.asString ?: "",
                shortMeaning = meaning,
                hindiMeaning = obj["hindi_meaning"]?.asString ?: "",
                exampleSentence = obj["example"]?.asString ?: "",
                synonyms = runCatching {
                    gson.fromJson<List<String>>(obj["synonyms"], listType)
                }.getOrDefault(emptyList()),
                antonyms = runCatching {
                    gson.fromJson<List<String>>(obj["antonyms"], listType)
                }.getOrDefault(emptyList()),
                etymology = obj["etymology"]?.asString ?: "",
                source = "ai"
            )
        } catch (_: Exception) {
            null
        }
    }
}
