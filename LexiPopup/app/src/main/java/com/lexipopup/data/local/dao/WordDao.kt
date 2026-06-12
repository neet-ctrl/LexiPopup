package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.WordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT * FROM dictionary_cache WHERE word = :word AND mode = :mode LIMIT 1")
    suspend fun findWord(word: String, mode: String = "english"): WordEntity?

    @Query("SELECT word FROM dictionary_cache WHERE word LIKE :prefix || '%' AND mode = :mode ORDER BY frequency_rating DESC LIMIT :limit")
    suspend fun getSuggestions(prefix: String, limit: Int, mode: String = "english"): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>)

    @Query("UPDATE dictionary_cache SET is_favorite = NOT is_favorite WHERE word = :word AND mode = :mode")
    suspend fun toggleFavorite(word: String, mode: String = "english")

    @Query("UPDATE dictionary_cache SET is_favorite = :isFav WHERE word = :word AND mode = :mode")
    suspend fun setFavorite(word: String, isFav: Boolean, mode: String = "english")

    @Query("UPDATE dictionary_cache SET user_note = :note WHERE word = :word AND mode = :mode")
    suspend fun updateNote(word: String, note: String, mode: String = "english")

    @Query("UPDATE dictionary_cache SET last_accessed = :ts, access_count = access_count + 1 WHERE word = :word AND mode = :mode")
    suspend fun updateAccess(word: String, mode: String = "english", ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM dictionary_cache WHERE is_favorite = 1 AND mode = :mode ORDER BY last_accessed DESC")
    fun getFavorites(mode: String = "english"): Flow<List<WordEntity>>

    @Query("SELECT * FROM dictionary_cache WHERE is_favorite = 1 AND mode = :mode ORDER BY last_accessed DESC")
    suspend fun getFavoritesList(mode: String = "english"): List<WordEntity>

    @Query("SELECT * FROM dictionary_cache WHERE mode = :mode ORDER BY last_accessed DESC LIMIT :limit")
    fun getRecentWords(limit: Int, mode: String = "english"): Flow<List<WordEntity>>

    @Query("SELECT * FROM dictionary_cache WHERE mode = :mode ORDER BY last_accessed DESC LIMIT :limit")
    suspend fun getRecentWordsList(limit: Int, mode: String = "english"): List<WordEntity>

    @Query("SELECT COUNT(*) FROM dictionary_cache WHERE mode = :mode")
    suspend fun getTotalCount(mode: String = "english"): Int

    @Query("SELECT COUNT(*) FROM dictionary_cache WHERE mode = :mode")
    fun getTotalCountFlow(mode: String = "english"): kotlinx.coroutines.flow.Flow<Int>

    @Query("DELETE FROM dictionary_cache WHERE source = 'local' AND word = :word AND mode = :mode")
    suspend fun deleteWord(word: String, mode: String = "english")

    @Query("DELETE FROM dictionary_cache WHERE source = :source AND mode = :mode")
    suspend fun deleteWordsBySource(source: String, mode: String = "english")

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE word LIKE '%' || :query || '%'
          AND mode = :mode
        ORDER BY
            CASE WHEN word LIKE :query || '%' THEN 0 ELSE 1 END,
            frequency_rating DESC
        LIMIT :limit
    """)
    suspend fun searchWords(query: String, limit: Int = 60, mode: String = "english"): List<WordEntity>

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE UPPER(SUBSTR(word, 1, 1)) = UPPER(:letter)
          AND (:pos = '' OR LOWER(part_of_speech) = LOWER(:pos))
          AND mode = :mode
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
        pos: String = "",
        mode: String = "english"
    ): List<WordEntity>

    @Query("SELECT COUNT(*) FROM dictionary_cache WHERE UPPER(SUBSTR(word, 1, 1)) = UPPER(:letter) AND mode = :mode")
    suspend fun countByLetter(letter: String, mode: String = "english"): Int

    @Query("SELECT * FROM dictionary_cache WHERE is_favorite = 0 AND mode = :mode ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomWord(mode: String = "english"): WordEntity?

    @Query("SELECT * FROM dictionary_cache WHERE part_of_speech = :pos AND mode = :mode ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomWordByPos(pos: String, mode: String = "english"): WordEntity?

    @Query("SELECT * FROM dictionary_cache WHERE mode = :mode ORDER BY word ASC LIMIT 1 OFFSET :offset")
    suspend fun getWordAtOffset(offset: Int, mode: String = "english"): WordEntity?

    @Query("""
        SELECT difficulty_level AS difficultyLevel, COUNT(*) AS count
        FROM dictionary_cache
        WHERE mode = :mode
        GROUP BY difficulty_level
        ORDER BY difficulty_level ASC
    """)
    suspend fun getDifficultyDistribution(mode: String = "english"): List<DifficultyCountRow>

    // ── Word of the Day / Term of the Day queries ─────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM dictionary_cache
        WHERE frequency_rating BETWEEN :minFreq AND :maxFreq
          AND difficulty_level BETWEEN :minDiff AND :maxDiff
          AND LENGTH(word) BETWEEN 5 AND 12
          AND hindi_meaning != ''
          AND example_sentence != ''
          AND pronunciation != ''
          AND mode = :mode
    """)
    suspend fun getWordOfDayCandidateCount(
        minFreq: Int, maxFreq: Int, minDiff: Int, maxDiff: Int,
        mode: String = "english"
    ): Int

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE frequency_rating BETWEEN :minFreq AND :maxFreq
          AND difficulty_level BETWEEN :minDiff AND :maxDiff
          AND LENGTH(word) BETWEEN 5 AND 12
          AND hindi_meaning != ''
          AND example_sentence != ''
          AND pronunciation != ''
          AND mode = :mode
        ORDER BY word ASC
        LIMIT 1 OFFSET :offset
    """)
    suspend fun getWordOfDayCandidateAt(
        offset: Int, minFreq: Int, maxFreq: Int, minDiff: Int, maxDiff: Int,
        mode: String = "english"
    ): WordEntity?

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE frequency_rating BETWEEN :minFreq AND :maxFreq
          AND difficulty_level BETWEEN :minDiff AND :maxDiff
          AND LENGTH(word) BETWEEN 5 AND 12
          AND hindi_meaning != ''
          AND example_sentence != ''
          AND pronunciation != ''
          AND mode = :mode
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomWordOfDayCandidate(
        minFreq: Int, maxFreq: Int, minDiff: Int, maxDiff: Int,
        mode: String = "english"
    ): WordEntity?

    /** Biology Term of Day: most recently accessed biology term with a definition. */
    @Query("""
        SELECT * FROM dictionary_cache
        WHERE mode = 'biology'
          AND short_meaning != ''
        ORDER BY last_accessed DESC
        LIMIT 1 OFFSET :offset
    """)
    suspend fun getBiologyTermOfDayCandidateAt(offset: Int): WordEntity?

    @Query("""
        SELECT COUNT(*) FROM dictionary_cache
        WHERE mode = 'biology' AND short_meaning != ''
    """)
    suspend fun getBiologyTermCount(): Int

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE word = :word
          AND mode = :mode
          AND (access_count >= :minCount
               OR (:includeAiSourced = 1 AND source IN ('online', 'groq', 'openai', 'on_device', 'groq_bio', 'openai_bio', 'on_device_bio')))
        LIMIT 1
    """)
    suspend fun findHistoryWord(
        word: String,
        mode: String = "english",
        minCount: Int,
        includeAiSourced: Boolean
    ): WordEntity?

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE (access_count > 0 OR source IN ('online', 'groq', 'openai', 'on_device', 'groq_bio', 'openai_bio', 'on_device_bio'))
          AND mode = :mode
        ORDER BY last_accessed DESC
    """)
    fun getAllHistoryWords(mode: String = "english"): Flow<List<WordEntity>>

    @Query("""
        SELECT COUNT(*) FROM dictionary_cache
        WHERE (access_count > 0 OR source IN ('online', 'groq', 'openai', 'on_device', 'groq_bio', 'openai_bio', 'on_device_bio'))
          AND mode = :mode
    """)
    fun getHistoryWordCountFlow(mode: String = "english"): Flow<Int>

    // ── Seed word list queries ─────────────────────────────────────────────────

    @Query("""
        SELECT * FROM dictionary_cache
        WHERE source = 'seed'
          AND mode = 'english'
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

    @Query("SELECT COUNT(*) FROM dictionary_cache WHERE source = 'seed' AND mode = 'english'")
    suspend fun getSeedWordCount(): Int
}

data class DifficultyCountRow(
    val difficultyLevel: Int,
    val count: Int
)
