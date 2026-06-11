package com.lexipopup.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lexipopup.data.local.dao.FlashcardDao
import com.lexipopup.data.local.dao.VocabularyDao
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.entities.FlashcardEntity
import com.lexipopup.data.local.entities.VocabularyHistoryEntity
import com.lexipopup.data.local.entities.WordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [WordEntity::class, VocabularyHistoryEntity::class, FlashcardEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LexiDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun flashcardDao(): FlashcardDao

    companion object {
        const val DATABASE_NAME = "lexi_dictionary.db"

        fun create(context: Context, scope: CoroutineScope): LexiDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LexiDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL("PRAGMA journal_mode = WAL")
                        db.execSQL("PRAGMA synchronous = NORMAL")
                        db.execSQL("PRAGMA cache_size = -2000")
                        scope.launch(Dispatchers.IO) {
                            DatabaseSeeder.seed(db)
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
