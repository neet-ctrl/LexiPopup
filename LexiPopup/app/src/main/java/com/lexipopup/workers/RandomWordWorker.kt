package com.lexipopup.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lexipopup.data.local.dao.RandomWordDao
import com.lexipopup.data.local.entities.RandomWordEntity
import com.lexipopup.utils.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "RandomWordWorker"

/**
 * WorkManager worker that prefetches batches of advanced vocabulary words from the
 * configured AI provider (Groq or OpenAI) and stores them in the random_words table.
 *
 * Runs on demand when the queue drops below a threshold, and can also be scheduled
 * to refresh periodically when auto-refresh is enabled.
 */
@HiltWorker
class RandomWordWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val randomWordDao: RandomWordDao,
    private val settingsDataStore: SettingsDataStore,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsDataStore.settings.first()
        val apiKey = when (settings.randomWordProvider) {
            "openai" -> settings.openAiApiKey
            else     -> settings.groqApiKey
        }
        if (apiKey.isBlank()) {
            Log.w(TAG, "No API key configured for ${settings.randomWordProvider}. Skipping fetch.")
            return Result.success()
        }

        val unseenCount = randomWordDao.getUnseenCount()
        val target = settings.randomWordPrefetchCount
        if (unseenCount >= target) {
            Log.d(TAG, "Queue already has $unseenCount words (target $target). Nothing to do.")
            return Result.success()
        }

        val toFetch = (target - unseenCount).coerceAtLeast(3)
        val existingWords = randomWordDao.getAllWordNames().toSet()

        Log.d(TAG, "Fetching $toFetch new words (${settings.randomWordDifficulty} / ${settings.randomWordTopics})")

        val newWords = generateWords(
            apiKey    = apiKey,
            provider  = settings.randomWordProvider,
            difficulty = settings.randomWordDifficulty,
            topics     = settings.randomWordTopics,
            count      = toFetch,
            exclude    = existingWords
        )

        if (newWords.isNotEmpty()) {
            randomWordDao.insertAll(newWords)
            Log.d(TAG, "Inserted ${newWords.size} new random words")
        } else {
            Log.w(TAG, "AI returned no valid words this run")
        }

        pruneOldWords()
        return Result.success()
    }

    private suspend fun generateWords(
        apiKey: String,
        provider: String,
        difficulty: String,
        topics: String,
        count: Int,
        exclude: Set<String>
    ): List<RandomWordEntity> {
        val endpoint = if (provider == "openai")
            "https://api.openai.com/v1/chat/completions"
        else
            "https://api.groq.com/openai/v1/chat/completions"

        val model = if (provider == "openai") "gpt-4o-mini" else "llama-3.3-70b-versatile"

        val excludeNote = if (exclude.isNotEmpty())
            "\nDo NOT include any of these already-known words: ${exclude.take(30).joinToString(", ")}."
        else ""

        val topicList = topics.split(",").map { it.trim() }.filter { it.isNotBlank() }
            .joinToString(" and ").ifBlank { "general vocabulary" }

        val prompt = """Generate exactly $count advanced English vocabulary words.

Requirements:
- Difficulty: $difficulty (used in educated conversation but not commonly known by most people)
- Topics: $topicList
- Each word must be genuinely useful — not archaic or overly obscure
- Include a mix of nouns, verbs, adjectives, and adverbs$excludeNote

Return ONLY a valid JSON array (no markdown, no code fences, no explanation):
[
  {
    "word": "ameliorate",
    "teaser": "to gradually make better",
    "part_of_speech": "verb",
    "pronunciation": "/əˈmiːliəreɪt/",
    "definition": "Make (something bad or unsatisfactory) better; improve a difficult situation.",
    "example": "The new policies were designed to ameliorate the living conditions of the urban poor.",
    "etymology": "From Latin meliorare, from melior 'better'."
  }
]"""

        return try {
            val bodyJson = gson.toJson(
                mapOf(
                    "model" to model,
                    "messages" to listOf(
                        mapOf("role" to "system", "content" to "You are a vocabulary expert. Respond ONLY with valid JSON arrays. No markdown, no prose."),
                        mapOf("role" to "user", "content" to prompt)
                    ),
                    "max_tokens" to 2000,
                    "temperature" to 0.85
                )
            )

            val client = okHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(endpoint)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code}")
                return emptyList()
            }

            val root = gson.fromJson(response.body?.string() ?: return emptyList(), JsonObject::class.java)
            val content = root["choices"]?.asJsonArray?.get(0)?.asJsonObject
                ?.get("message")?.asJsonObject?.get("content")?.asString?.trim()
                ?: return emptyList()

            val cleanContent = content
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val jsonArray = JsonParser.parseString(cleanContent).asJsonArray
            val now = System.currentTimeMillis()
            jsonArray.mapIndexedNotNull { i, elem ->
                try {
                    val obj = elem.asJsonObject
                    RandomWordEntity(
                        word         = obj["word"]?.asString?.trim()?.lowercase() ?: return@mapIndexedNotNull null,
                        teaser       = obj["teaser"]?.asString?.trim() ?: "",
                        partOfSpeech = obj["part_of_speech"]?.asString?.trim() ?: "",
                        pronunciation = obj["pronunciation"]?.asString?.trim() ?: "",
                        definition   = obj["definition"]?.asString?.trim() ?: "",
                        example      = obj["example"]?.asString?.trim() ?: "",
                        etymology    = obj["etymology"]?.asString?.trim() ?: "",
                        difficulty   = difficulty,
                        topic        = topics.split(",").firstOrNull()?.trim() ?: "general",
                        provider     = provider,
                        fetchedAt    = now + i
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse word entry: $e")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateWords failed: $e")
            emptyList()
        }
    }

    private suspend fun pruneOldWords() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        randomWordDao.pruneOldDiscovered(thirtyDaysAgo)
    }

    companion object {
        const val WORK_NAME_PREFETCH = "random_word_prefetch"
        const val WORK_NAME_PERIODIC = "random_word_periodic"

        fun scheduleOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<RandomWordWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_PREFETCH, ExistingWorkPolicy.KEEP, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RandomWordWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }
}
