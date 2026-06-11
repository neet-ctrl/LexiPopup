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

    override suspend fun saveToCache(entry: WordEntry) {
        wordDao.insertWord(entry.toEntity(gson))
    }

    override suspend fun toggleFavorite(word: String) = wordDao.toggleFavorite(word)

    override suspend fun saveNote(word: String, note: String) = wordDao.updateNote(word, note)

    override fun getFavorites(): Flow<List<WordEntry>> =
        wordDao.getFavorites().map { list -> list.map { it.toDomain(gson) } }

    override fun getRecentWords(limit: Int): Flow<List<WordEntry>> =
        wordDao.getRecentWords(limit).map { list -> list.map { it.toDomain(gson) } }
}

fun WordEntity.toDomain(gson: Gson): WordEntry {
    val listType = object : TypeToken<List<String>>() {}.type
    return WordEntry(
        word = word,
        pronunciation = pronunciation,
        partOfSpeech = partOfSpeech,
        shortMeaning = shortMeaning,
        detailedMeaning = detailedMeaning,
        hindiMeaning = hindiMeaning,
        hindiPronunciation = hindiPronunciation,
        exampleSentence = exampleSentence,
        synonyms = runCatching { gson.fromJson<List<String>>(synonyms, listType) }.getOrDefault(emptyList()),
        antonyms = runCatching { gson.fromJson<List<String>>(antonyms, listType) }.getOrDefault(emptyList()),
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
