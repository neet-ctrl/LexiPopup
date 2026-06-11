package com.lexipopup.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lexipopup.data.local.dao.FavoriteWordDao
import com.lexipopup.data.local.dao.FlashcardDao
import com.lexipopup.data.local.dao.UserNoteDao
import com.lexipopup.data.local.dao.VocabularyDao
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.entities.FavoriteWordEntity
import com.lexipopup.data.local.entities.FlashcardEntity
import com.lexipopup.data.local.entities.UserNoteEntity
import com.lexipopup.data.local.entities.UserSettingsEntity
import com.lexipopup.data.local.entities.VocabularyHistoryEntity
import com.lexipopup.data.local.entities.WordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        WordEntity::class,
        VocabularyHistoryEntity::class,
        FlashcardEntity::class,
        FavoriteWordEntity::class,
        UserNoteEntity::class,
        UserSettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LexiDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun favoriteWordDao(): FavoriteWordDao
    abstract fun userNoteDao(): UserNoteDao

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
                        // SQLite optimizations per spec:
                        // page_size=4096, WAL, NORMAL sync, 2MB cache
                        db.execSQL("PRAGMA page_size = 4096")
                        db.execSQL("PRAGMA journal_mode = WAL")
                        db.execSQL("PRAGMA synchronous = NORMAL")
                        db.execSQL("PRAGMA cache_size = -2000")
                        db.execSQL("PRAGMA mmap_size = 268435456")
                        db.execSQL("PRAGMA shrink_memory")
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
