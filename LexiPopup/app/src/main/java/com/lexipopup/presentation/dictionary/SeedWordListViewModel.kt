package com.lexipopup.presentation.dictionary

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.data.local.database.DatabaseSeeder
import com.lexipopup.data.local.database.LexiDatabase
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.WordEntry
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.utils.ModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SeedWordListViewModel @Inject constructor(
    private val repo: DictionaryRepository,
    private val db: LexiDatabase,
    private val modeManager: ModeManager
) : ViewModel() {

    val activeMode: StateFlow<AppMode> = modeManager.currentMode

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _words = MutableStateFlow<List<WordEntry>>(emptyList())
    val words: StateFlow<List<WordEntry>> = _words.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isForcingReseed = MutableStateFlow(false)
    val isForcingReseed: StateFlow<Boolean> = _isForcingReseed.asStateFlow()

    private val _forceSeedMessage = MutableStateFlow<String?>(null)
    val forceSeedMessage: StateFlow<String?> = _forceSeedMessage.asStateFlow()

    private val _diagnostics = MutableStateFlow("Gathering diagnostics…")
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            modeManager.currentMode.collect {
                _totalCount.value = repo.getSeedWordCount(it)
                loadWords("", it)
                refreshDiagnostics()
            }
        }
    }

    fun onSearch(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(250)
            _isLoading.value = true
            _words.value = repo.getSeedWords(query.trim(), 1000, modeManager.currentMode.value)
            _isLoading.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        loadWords("", modeManager.currentMode.value)
    }

    fun refreshDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = db.wordDao()
                val mode = modeManager.currentMode.value
                val modeId = mode.id
                val total = dao.getTotalCount(modeId)
                val seedCount = dao.getSeedWordCount(modeId)
                val recentCount = dao.getRecentWordsList(500, modeId).size
                val favCount = dao.getFavoritesList(modeId).size
                val diffDist = dao.getDifficultyDistribution(modeId)

                val dbPath = try {
                    db.openHelper.writableDatabase.path ?: "unknown"
                } catch (_: Exception) { "unavailable" }

                val sb = StringBuilder()
                val modeLabel = if (mode == AppMode.BIOLOGY) "Biology" else "English"
                sb.appendLine("══ LexiPopup DB Diagnostics [$modeLabel Mode] ══")
                sb.appendLine("Total entries (all sources) : $total")
                sb.appendLine("Seed entries (source=seed)  : $seedCount")
                sb.appendLine("Recent lookups              : $recentCount")
                sb.appendLine("Favourites starred          : $favCount")
                sb.appendLine()
                sb.appendLine("Difficulty breakdown:")
                diffDist.forEach { row ->
                    val label = when (row.difficultyLevel) {
                        1 -> "Easy   (1)"
                        2 -> "Medium (2)"
                        3 -> "Hard   (3)"
                        4 -> "Expert (4)"
                        else -> "Level  (${row.difficultyLevel})"
                    }
                    sb.appendLine("  $label : ${row.count}")
                }
                sb.appendLine()
                sb.appendLine("DB file  : ${LexiDatabase.DATABASE_NAME}")
                sb.appendLine("DB path  : $dbPath")
                sb.appendLine("DB ver   : 3 (Room schema)")
                sb.appendLine("Android  : API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                sb.appendLine("Device   : ${Build.MANUFACTURER} ${Build.MODEL}")

                _diagnostics.value = sb.toString().trimEnd()
            } catch (e: Exception) {
                _diagnostics.value = "Diagnostics error:\n${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    fun forceSeed() {
        if (_isForcingReseed.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isForcingReseed.value = true
            _forceSeedMessage.value = null
            try {
                val sqlDb = db.openHelper.writableDatabase
                val result = DatabaseSeeder.seedSafely(sqlDb)

                val mode = modeManager.currentMode.value
                val newSeedCount = db.wordDao().getSeedWordCount(mode.id)
                withContext(Dispatchers.Main) {
                    _totalCount.value = newSeedCount
                }
                _words.value = repo.getSeedWords(_searchQuery.value, 1000, mode)
                refreshDiagnostics()

                _forceSeedMessage.value = buildString {
                    appendLine("✓ Re-seed complete")
                    appendLine("  Processed : ${result.total} words")
                    appendLine("  Inserted  : ${result.inserted}")
                    appendLine("  Failed    : ${result.failed}")
                    if (result.failed > 0) {
                        appendLine()
                        appendLine("First errors:")
                        result.errors.take(5).forEach { appendLine("  • $it") }
                        if (result.errors.size > 5) appendLine("  … +${result.errors.size - 5} more")
                    }
                }.trimEnd()
            } catch (e: Exception) {
                _forceSeedMessage.value = "✗ Re-seed failed\n${e.javaClass.simpleName}: ${e.message}"
            } finally {
                _isForcingReseed.value = false
            }
        }
    }

    fun clearForceSeedMessage() {
        _forceSeedMessage.value = null
    }

    private fun loadWords(query: String, mode: AppMode) {
        viewModelScope.launch {
            _isLoading.value = true
            _words.value = repo.getSeedWords(query, 1000, mode)
            _isLoading.value = false
        }
    }
}
