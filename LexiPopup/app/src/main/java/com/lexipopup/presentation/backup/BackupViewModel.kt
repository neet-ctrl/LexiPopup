package com.lexipopup.presentation.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BackupData(
    val version: Int = 2,
    val exportedAt: String = "",
    // v2: full word entries for all history (online/AI/accessed) — includes all fields
    val historyEntries: List<WordEntry> = emptyList(),
    // favorites: which words from historyEntries are starred
    val favoriteWords: List<String> = emptyList(),
    // flashcard word list
    val flashcardWords: List<String> = emptyList(),
    // v1 compat fields (kept so old backups still import partial data)
    val favorites: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList()
)

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Exporting : BackupUiState()
    data class ExportDone(val uri: Uri, val wordCount: Int) : BackupUiState()
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

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            _state.value = BackupUiState.Exporting
            try {
                // Full word details for every word in history
                val historyEntries = dictionaryRepository.getWordHistory().first()

                // Also grab favorites that might not be in history (e.g. pack words starred but never accessed)
                val favoritesFromRepo = dictionaryRepository.getFavorites().first()
                val favoriteWords = favoritesFromRepo.map { it.word }

                // Merge: favorites that aren't already in history entries
                val historyWords = historyEntries.map { it.word }.toSet()
                val extraFavoriteEntries = favoritesFromRepo.filter { it.word !in historyWords }
                val allEntries = historyEntries + extraFavoriteEntries

                // Flashcard words
                val flashcards = vocabularyRepository.getAllFlashcards().first().map { it.word }

                val backup = BackupData(
                    version = 2,
                    exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    historyEntries = allEntries,
                    favoriteWords = favoriteWords,
                    flashcardWords = flashcards
                )

                val json = gson.toJson(backup)
                val dir = File(context.filesDir, "backups").also { it.mkdirs() }
                val file = File(dir, "lexipopup-backup-${System.currentTimeMillis()}.json")
                file.writeText(json)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "LexiPopup Backup — ${allEntries.size} words")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Save backup via…").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })

                _state.value = BackupUiState.ExportDone(uri, allEntries.size)
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

                // ── Step 1: Restore full word entries into DB ─────────────────
                // This is the key step — inserts definitions, meanings, pronunciations,
                // etymology, Hindi, examples, synonyms/antonyms — everything.
                val entriesToRestore = backup.historyEntries.ifEmpty {
                    // v1 fallback: no entries stored, nothing to restore for word details
                    emptyList()
                }
                entriesToRestore.forEach { entry ->
                    runCatching {
                        dictionaryRepository.saveToCache(entry)
                        dictionaryRepository.markAccessed(entry.word)
                        wordsRestored++
                    }
                }

                // ── Step 2: Restore favorites ──────────────────────────────────
                // Use v2 favoriteWords first, fall back to v1 favorites field
                val favWords = backup.favoriteWords.ifEmpty { backup.favorites }
                favWords.forEach { word ->
                    runCatching {
                        // Only mark as favorite if the word now exists in DB
                        val entry = dictionaryRepository.lookupWord(word)
                        if (entry != null && !entry.isFavorite) {
                            dictionaryRepository.toggleFavorite(word)
                            favoritesRestored++
                        }
                    }
                }

                // ── Step 3: Restore flashcards ─────────────────────────────────
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
