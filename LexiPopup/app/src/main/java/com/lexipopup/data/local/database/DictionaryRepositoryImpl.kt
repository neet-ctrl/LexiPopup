package com.lexipopup.data.local.database

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.entities.WordEntity
import com.lexipopup.data.remote.api.DictionaryApi
import com.lexipopup.data.remote.dto.FreeDictionaryResponse
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val api: DictionaryApi,
    private val gson: Gson
) : DictionaryRepository {

    override suspend fun lookupWord(word: String): WordEntry? {
        val local = wordDao.findWord(word)
        if (local != null) {
            wordDao.updateAccess(word)
            return local.toDomain(gson)
        }
        return try {
            val remote = api.getDefinition(word)
            val entry = remote.firstOrNull()?.toWordEntry(word) ?: return null
            saveToCache(entry)
            entry
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun searchSuggestions(query: String, limit: Int): List<String> =
        wordDao.getSuggestions(query, limit)

    override suspend fun searchWords(query: String, limit: Int): List<WordEntry> =
        wordDao.searchWords(query, limit).map { it.toDomain(gson) }

    override suspend fun saveToCache(entry: WordEntry) {
        wordDao.insertWord(entry.toEntity(gson))
    }

    override suspend fun toggleFavorite(word: String) = wordDao.toggleFavorite(word)

    override suspend fun saveNote(word: String, note: String) = wordDao.updateNote(word, note)

    override fun getFavorites(): Flow<List<WordEntry>> =
        wordDao.getFavorites().map { list -> list.map { it.toDomain(gson) } }

    override fun getRecentWords(limit: Int): Flow<List<WordEntry>> =
        wordDao.getRecentWords(limit).map { list -> list.map { it.toDomain(gson) } }

    override fun getTotalWordCount(): Flow<Int> = wordDao.getTotalCountFlow()

    override suspend fun getWordsByLetter(
        letter: String,
        limit: Int,
        offset: Int,
        sortBy: String,
        pos: String
    ): List<WordEntry> = wordDao.getWordsByLetter(letter, limit, offset, sortBy, pos)
        .map { it.toDomain(gson) }

    override suspend fun countByLetter(letter: String): Int =
        wordDao.countByLetter(letter)

    override suspend fun getWordOfDay(mode: String, userLevel: Int): WordEntry? {
        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)

        // Map mode/level → difficulty range
        val (minDiff, maxDiff) = when (mode) {
            "personalized" -> when (userLevel) {
                1    -> 1 to 1   // Beginner only
                2    -> 2 to 2   // Intermediate only
                3    -> 3 to 3   // Advanced only
                4    -> 3 to 4   // Advanced + Expert
                else -> 2 to 3
            }
            else -> 2 to 3       // global: Intermediate + Advanced
        }

        // Random mode — fresh pick every call
        if (mode == "random") {
            return wordDao.getRandomWordOfDayCandidate(30, 70, minDiff, maxDiff)?.toDomain(gson)
                ?: wordDao.getRandomWord()?.toDomain(gson)
        }

        // Global / Personalized — deterministic date-seed
        val strictCount = wordDao.getWordOfDayCandidateCount(30, 70, minDiff, maxDiff)
        if (strictCount > 0) {
            val offset = dayOfYear % strictCount
            wordDao.getWordOfDayCandidateAt(offset, 30, 70, minDiff, maxDiff)?.toDomain(gson)
                ?.let { return it }
        }

        // Fallback 1: relax frequency filter (keep length + difficulty + content)
        val relaxedCount = wordDao.getWordOfDayCandidateCount(0, 100, minDiff, maxDiff)
        if (relaxedCount > 0) {
            val offset = dayOfYear % relaxedCount
            wordDao.getWordOfDayCandidateAt(offset, 0, 100, minDiff, maxDiff)?.toDomain(gson)
                ?.let { return it }
        }

        // Fallback 2: any word at day-seeded offset
        val total = wordDao.getTotalCount()
        if (total == 0) return null
        return wordDao.getWordAtOffset(dayOfYear % total)?.toDomain(gson)
            ?: wordDao.getRandomWord()?.toDomain(gson)
    }

    override suspend fun getDifficultyDistribution(): Map<Int, Int> =
        wordDao.getDifficultyDistribution().associate { it.difficultyLevel to it.count }
}

private val STRING_LIST_TYPE = TypeToken.getParameterized(List::class.java, String::class.java).type

fun WordEntity.toDomain(gson: Gson): WordEntry {
    return WordEntry(
        word = word,
        pronunciation = pronunciation,
        partOfSpeech = partOfSpeech,
        shortMeaning = shortMeaning,
        detailedMeaning = detailedMeaning,
        hindiMeaning = hindiMeaning,
        hindiPronunciation = hindiPronunciation,
        exampleSentence = exampleSentence,
        synonyms = runCatching { gson.fromJson<List<String>>(synonyms, STRING_LIST_TYPE) }.getOrDefault(emptyList()),
        antonyms = runCatching { gson.fromJson<List<String>>(antonyms, STRING_LIST_TYPE) }.getOrDefault(emptyList()),
        etymology = etymology,
        difficultyLevel = difficultyLevel,
        frequencyRating = frequencyRating,
        source = source,
        isFavorite = isFavorite,
        userNote = userNote
    )
}

fun WordEntry.toEntity(gson: Gson) = WordEntity(
    word = word,
    pronunciation = pronunciation,
    partOfSpeech = partOfSpeech,
    shortMeaning = shortMeaning,
    detailedMeaning = detailedMeaning,
    hindiMeaning = hindiMeaning,
    hindiPronunciation = hindiPronunciation,
    exampleSentence = exampleSentence,
    synonyms = gson.toJson(synonyms),
    antonyms = gson.toJson(antonyms),
    etymology = etymology,
    difficultyLevel = difficultyLevel,
    frequencyRating = frequencyRating,
    source = source,
    isFavorite = isFavorite,
    userNote = userNote
)

fun FreeDictionaryResponse.toWordEntry(word: String): WordEntry {
    val firstMeaning = meanings.firstOrNull()
    val firstDef = firstMeaning?.definitions?.firstOrNull()
    val allSynonyms = meanings.flatMap { it.synonyms } +
            meanings.flatMap { it.definitions.flatMap { d -> d.synonyms } }
    val allAntonyms = meanings.flatMap { it.antonyms } +
            meanings.flatMap { it.definitions.flatMap { d -> d.antonyms } }
    val ipa = phonetics.firstOrNull { it.text != null }?.text ?: phonetic ?: ""

    return WordEntry(
        word = word,
        pronunciation = ipa,
        partOfSpeech = firstMeaning?.partOfSpeech ?: "",
        shortMeaning = firstDef?.definition ?: "",
        detailedMeaning = meanings.joinToString("\n") { m ->
            "[${m.partOfSpeech}] ${m.definitions.take(3).joinToString("; ") { it.definition }}"
        },
        exampleSentence = firstDef?.example ?: "",
        synonyms = allSynonyms.distinct().take(8),
        antonyms = allAntonyms.distinct().take(8),
        etymology = etymology ?: "",
        source = "online"
    )
}
