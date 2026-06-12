package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.WordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT * FROM dictionary_cache WHERE word = :word LIMIT 1")
    suspend fun findWord(word: String): WordEntity?

    @Query("SELECT word FROM dictionary_cache WHERE word LIKE :prefix || '%' ORDER BY frequency_rating DESC LIMIT :limit")
    suspend fun getSuggestions(prefix: String, limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>)

    @Query("UPDATE dictionary_cache SET is_favorite = NOT is_favorite WHERE word = :word")
    suspend fun toggleFavorite(word: String)

    @Query("UPDATE dictionary_cache SET is_favorite = :isFav WHERE word = :word")
    suspend fun setFavorite(word: String, isFav: Boolean)

    @Query("UPDATE dictionary_cache SET user_note = :note WHERE word = :word")
    suspend fun updateNote(word: String, note: String)

    @Query("UPDATE dictionary_cache SET last_accessed = :ts, access_count = access_count + 1 WHERE word = :word")
    suspend fun updateAccess(word: String, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM dictionary_cache WHERE is_favorite = 1 ORDER BY last_accessed DESC")
    fun getFavorites(): Flow<List<WordEntity>>

    @Query("SELECT * FROM dictionary_cache WHERE is_favorite = 1 ORDER BY last_accessed DESC")
    suspend fun getFavoritesList(): List<WordEntity>

    @Query("SELECT * FROM dictionary_cache ORDER BY last_accessed DESC LIMIT :limit")
    fun getRecentWords(limit: Int): Flow<List<WordEntity>>

    @Query("SELECT * FROM dictionary_cache ORDER BY last_accessed DESC LIMIT :limit")
    suspend fun getRecentWordsList(limit: Int): List<WordEntity>

    @Query("SELECT COUNT(*) FROM dictionary_cache")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM dictionary_cache")
    fun getTotalCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Query("DELETE FROM dictionary_cache WHERE source = 'local' AND word = :word")
    suspend fun deleteWord(word: String)

    @Query("DELETE FROM dictionary_cache WHERE source = :source")
    suspend fun deleteWordsBySource(source: String)

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE word LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN word LIKE :query || '%' THEN 0 ELSE 1 END,
            frequency_rating DESC
        LIMIT :limit
    """)
    suspend fun searchWords(query: String, limit: Int = 60): List<WordEntity>

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE UPPER(SUBSTR(word, 1, 1)) = UPPER(:letter)
          AND (:pos = '' OR LOWER(part_of_speech) = LOWER(:pos))
        ORDER BY
            CASE :sortBy
                WHEN 'frequency' THEN (100 - frequency_rating)
                WHEN 'difficulty' THEN difficulty_level
                ELSE 0
            END,
            word ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getWordsByLetter(
        letter: String,
        limit: Int = 60,
        offset: Int = 0,
        sortBy: String = "alpha",
        pos: String = ""
    ): List<WordEntity>

    @Query("SELECT COUNT(*) FROM dictionary_cache WHERE UPPER(SUBSTR(word, 1, 1)) = UPPER(:letter)")
    suspend fun countByLetter(letter: String): Int

    @Query("SELECT * FROM dictionary_cache WHERE is_favorite = 0 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomWord(): WordEntity?

    @Query("SELECT * FROM dictionary_cache WHERE part_of_speech = :pos ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomWordByPos(pos: String): WordEntity?

    @Query("SELECT * FROM dictionary_cache ORDER BY word ASC LIMIT 1 OFFSET :offset")
    suspend fun getWordAtOffset(offset: Int): WordEntity?

    @Query("""
        SELECT difficulty_level AS difficultyLevel, COUNT(*) AS count
        FROM dictionary_cache
        GROUP BY difficulty_level
        ORDER BY difficulty_level ASC
    """)
    suspend fun getDifficultyDistribution(): List<DifficultyCountRow>

    // ── Word of the Day queries ─────────────────────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM dictionary_cache
        WHERE frequency_rating BETWEEN :minFreq AND :maxFreq
          AND difficulty_level BETWEEN :minDiff AND :maxDiff
          AND LENGTH(word) BETWEEN 5 AND 12
          AND hindi_meaning != ''
          AND example_sentence != ''
          AND pronunciation != ''
    """)
    suspend fun getWordOfDayCandidateCount(
        minFreq: Int, maxFreq: Int, minDiff: Int, maxDiff: Int
    ): Int

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE frequency_rating BETWEEN :minFreq AND :maxFreq
          AND difficulty_level BETWEEN :minDiff AND :maxDiff
          AND LENGTH(word) BETWEEN 5 AND 12
          AND hindi_meaning != ''
          AND example_sentence != ''
          AND pronunciation != ''
        ORDER BY word ASC
        LIMIT 1 OFFSET :offset
    """)
    suspend fun getWordOfDayCandidateAt(
        offset: Int, minFreq: Int, maxFreq: Int, minDiff: Int, maxDiff: Int
    ): WordEntity?

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE frequency_rating BETWEEN :minFreq AND :maxFreq
          AND difficulty_level BETWEEN :minDiff AND :maxDiff
          AND LENGTH(word) BETWEEN 5 AND 12
          AND hindi_meaning != ''
          AND example_sentence != ''
          AND pronunciation != ''
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomWordOfDayCandidate(
        minFreq: Int, maxFreq: Int, minDiff: Int, maxDiff: Int
    ): WordEntity?

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE word = :word
          AND (access_count >= :minCount
               OR (:includeAiSourced = 1 AND source IN ('online', 'groq', 'openai', 'on_device')))
        LIMIT 1
    """)
    suspend fun findHistoryWord(
        word: String,
        minCount: Int,
        includeAiSourced: Boolean
    ): WordEntity?

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE access_count > 0 OR source IN ('online', 'groq', 'openai', 'on_device')
        ORDER BY last_accessed DESC
    """)
    fun getAllHistoryWords(): Flow<List<WordEntity>>

    @Query("""
        SELECT COUNT(*) FROM dictionary_cache
        WHERE access_count > 0 OR source IN ('online', 'groq', 'openai', 'on_device')
    """)
    fun getHistoryWordCountFlow(): Flow<Int>

    // ── Seed word list queries ──────────────────────────────────────────────

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE source = 'seed'
          AND (:query = '' OR LOWER(word) LIKE '%' || LOWER(:query) || '%'
               OR LOWER(short_meaning) LIKE '%' || LOWER(:query) || '%'
               OR LOWER(part_of_speech) LIKE '%' || LOWER(:query) || '%')
        ORDER BY
            CASE WHEN :query != '' AND LOWER(word) LIKE LOWER(:query) || '%' THEN 0 ELSE 1 END,
            frequency_rating DESC,
            word ASC
        LIMIT :limit
    """)
    suspend fun getSeedWords(query: String, limit: Int): List<WordEntity>

    @Query("SELECT COUNT(*) FROM dictionary_cache WHERE source = 'seed'")
    suspend fun getSeedWordCount(): Int
}

data class DifficultyCountRow(
    val difficultyLevel: Int,
    val count: Int
)
