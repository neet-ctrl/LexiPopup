package com.lexipopup.presentation.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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

                val flashcards = vocabularyRepository.getAllFlashcards().first().map { it.word }

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

                val backup = gson.fromJson(json, BackupData::class.java)

                var wordsRestored = 0
                var favoritesRestored = 0
                var flashcardsRestored = 0

                val entriesToRestore = backup.historyEntries.ifEmpty { emptyList() }
                entriesToRestore.forEach { entry ->
                    runCatching {
                        dictionaryRepository.saveToCache(entry)
                        dictionaryRepository.markAccessed(entry.word)
                        wordsRestored++
                    }
                }

                val favWords = backup.favoriteWords.ifEmpty { backup.favorites }
                favWords.forEach { word ->
                    runCatching {
                        val entry = dictionaryRepository.lookupWord(word)
                        if (entry != null && !entry.isFavorite) {
                            dictionaryRepository.toggleFavorite(word)
                            favoritesRestored++
                        }
                    }
                }

                backup.flashcardWords.forEach { word ->
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
