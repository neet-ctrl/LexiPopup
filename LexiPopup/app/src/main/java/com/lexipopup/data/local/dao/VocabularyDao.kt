package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.VocabularyHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(entry: VocabularyHistoryEntity)

    @Query("SELECT * FROM vocabulary_history WHERE mode = :mode ORDER BY search_timestamp DESC LIMIT :limit")
    fun getHistory(limit: Int, mode: String = "english"): Flow<List<VocabularyHistoryEntity>>

    @Query("SELECT COUNT(*) FROM vocabulary_history WHERE search_timestamp >= :startOfDay AND mode = :mode")
    fun getTodayCount(startOfDay: Long, mode: String = "english"): Flow<Int>

    @Query("""
        SELECT word, COUNT(*) as count 
        FROM vocabulary_history
        WHERE mode = :mode
        GROUP BY word 
        ORDER BY count DESC 
        LIMIT :limit
    """)
    fun getMostSearched(limit: Int, mode: String = "english"): Flow<List<WordCount>>

    @Query("""
        SELECT strftime('%w', datetime(search_timestamp/1000, 'unixepoch')) as day, COUNT(*) as count
        FROM vocabulary_history
        WHERE search_timestamp >= :sevenDaysAgo AND mode = :mode
        GROUP BY day
    """)
    fun getWeeklyStats(sevenDaysAgo: Long, mode: String = "english"): Flow<List<DayCount>>

    @Query("""
        SELECT strftime('%Y-%m-%d', datetime(search_timestamp/1000, 'unixepoch')) as date, 
               COUNT(*) as count
        FROM vocabulary_history
        WHERE search_timestamp >= (strftime('%s','now') - :days * 86400) * 1000
          AND mode = :mode
        GROUP BY date
        ORDER BY date ASC
    """)
    fun getActivityForDays(days: Int = 84, mode: String = "english"): Flow<List<DateCount>>

    @Query("DELETE FROM vocabulary_history WHERE mode = :mode")
    suspend fun clearHistory(mode: String = "english")

    @Query("DELETE FROM vocabulary_history")
    suspend fun clearAllHistory()
}

data class WordCount(val word: String, val count: Int)
data class DayCount(val day: String, val count: Int)
data class DateCount(val date: String, val count: Int)
