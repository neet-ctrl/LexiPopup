package com.lexipopup.presentation.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.AppSettings
import com.lexipopup.domain.models.Flashcard
import com.lexipopup.domain.repositories.VocabularyRepository
import com.lexipopup.utils.ModeManager
import com.lexipopup.utils.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlashcardStats(val due: Int = 0, val learning: Int = 0, val mastered: Int = 0)

@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val modeManager: ModeManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val activeMode: StateFlow<AppMode> = modeManager.currentMode

    @OptIn(ExperimentalCoroutinesApi::class)
    val dueCards: StateFlow<List<Flashcard>> = modeManager.currentMode
        .flatMapLatest { mode -> vocabularyRepository.getDueFlashcards(mode.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<FlashcardStats> = modeManager.currentMode
        .flatMapLatest { mode ->
            vocabularyRepository.getAllFlashcards(mode.id)
                .combine(vocabularyRepository.getDueFlashcards(mode.id)) { all, due ->
                    FlashcardStats(
                        due = due.size,
                        learning = all.count { it.reviewLevel in 1..4 },
                        mastered = all.count { it.reviewLevel >= 5 }
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlashcardStats())

    val appSettings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun reviewCard(id: Long, quality: Int) {
        viewModelScope.launch {
            vocabularyRepository.reviewFlashcard(id, quality)
        }
    }

    fun skipCard(id: Long) {
        reviewCard(id, 3)
    }

    fun deleteCard(id: Long) {
        viewModelScope.launch {
            vocabularyRepository.deleteFlashcard(id)
        }
    }

    fun toggleBioCardSetting(key: String, value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.update { prefs ->
                when (key) {
                    "category" -> prefs[SettingsDataStore.BIO_CARD_SHOW_CATEGORY] = value
                    "example"  -> prefs[SettingsDataStore.BIO_CARD_SHOW_EXAMPLE]  = value
                    "function" -> prefs[SettingsDataStore.BIO_CARD_SHOW_FUNCTION]  = value
                    "hindi"    -> prefs[SettingsDataStore.BIO_CARD_SHOW_HINDI]     = value
                }
            }
        }
    }
}
