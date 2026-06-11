package com.lexipopup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.database.LexiDatabase
import com.lexipopup.data.local.entities.WordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: LexiDatabase
    private lateinit var dao: WordDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LexiDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.wordDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insertAndRetrieveWord() = runTest {
        val entity = WordEntity(
            word = "ephemeral",
            shortMeaning = "Lasting for a very short time",
            pronunciation = "/ɪˈfem.ər.əl/",
            partOfSpeech = "adjective"
        )
        dao.insertWord(entity)
        val result = dao.findWord("ephemeral")
        assertNotNull(result)
        assertEquals("ephemeral", result?.word)
        assertEquals("Lasting for a very short time", result?.shortMeaning)
    }

    @Test
    fun toggleFavorite() = runTest {
        dao.insertWord(WordEntity(word = "serendipity", shortMeaning = "Happy accident"))
        dao.toggleFavorite("serendipity")
        val favorites = dao.getFavorites().first()
        assertTrue(favorites.any { it.word == "serendipity" })
    }

    @Test
    fun searchSuggestions() = runTest {
        dao.insertWord(WordEntity(word = "procrastination", shortMeaning = "Delay"))
        dao.insertWord(WordEntity(word = "profound", shortMeaning = "Deep"))
        dao.insertWord(WordEntity(word = "progress", shortMeaning = "Advance"))
        val suggestions = dao.getSuggestions("pro", 5)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.contains("procrastination") || suggestions.contains("profound"))
    }

    @Test
    fun updateNoteAndRetrieve() = runTest {
        dao.insertWord(WordEntity(word = "belittle", shortMeaning = "Dismiss as unimportant"))
        dao.updateNote("belittle", "Use this word in writing")
        val word = dao.findWord("belittle")
        assertEquals("Use this word in writing", word?.userNote)
    }
}
