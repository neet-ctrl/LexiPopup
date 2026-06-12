package com.lexipopup.presentation.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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
    val version: Int = 1,
    val exportedAt: String = "",
    val favorites: List<String> = emptyList(),
    val flashcardWords: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList()
)

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Exporting : BackupUiState()
    data class ExportDone(val uri: Uri) : BackupUiState()
    object Importing : BackupUiState()
    data class ImportDone(val wordsRestored: Int) : BackupUiState()
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
                val favorites = dictionaryRepository.getFavorites().first().map { it.word }
                val flashcards = vocabularyRepository.getAllFlashcards().first().map { it.word }
                val recent = dictionaryRepository.getRecentWords(limit = 500).first().map { it.word }

                val backup = BackupData(
                    exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    favorites = favorites,
                    flashcardWords = flashcards,
                    recentSearches = recent
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
                    putExtra(Intent.EXTRA_SUBJECT, "LexiPopup Backup")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Save backup via…").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })

                _state.value = BackupUiState.ExportDone(uri)
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
                var restored = 0

                backup.favorites.forEach { word ->
                    runCatching {
                        val isFav = vocabularyRepository.isFavorite(word)
                        if (!isFav) vocabularyRepository.toggleFavorite(word)
                        restored++
                    }
                }

                backup.flashcardWords.forEach { word ->
                    runCatching {
                        vocabularyRepository.createFlashcard(word, word, "")
                        restored++
                    }
                }

                _state.value = BackupUiState.ImportDone(restored)
            } catch (e: Exception) {
                _state.value = BackupUiState.Error("Import failed: ${e.message}")
            }
        }
    }

    fun resetState() { _state.value = BackupUiState.Idle }
}
