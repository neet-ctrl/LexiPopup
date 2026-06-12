package com.lexipopup.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lexipopup.data.local.dao.ChatDao
import com.lexipopup.data.local.dao.FavoriteWordDao
import com.lexipopup.data.local.dao.FlashcardDao
import com.lexipopup.data.local.dao.UserNoteDao
import com.lexipopup.data.local.dao.VocabularyDao
import com.lexipopup.data.local.dao.WordDao
import com.lexipopup.data.local.entities.ChatMessageEntity
import com.lexipopup.data.local.entities.ChatSessionEntity
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
        UserSettingsEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LexiDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun favoriteWordDao(): FavoriteWordDao
    abstract fun userNoteDao(): UserNoteDao
    abstract fun chatDao(): ChatDao

    companion object {
        const val DATABASE_NAME = "lexi_dictionary.db"

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM dictionary_cache WHERE source = 'seed'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id`            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title`         TEXT    NOT NULL DEFAULT 'New Chat',
                        `provider`      TEXT    NOT NULL DEFAULT 'groq',
                        `created_at`    INTEGER NOT NULL,
                        `updated_at`    INTEGER NOT NULL,
                        `message_count` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id`               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `session_id`       INTEGER NOT NULL,
                        `role`             TEXT    NOT NULL,
                        `content`          TEXT    NOT NULL,
                        `timestamp`        INTEGER NOT NULL,
                        `provider`         TEXT    NOT NULL DEFAULT '',
                        `underlined_words` TEXT    NOT NULL DEFAULT '[]',
                        `is_error`         INTEGER NOT NULL DEFAULT 0,
                        `tokens_used`      INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`session_id`) REFERENCES `chat_sessions`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_session_id` ON `chat_messages` (`session_id`)")
            }
        }

        /**
         * Migration 4 → 5: Biology mode support.
         *
         * Changes:
         * - `dictionary_cache`: add `mode` TEXT (default 'english') and `bio_ext_data` TEXT (default '{}').
         *   Unique index changes from (word) to (word, mode) — requires full table recreate in SQLite.
         * - `favorite_words`: add `mode` TEXT; change PK from (word) to composite (word, mode) — recreate.
         * - `vocabulary_history`: add `mode` TEXT column (ALTER TABLE is sufficient, no unique index change).
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // ── dictionary_cache: recreate with mode + bio_ext_data + new unique index ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dictionary_cache_new` (
                        `id`                 INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `word`               TEXT    NOT NULL,
                        `mode`               TEXT    NOT NULL DEFAULT 'english',
                        `pronunciation`      TEXT    NOT NULL DEFAULT '',
                        `part_of_speech`     TEXT    NOT NULL DEFAULT '',
                        `short_meaning`      TEXT    NOT NULL DEFAULT '',
                        `detailed_meaning`   TEXT    NOT NULL DEFAULT '',
                        `hindi_meaning`      TEXT    NOT NULL DEFAULT '',
                        `hindi_pronunciation` TEXT   NOT NULL DEFAULT '',
                        `example_sentence`   TEXT    NOT NULL DEFAULT '',
                        `synonyms`           TEXT    NOT NULL DEFAULT '[]',
                        `antonyms`           TEXT    NOT NULL DEFAULT '[]',
                        `etymology`          TEXT    NOT NULL DEFAULT '',
                        `difficulty_level`   INTEGER NOT NULL DEFAULT 1,
                        `frequency_rating`   INTEGER NOT NULL DEFAULT 50,
                        `source`             TEXT    NOT NULL DEFAULT 'local',
                        `created_at`         INTEGER NOT NULL DEFAULT 0,
                        `last_accessed`      INTEGER NOT NULL DEFAULT 0,
                        `access_count`       INTEGER NOT NULL DEFAULT 0,
                        `is_favorite`        INTEGER NOT NULL DEFAULT 0,
                        `user_note`          TEXT    NOT NULL DEFAULT '',
                        `last_reviewed`      INTEGER,
                        `bio_ext_data`       TEXT    NOT NULL DEFAULT '{}'
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO dictionary_cache_new
                        (id, word, mode, pronunciation, part_of_speech, short_meaning, detailed_meaning,
                         hindi_meaning, hindi_pronunciation, example_sentence, synonyms, antonyms,
                         etymology, difficulty_level, frequency_rating, source, created_at,
                         last_accessed, access_count, is_favorite, user_note, last_reviewed, bio_ext_data)
                    SELECT id, word, 'english', pronunciation, part_of_speech, short_meaning, detailed_meaning,
                           hindi_meaning, hindi_pronunciation, example_sentence, synonyms, antonyms,
                           etymology, difficulty_level, frequency_rating, source, created_at,
                           last_accessed, access_count, is_favorite, user_note, last_reviewed, '{}'
                    FROM dictionary_cache
                """.trimIndent())

                db.execSQL("DROP TABLE IF EXISTS `dictionary_cache`")
                db.execSQL("ALTER TABLE `dictionary_cache_new` RENAME TO `dictionary_cache`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dictionary_cache_word_mode` ON `dictionary_cache` (`word`, `mode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_cache_frequency_rating` ON `dictionary_cache` (`frequency_rating`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_cache_difficulty_level` ON `dictionary_cache` (`difficulty_level`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_cache_mode` ON `dictionary_cache` (`mode`)")

                // ── favorite_words: recreate with composite PK (word, mode) ──────────────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `favorite_words_new` (
                        `word`     TEXT NOT NULL,
                        `mode`     TEXT NOT NULL DEFAULT 'english',
                        `added_at` INTEGER NOT NULL DEFAULT 0,
                        `notes`    TEXT,
                        PRIMARY KEY(`word`, `mode`)
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO favorite_words_new (word, mode, added_at, notes)
                    SELECT word, 'english', added_at, notes FROM favorite_words
                """.trimIndent())

                db.execSQL("DROP TABLE IF EXISTS `favorite_words`")
                db.execSQL("ALTER TABLE `favorite_words_new` RENAME TO `favorite_words`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_words_word_mode` ON `favorite_words` (`word`, `mode`)")

                // ── vocabulary_history: simple column addition ────────────────────────────
                db.execSQL("ALTER TABLE `vocabulary_history` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'english'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_history_mode` ON `vocabulary_history` (`mode`)")
            }
        }

        fun create(context: Context, scope: CoroutineScope): LexiDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LexiDatabase::class.java,
                DATABASE_NAME
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        try {
                            DatabaseSeeder.seed(db)
                            Log.i("LexiDatabase", "Seed completed successfully")
                        } catch (e: Exception) {
                            Log.e("LexiDatabase", "Seed failed — app will have no built-in words", e)
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            db.execSQL("PRAGMA cache_size = -2000")
                            db.execSQL("PRAGMA mmap_size = 268435456")
                        } catch (_: Exception) {}

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
