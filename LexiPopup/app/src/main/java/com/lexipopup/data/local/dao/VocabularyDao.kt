package com.lexipopup.data.local.dao

import androidx.room.*
import com.lexipopup.data.local.entities.VocabularyHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(entry: VocabularyHistoryEntity)

    @Query("SELECT * FROM vocabulary_history ORDER BY search_timestamp DESC LIMIT :limit")
    fun getHistory(limit: Int): Flow<List<VocabularyHistoryEntity>>

    @Query("SELECT COUNT(*) FROM vocabulary_history WHERE search_timestamp >= :startOfDay")
    fun getTodayCount(startOfDay: Long): Flow<Int>

    @Query("""
        SELECT word, COUNT(*) as count 
        FROM vocabulary_history 
        GROUP BY word 
        ORDER BY count DESC 
        LIMIT :limit
    """)
    fun getMostSearched(limit: Int): Flow<List<WordCount>>

    @Query("""
        SELECT strftime('%w', datetime(search_timestamp/1000, 'unixepoch')) as day, COUNT(*) as count
        FROM vocabulary_history
        WHERE search_timestamp >= :sevenDaysAgo
        GROUP BY day
    """)
    fun getWeeklyStats(sevenDaysAgo: Long): Flow<List<DayCount>>

    @Query("DELETE FROM vocabulary_history")
    suspend fun clearHistory()
}

data class WordCount(val word: String, val count: Int)
data class DayCount(val day: String, val count: Int)
