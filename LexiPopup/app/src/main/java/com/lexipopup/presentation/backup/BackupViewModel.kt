package com.lexipopup.presentation.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.domain.repositories.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BackupData(
    val version: Int = 2,
    val exportedAt: String = "",
    val historyEntries: List<WordEntry> = emptyList(),
    val favoriteWords: List<String> = emptyList(),
    val flashcardWords: List<String> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList()
)

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Exporting : BackupUiState()
    data class ExportDone(val wordCount: Int) : BackupUiState()
    object Importing : BackupUiState()
    data class ImportDone(val wordsRestored: Int, val favoritesRestored: Int, val flashcardsRestored: Int) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val gson: Gson
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Exporting
            try {
                val historyEntries = dictionaryRepository.getWordHistory().first()

                val favoritesFromRepo = dictionaryRepository.getFavorites().first()
                val favoriteWords = favoritesFromRepo.map { it.word }

                val historyWords = historyEntries.map { it.word }.toSet()
                val extraFavoriteEntries = favoritesFromRepo.filter { it.word !in historyWords }
                val allEntries = historyEntries + extraFavoriteEntries

                val flashcards = vocabularyRepository.getAllFlashcardsAllModes().first().map { it.word }

                val backup = BackupData(
                    version = 2,
                    exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    historyEntries = allEntries,
                    favoriteWords = favoriteWords,
                    flashcardWords = flashcards
                )

                val json = gson.toJson(backup)

                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw Exception("Could not open output stream for selected file")

                _state.value = BackupUiState.ExportDone(allEntries.size)
            } catch (e: Exception) {
                _state.value = BackupUiState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Importing
            try {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
                    ?: throw Exception("Could not read file")

                // ── Type-safe parsing ───────────────────────────────────────
                // gson.fromJson(json, BackupData::class.java) causes a ClassCastException
                // in release builds because Gson cannot recover the List<WordEntry> type
                // parameter at runtime (type erasure). Gson creates LinkedTreeMap objects
                // instead of WordEntry instances, which then crash on cast.
                //
                // Fix: parse as JsonObject first, then extract each field with an explicit
                // TypeToken.getParameterized() so Gson has the full type info it needs.
                val root = gson.fromJson(json, JsonObject::class.java)

                val wordEntryListType =
                    TypeToken.getParameterized(List::class.java, WordEntry::class.java).type
                val stringListType =
                    TypeToken.getParameterized(List::class.java, String::class.java).type

                val historyEntries: List<WordEntry> = root["historyEntries"]?.let {
                    runCatching { gson.fromJson<List<WordEntry>>(it, wordEntryListType) }
                        .getOrDefault(emptyList())
                } ?: emptyList()

                val favoriteWords: List<String> = root["favoriteWords"]?.let {
                    runCatching { gson.fromJson<List<String>>(it, stringListType) }
                        .getOrDefault(emptyList())
                } ?: emptyList()

                val legacyFavorites: List<String> = root["favorites"]?.let {
                    runCatching { gson.fromJson<List<String>>(it, stringListType) }
                        .getOrDefault(emptyList())
                } ?: emptyList()

                val flashcardWords: List<String> = root["flashcardWords"]?.let {
                    runCatching { gson.fromJson<List<String>>(it, stringListType) }
                        .getOrDefault(emptyList())
                } ?: emptyList()

                // ── Restore ─────────────────────────────────────────────────
                var wordsRestored = 0
                var favoritesRestored = 0
                var flashcardsRestored = 0

                historyEntries.forEach { entry ->
                    runCatching {
                        dictionaryRepository.saveToCache(entry)
                        dictionaryRepository.markAccessed(entry.word)
                        wordsRestored++
                    }
                }

                val allFavWords = favoriteWords.ifEmpty { legacyFavorites }
                allFavWords.forEach { word ->
                    runCatching {
                        val existing = dictionaryRepository.lookupWord(word)
                        if (existing != null && !existing.isFavorite) {
                            dictionaryRepository.toggleFavorite(word)
                            favoritesRestored++
                        }
                    }
                }

                flashcardWords.forEach { word ->
                    runCatching {
                        vocabularyRepository.createFlashcard(word, word, "")
                        flashcardsRestored++
                    }
                }

                _state.value = BackupUiState.ImportDone(wordsRestored, favoritesRestored, flashcardsRestored)
            } catch (e: Exception) {
                _state.value = BackupUiState.Error("Import failed: ${e.message}")
            }
        }
    }

    fun resetState() { _state.value = BackupUiState.Idle }
}
