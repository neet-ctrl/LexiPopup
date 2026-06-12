package com.lexipopup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lexipopup.data.local.database.LexiDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies that critical words are in the seed database,
 * lookup performance meets the <50ms target, and that when
 * the full pack is downloaded it fits within the 300MB budget
 * (Full pack is Wiktionary + WordNet + Hindi WordNet, up to ~200MB on-disk).
 *
 * NOTE: This is an instrumented test — run with `./gradlew connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSizeAndWordTest {

    private lateinit var db: LexiDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LexiDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    // ─── Word existence tests ──────────────────────────────────────

    @Test
    fun testWordExists_procrastination() = runTest {
        val result = db.wordDao().findWord("procrastination")
        assertNotNull("'procrastination' must be in seed database", result)
        assertTrue(
            "Meaning must reference delay or postpone",
            result!!.shortMeaning.contains("delay", ignoreCase = true) ||
                result.shortMeaning.contains("postpone", ignoreCase = true) ||
                result.shortMeaning.contains("defer", ignoreCase = true)
        )
        assertNotNull("Hindi meaning must be present", result.hindiMeaning.takeIf { it.isNotBlank() })
    }

    @Test
    fun testWordExists_belittle() = runTest {
        val result = db.wordDao().findWord("belittle")
        assertNotNull("'belittle' must be in seed database", result)
        val synonymsStr = result!!.synonyms ?: ""
        assertTrue(
            "Synonyms must contain 'disparage' or 'denigrate'",
            synonymsStr.contains("disparage", ignoreCase = true) ||
                synonymsStr.contains("denigrate", ignoreCase = true) ||
                synonymsStr.contains("dismiss", ignoreCase = true)
        )
    }

    @Test
    fun testWordExists_serendipity() = runTest {
        val result = db.wordDao().findWord("serendipity")
        assertNotNull("'serendipity' must be in seed database", result)
        assertFalse("Meaning must not be empty", result!!.shortMeaning.isBlank())
        assertFalse("Example must not be empty", result.exampleSentence.isBlank())
    }

    @Test
    fun testWordExists_ephemeral() = runTest {
        val result = db.wordDao().findWord("ephemeral")
        assertNotNull("'ephemeral' must be in seed database", result)
        val synonymsStr = result!!.synonyms ?: ""
        assertTrue(
            "Synonyms must contain 'transient' or 'temporary'",
            synonymsStr.contains("transient", ignoreCase = true) ||
                synonymsStr.contains("temporary", ignoreCase = true) ||
                synonymsStr.contains("fleeting", ignoreCase = true)
        )
    }

    // ─── Lookup performance tests ──────────────────────────────────

    @Test
    fun testLookupPerformanceUnder50ms() = runTest {
        // warm up
        db.wordDao().findWord("ephemeral")

        val start = System.currentTimeMillis()
        repeat(10) { db.wordDao().findWord("procrastination") }
        val elapsed = (System.currentTimeMillis() - start) / 10

        assertTrue("Average lookup must be under 50ms but was ${elapsed}ms", elapsed < 50)
    }

    @Test
    fun testSuggestionsReturnedQuickly() = runTest {
        val start = System.currentTimeMillis()
        val results = db.wordDao().getSuggestions("pro", 5)
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Suggestions must arrive under 50ms", elapsed < 50)
    }

    // ─── Database size test (production only — skipped if DB not present) ──────

    @Test
    fun testProductionDatabaseSizeUnder300MB() {
        val dbFile = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().filesDir,
            "lexi_database.db"
        )
        if (!dbFile.exists()) {
            // Full pack not downloaded in test environment — skip gracefully
            return
        }
        val sizeMb = dbFile.length() / (1024L * 1024L)
        assertTrue("Production DB must be under 300MB but is ${sizeMb}MB", sizeMb < 300)
    }

    @Test
    fun testSeedDatabaseHasMinimum1000Words() = runTest {
        val count = db.wordDao().getTotalCount()
        assertTrue("Seed DB must have at least 1000 words but has $count", count >= 1000)
    }

    // ─── SQLite optimization verification ─────────────────────────

    @Test
    fun testWalModeEnabled() {
        val cursor = db.openHelper.readableDatabase.query("PRAGMA journal_mode", null)
        cursor.use {
            if (it.moveToFirst()) {
                val mode = it.getString(0)
                assertTrue(
                    "WAL mode must be enabled for performance",
                    mode.equals("wal", ignoreCase = true)
                )
            }
        }
    }
}
