package com.lexipopup.presentation.datacontrol

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.data.local.dao.ChatDao
import com.lexipopup.data.local.dao.FavoriteWordDao
import com.lexipopup.data.local.dao.FlashcardDao
import com.lexipopup.data.local.dao.RandomWordDao
import com.lexipopup.data.local.dao.UserNoteDao
import com.lexipopup.data.local.dao.VocabularyDao
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.database.LexiDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DictPackInfo(
    val packName: String,
    val displayName: String,
    val bytes: Long,
    val isPartial: Boolean
)

data class AiModelFileInfo(
    val fileName: String,
    val displayName: String,
    val bytes: Long
)

data class DataControlUiState(
    val isLoading: Boolean = true,
    val lookupCacheNonSeedCount: Int = 0,
    val searchHistoryCount: Int = 0,
    val flashcardCount: Int = 0,
    val favoritesCount: Int = 0,
    val notesCount: Int = 0,
    val chatSessionCount: Int = 0,
    val chatMessageCount: Int = 0,
    val randomWordCount: Int = 0,
    val dictPacks: List<DictPackInfo> = emptyList(),
    val aiModelFiles: List<AiModelFileInfo> = emptyList(),
    val appCacheBytes: Long = 0L,
    val dbBytes: Long = 0L,
    val snackbarMessage: String? = null
) {
    val totalBytes: Long
        get() = dbBytes + appCacheBytes +
                dictPacks.sumOf { it.bytes } +
                aiModelFiles.sumOf { it.bytes }
}

@HiltViewModel
class DataControlViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wordDao: WordDao,
    private val vocabularyDao: VocabularyDao,
    private val flashcardDao: FlashcardDao,
    private val favoriteWordDao: FavoriteWordDao,
    private val userNoteDao: UserNoteDao,
    private val chatDao: ChatDao,
    private val randomWordDao: RandomWordDao,
    private val db: LexiDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataControlUiState())
    val uiState = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                val totalCount = wordDao.getTotalCount()
                val seedCount  = wordDao.getSeedWordCount()
                val nonSeedCount = (totalCount - seedCount).coerceAtLeast(0)

                val historyCount     = rawCount("vocabulary_history")
                val flashcardCount   = rawCount("flashcards")
                val favCount         = favoriteWordDao.getFavoriteCount()
                val noteCount        = userNoteDao.getNoteCount()
                val chatSessionCount = rawCount("chat_sessions")
                val chatMsgCount     = rawCount("chat_messages")
                val randomCount      = rawCount("random_words")

                val cacheBytes = context.cacheDir.totalSize()
                val dbBytes    = context.getDatabasePath(LexiDatabase.DATABASE_NAME)?.length() ?: 0L

                val dictPacks  = scanDictPackFiles()
                val aiModels   = scanAiModelFiles()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lookupCacheNonSeedCount = nonSeedCount,
                        searchHistoryCount = historyCount,
                        flashcardCount = flashcardCount,
                        favoritesCount = favCount,
                        notesCount = noteCount,
                        chatSessionCount = chatSessionCount,
                        chatMessageCount = chatMsgCount,
                        randomWordCount = randomCount,
                        appCacheBytes = cacheBytes,
                        dbBytes = dbBytes,
                        dictPacks = dictPacks,
                        aiModelFiles = aiModels
                    )
                }
            }
        }
    }

    private fun rawCount(table: String): Int {
        return try {
            val cur = db.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM $table", arrayOf<Any?>())
            val n = if (cur.moveToFirst()) cur.getInt(0) else 0
            cur.close()
            n
        } catch (_: Exception) { 0 }
    }

    private fun scanDictPackFiles(): List<DictPackInfo> {
        val names = listOf("minimal" to "Minimal Pack", "standard" to "Standard Pack", "full" to "Full Pack")
        return names.mapNotNull { (name, display) ->
            val gz = File(context.filesDir, "dict_${name}.db.gz")
            val plain = File(context.filesDir, "dict_${name}.db")
            when {
                gz.exists()    -> DictPackInfo(name, display, gz.length(), isPartial = true)
                plain.exists() -> DictPackInfo(name, display, plain.length(), isPartial = false)
                else           -> null
            }
        }
    }

    private fun scanAiModelFiles(): List<AiModelFileInfo> {
        val dir = File(context.filesDir, "ai_models")
        if (!dir.exists()) return emptyList()
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.length() > 1024 }
            .map { f ->
                val display = when {
                    f.name.contains("gemma", ignoreCase = true) -> "Gemma 2 2B"
                    f.name.contains("phi", ignoreCase = true)   -> "Phi-2"
                    else -> f.nameWithoutExtension
                }
                AiModelFileInfo(f.name, display, f.length())
            }
    }

    private fun File.totalSize(): Long =
        if (!exists()) 0L else walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    // ─── Delete operations ────────────────────────────────────────────────────

    fun clearLookupCache() = execDelete("Lookup cache cleared") {
        for (src in listOf("online", "groq", "openai", "on_device")) {
            wordDao.deleteWordsBySource(src)
        }
    }

    fun clearSearchHistory() = execDelete("Search history cleared") {
        vocabularyDao.clearHistory()
    }

    fun clearFlashcards() = execDelete("All flashcards deleted") {
        db.openHelper.writableDatabase.execSQL("DELETE FROM flashcards")
    }

    fun clearFavorites() = execDelete("All favorites removed") {
        favoriteWordDao.clearAll()
    }

    fun clearNotes() = execDelete("All notes deleted") {
        db.openHelper.writableDatabase.execSQL("DELETE FROM user_notes")
    }

    fun clearChatHistory() = execDelete("Chat history deleted") {
        chatDao.deleteAllSessions()
    }

    fun clearRandomWords() = execDelete("Random word queue cleared") {
        db.openHelper.writableDatabase.execSQL("DELETE FROM random_words")
    }

    fun clearAppCache() = execDelete("App cache cleared") {
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
    }

    fun deleteDictPack(packName: String) = execDelete("'${packName.replaceFirstChar { it.uppercase() }}' pack removed") {
        for (ext in listOf("db.gz", "db")) {
            File(context.filesDir, "dict_${packName}.$ext").takeIf { it.exists() }?.delete()
        }
    }

    fun deleteAiModel(fileName: String) = execDelete("AI model deleted — free up ${
        (File(context.filesDir, "ai_models/$fileName").length() / 1_048_576)}MB") {
        File(context.filesDir, "ai_models/$fileName").delete()
    }

    fun wipeAllUserData() = execDelete("All user data permanently wiped") {
        for (src in listOf("online", "groq", "openai", "on_device")) {
            wordDao.deleteWordsBySource(src)
        }
        vocabularyDao.clearHistory()
        chatDao.deleteAllSessions()
        favoriteWordDao.clearAll()
        db.openHelper.writableDatabase.execSQL("DELETE FROM flashcards")
        db.openHelper.writableDatabase.execSQL("DELETE FROM user_notes")
        db.openHelper.writableDatabase.execSQL("DELETE FROM random_words")
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
        for (name in listOf("minimal", "standard", "full")) {
            for (ext in listOf("db.gz", "db")) {
                File(context.filesDir, "dict_${name}.$ext").takeIf { it.exists() }?.delete()
            }
        }
        File(context.filesDir, "ai_models").takeIf { it.exists() }?.deleteRecursively()
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    private fun execDelete(successMsg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            _uiState.update { it.copy(snackbarMessage = successMsg) }
            refresh()
        }
    }
}
