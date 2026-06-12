package com.lexipopup.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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
import android.util.Log
import kotlinx.coroutines.CoroutineScope

@Database(
    entities = [
        WordEntity::class,
        VocabularyHistoryEntity::class,
        FlashcardEntity::class,
        FavoriteWordEntity::class,
        UserNoteEntity::class,
        UserSettingsEntity::class
    ],
    version = 3,
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

        /**
         * Migration 2 → 3: wipe the old built-in seed words so the new
         * 1 000-word set is inserted fresh by the onOpen re-seed guard.
         * All user data (favourites, flashcards, notes, history) is untouched.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM dictionary_cache WHERE source = 'seed'")
            }
        }

        fun create(context: Context, scope: CoroutineScope): LexiDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LexiDatabase::class.java,
                DATABASE_NAME
            )
                // Let Room enable WAL + set synchronous = NORMAL itself, OUTSIDE any
                // transaction. Setting PRAGMA synchronous inside a transaction (which is
                // what Callback.onCreate receives) is illegal on Android 16 / SQLite 3.46+
                // and causes the "Safety level may not be changed inside a transaction" crash.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed runs synchronously inside Room's onCreate transaction.
                        // DatabaseSeeder.seed() detects db.inTransaction() and skips its own
                        // beginTransaction/endTransaction to avoid illegal nesting.
                        try {
                            DatabaseSeeder.seed(db)
                            Log.i("LexiDatabase", "Seed completed successfully")
                        } catch (e: Exception) {
                            Log.e("LexiDatabase", "Seed failed — app will have no built-in words", e)
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // These PRAGMAs are safe to set outside a transaction (onOpen is
                        // called after Room's setup transaction closes).
                        // Wrap in try/catch so an unexpected OEM restriction never crashes the app.
                        try {
                            db.execSQL("PRAGMA cache_size = -2000")
                            db.execSQL("PRAGMA mmap_size = 268435456")
                        } catch (_: Exception) { /* non-fatal — defaults are fine */ }

                        // ── Lazy re-seed guard ─────────────────────────────────────
                        // onCreate only fires on a fresh install. Users who installed
                        // before the seeder existed (or who had the DB recreated via
                        // fallbackToDestructiveMigration without a fresh install) will
                        // have 0 seed words. Detect and fix that here on every open.
                        // DatabaseSeeder uses INSERT OR IGNORE so this is idempotent —
                        // if words already exist nothing changes.
                        try {
                            val cursor = db.query(
                                "SELECT COUNT(*) FROM dictionary_cache WHERE source = 'seed'",
                                emptyArray<Any?>()
                            )
                            val seedCount = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                            cursor.close()
                            if (seedCount == 0) {
                                Log.i("LexiDatabase", "No seed words found on open — running seeder now")
                                DatabaseSeeder.seed(db)
                                Log.i("LexiDatabase", "Re-seed on open completed successfully")
                            }
                        } catch (e: Exception) {
                            Log.e("LexiDatabase", "Re-seed on open failed", e)
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
