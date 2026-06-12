package com.lexipopup.data.local.database

import com.lexipopup.data.local.dao.FavoriteWordDao
import com.lexipopup.data.local.dao.FlashcardDao
import com.lexipopup.data.local.dao.UserNoteDao
import com.lexipopup.data.local.dao.VocabularyDao
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.entities.FavoriteWordEntity
import com.lexipopup.data.local.entities.FlashcardEntity
import com.lexipopup.data.local.entities.UserNoteEntity
import com.lexipopup.data.local.entities.VocabularyHistoryEntity
import com.lexipopup.domain.models.Flashcard
import com.lexipopup.domain.models.UserNote
import com.lexipopup.domain.models.VocabularyHistory
import com.lexipopup.domain.repositories.VocabularyRepository
import com.lexipopup.domain.usecases.SpacedRepetitionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocabularyRepositoryImpl @Inject constructor(
    private val vocabularyDao: VocabularyDao,
    private val flashcardDao: FlashcardDao,
    private val favoriteWordDao: FavoriteWordDao,
    private val userNoteDao: UserNoteDao,
    private val wordDao: WordDao,
    private val srs: SpacedRepetitionUseCase
) : VocabularyRepository {

    // ─── History ──────────────────────────────────────────────────

    override suspend fun recordSearch(word: String, sourceApp: String, timeSpentMs: Long) {
        vocabularyDao.insertHistory(
            VocabularyHistoryEntity(word = word, sourceApp = sourceApp, timeSpentMs = timeSpentMs)
        )
    }

    override fun getHistory(limit: Int): Flow<List<VocabularyHistory>> =
        vocabularyDao.getHistory(limit).map { list ->
            list.map {
                VocabularyHistory(
                    it.id, it.word,
                    Instant.ofEpochMilli(it.searchTimestamp).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    it.sourceApp, it.timeSpentMs
                )
            }
        }

    override fun getTodayCount(): Flow<Int> {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return vocabularyDao.getTodayCount(startOfDay)
    }

    override fun getWeeklyStats(): Flow<List<Pair<String, Int>>> {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return vocabularyDao.getWeeklyStats(sevenDaysAgo).map { list ->
            list.map { Pair(it.day, it.count) }
        }
    }

    override fun getMostSearchedWords(limit: Int): Flow<List<Pair<String, Int>>> =
        vocabularyDao.getMostSearched(limit).map { list ->
            list.map { Pair(it.word, it.count) }
        }

    override fun getActivityHeatmap(days: Int): Flow<Map<LocalDate, Int>> =
        vocabularyDao.getActivityForDays(days).map { list ->
            list.associate { row ->
                try { LocalDate.parse(row.date) to row.count }
                catch (_: Exception) { LocalDate.now() to 0 }
            }
        }

    // ─── Favorites ────────────────────────────────────────────────

    override suspend fun toggleFavorite(word: String) {
        val nowFav = !favoriteWordDao.isFavorite(word)
        if (nowFav) favoriteWordDao.addFavorite(FavoriteWordEntity(word = word))
        else favoriteWordDao.removeFavorite(word)
        // Keep dictionary_cache.is_favorite in sync so the dashboard flow updates instantly
        wordDao.setFavorite(word, nowFav)
    }

    override suspend fun isFavorite(word: String): Boolean = favoriteWordDao.isFavorite(word)

    // ─── Notes ────────────────────────────────────────────────────

    override suspend fun saveNote(word: String, note: String) {
        userNoteDao.insertNote(UserNoteEntity(word = word, note = note))
    }

    override fun getNotesForWord(word: String): Flow<List<UserNote>> =
        userNoteDao.getNotesForWord(word).map { list ->
            list.map { UserNote(it.id, it.word, it.note, it.createdAt, it.updatedAt) }
        }

    // ─── Flashcards ───────────────────────────────────────────────

    override fun getDueFlashcards(): Flow<List<Flashcard>> =
        flashcardDao.getDueCards().map { list -> list.map { it.toDomain() } }

    override fun getAllFlashcards(): Flow<List<Flashcard>> =
        flashcardDao.getAllCards().map { list -> list.map { it.toDomain() } }

    override suspend fun reviewFlashcard(id: Long, quality: Int) {
        val entity = flashcardDao.getCard(id) ?: return
        val reviewed = srs.calculateNextReview(entity.toDomain(), quality)
        flashcardDao.updateCard(reviewed.toEntity())
    }

    override suspend fun createFlashcard(word: String, front: String, back: String) {
        flashcardDao.insertCard(
            FlashcardEntity(word = word, frontText = front, backText = back)
        )
    }

    override suspend fun deleteFlashcard(id: Long) = flashcardDao.deleteById(id)
}

// ─── Extension helpers ────────────────────────────────────────────

fun FlashcardEntity.toDomain() = Flashcard(
    id = id, word = word, frontText = frontText, backText = backText,
    reviewLevel = reviewLevel,
    nextReviewDate = Instant.ofEpochMilli(nextReviewDate).atZone(ZoneId.systemDefault()).toLocalDateTime(),
    lastReviewed = lastReviewed?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
    interval = interval, easeFactor = easeFactor
)

fun Flashcard.toEntity() = FlashcardEntity(
    id = id, word = word, frontText = frontText, backText = backText,
    reviewLevel = reviewLevel,
    nextReviewDate = nextReviewDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    lastReviewed = lastReviewed?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
    interval = interval, easeFactor = easeFactor
)
