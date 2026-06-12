package com.lexipopup.utils.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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

// ── Result type ──────────────────────────────────────────────────────────────

sealed class ChatApiResult {
    data class Success(val content: String, val tokensUsed: Int = 0) : ChatApiResult()
    data class RateLimit(val retryAfterSeconds: Int = 60, val provider: String = "") : ChatApiResult()
    data class NoKey(val provider: String) : ChatApiResult()
    data class Error(val message: String, val code: Int = -1) : ChatApiResult()
}

// ── Client ───────────────────────────────────────────────────────────────────

@Singleton
class AiChatClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    data class Message(val role: String, val content: String)

    private val TAG = "AiChatClient"

    private val client: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json".toMediaType()

    // ── Multi-turn chat ───────────────────────────────────────────────────────

    suspend fun chat(
        messages: List<Message>,
        provider: AiProviderType,
        groqApiKey: String,
        openAiApiKey: String
    ): ChatApiResult = withContext(Dispatchers.IO) {
        when (provider) {
            AiProviderType.GROQ -> {
                if (groqApiKey.isBlank()) ChatApiResult.NoKey("Groq")
                else groqChat(messages, groqApiKey)
            }
            AiProviderType.OPENAI -> {
                if (openAiApiKey.isBlank()) ChatApiResult.NoKey("OpenAI")
                else openAiChat(messages, openAiApiKey)
            }
            AiProviderType.HYBRID -> {
                when {
                    groqApiKey.isNotBlank() -> {
                        val r = groqChat(messages, groqApiKey)
                        if (r is ChatApiResult.Success) r
                        else if (openAiApiKey.isNotBlank()) openAiChat(messages, openAiApiKey)
                        else r
                    }
                    openAiApiKey.isNotBlank() -> openAiChat(messages, openAiApiKey)
                    else -> ChatApiResult.NoKey("Groq or OpenAI")
                }
            }
            AiProviderType.ON_DEVICE -> {
                ChatApiResult.Error("On-device AI doesn't support free-form chat yet.\nSwitch to Groq (free) or OpenAI in provider selector.")
            }
        }
    }

    private fun groqChat(messages: List<Message>, apiKey: String): ChatApiResult {
        val body = buildBody(messages, "llama-3.3-70b-versatile", maxTokens = 2048, temp = 0.7)
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(body.toRequestBody(JSON_TYPE))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        return execute(request, "Groq")
    }

    private fun openAiChat(messages: List<Message>, apiKey: String): ChatApiResult {
        val body = buildBody(messages, "gpt-4o-mini", maxTokens = 2048, temp = 0.7)
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toRequestBody(JSON_TYPE))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        return execute(request, "OpenAI")
    }

    private fun buildBody(messages: List<Message>, model: String, maxTokens: Int, temp: Double): String =
        gson.toJson(mapOf(
            "model" to model,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "max_tokens" to maxTokens,
            "temperature" to temp
        ))

    private fun execute(request: Request, providerName: String): ChatApiResult {
        return try {
            val response = client.newCall(request).execute()
            when (response.code) {
                429 -> {
                    val retry = response.header("Retry-After")?.toIntOrNull() ?: 60
                    Log.w(TAG, "$providerName rate limit hit, retry in ${retry}s")
                    ChatApiResult.RateLimit(retry, providerName)
                }
                401 -> ChatApiResult.Error("Invalid $providerName API key. Check Settings → AI Settings.", 401)
                403 -> ChatApiResult.Error("$providerName access forbidden. Check your account plan.", 403)
                in 200..299 -> {
                    val bodyStr = response.body?.string()
                        ?: return ChatApiResult.Error("Empty response from $providerName")
                    val root = gson.fromJson(bodyStr, JsonObject::class.java)
                    val content = root["choices"]?.asJsonArray?.get(0)?.asJsonObject
                        ?.get("message")?.asJsonObject?.get("content")?.asString?.trim()
                        ?: return ChatApiResult.Error("Could not parse $providerName response")
                    val tokens = root["usage"]?.asJsonObject?.get("total_tokens")?.asInt ?: 0
                    ChatApiResult.Success(content, tokens)
                }
                else -> ChatApiResult.Error("$providerName error ${response.code}", response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error for $providerName", e)
            ChatApiResult.Error(e.message ?: "Network error")
        }
    }

    // ── Vocabulary extraction ─────────────────────────────────────────────────

    suspend fun extractVocabulary(
        conversationText: String,
        provider: AiProviderType,
        groqApiKey: String,
        openAiApiKey: String
    ): List<WordEntry> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("From this conversation, extract all valuable English vocabulary words worth learning ")
            append("(skip very common words: the, is, it, I, a, and, to, of, in, for, on, with, he, she, they, we, be, have, do, etc.).\n")
            append("For each word return a JSON object. Return ONLY a valid JSON array, no markdown:\n")
            append("""[{"word":"...","part_of_speech":"noun/verb/adj/adv","meaning":"...","hindi_meaning":"...Devanagari or empty","example":"...","synonyms":["s1","s2"],"antonyms":["a1"],"etymology":"...or empty","difficulty_level":2,"frequency_rating":50}]""")
            append("\n\nConversation:\n")
            append(conversationText.take(7000))
        }

        val extractMessages = listOf(
            Message("system", "You are a vocabulary extractor. Output ONLY a JSON array. No explanation, no markdown."),
            Message("user", prompt)
        )

        val result = chat(extractMessages, provider, groqApiKey, openAiApiKey)
        if (result !is ChatApiResult.Success) return@withContext emptyList()

        parseVocabJson(result.content)
    }

    private fun parseVocabJson(json: String): List<WordEntry> = try {
        val clean = json.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = gson.fromJson(clean, JsonArray::class.java)
        arr.mapNotNull { el ->
            try {
                val obj = el.asJsonObject
                val word = obj["word"]?.asString?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                WordEntry(
                    word = word,
                    partOfSpeech  = obj["part_of_speech"]?.asString ?: "",
                    shortMeaning  = obj["meaning"]?.asString ?: "",
                    hindiMeaning  = obj["hindi_meaning"]?.asString ?: "",
                    exampleSentence = obj["example"]?.asString ?: "",
                    synonyms = runCatching {
                        obj["synonyms"]?.asJsonArray?.map { it.asString } ?: emptyList()
                    }.getOrDefault(emptyList()),
                    antonyms = runCatching {
                        obj["antonyms"]?.asJsonArray?.map { it.asString } ?: emptyList()
                    }.getOrDefault(emptyList()),
                    etymology     = obj["etymology"]?.asString ?: "",
                    difficultyLevel  = obj["difficulty_level"]?.asInt ?: 2,
                    frequencyRating  = obj["frequency_rating"]?.asInt ?: 50,
                    source        = "chat"
                )
            } catch (_: Exception) { null }
        }
    } catch (e: Exception) {
        Log.e("AiChatClient", "Failed to parse vocab JSON", e)
        emptyList()
    }
}
