package com.vinplay.m3u.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vinplay.m3u.data.local.dao.ChannelDao
import com.vinplay.m3u.data.local.dao.PlaylistDao
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.local.entity.PlaylistEntity

@Database(
    entities = [PlaylistEntity::class, ChannelEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao

    companion object {
        const val NAME = "vinplay.db"
        const val FTS_TABLE = "channels_fts"

        /** v2 adds per-channel userAgent/referrer for streams that require specific HTTP headers. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN userAgent TEXT")
                db.execSQL("ALTER TABLE channels ADD COLUMN referrer TEXT")
            }
        }

        /**
         * v3 adds the full-text search index.
         *
         * Searching with `name LIKE '%text%'` can't use an index, so every search scanned the whole
         * channels table — the reason search crawls on large libraries. [FTS_TABLE] is an external
         * content FTS4 table over `channels`, kept in sync by triggers, turning that scan into an
         * index lookup.
         *
         * It is deliberately *not* a Room entity: it's created here and queried with @RawQuery, so
         * Room never schema-validates it (extra tables are ignored) and there is no risk of a
         * DDL mismatch bricking an existing install. The table is created empty and filled in the
         * background by SearchIndexer, so upgrading a huge library doesn't block app start.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = createFts(db)
        }

        fun createFts(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE USING fts4(" +
                    "name, groupTitle, content=`channels`)"
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS channels_fts_ai AFTER INSERT ON channels BEGIN
                  INSERT INTO $FTS_TABLE(docid, name, groupTitle) VALUES (new.rowid, new.name, new.groupTitle);
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS channels_fts_ad AFTER DELETE ON channels BEGIN
                  DELETE FROM $FTS_TABLE WHERE docid = old.rowid;
                END
                """.trimIndent()
            )
            // Only re-index when searchable text changes: link testing rewrites testStatus
            // constantly and must not pay for reindexing.
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS channels_fts_au AFTER UPDATE OF name, groupTitle ON channels BEGIN
                  DELETE FROM $FTS_TABLE WHERE docid = old.rowid;
                  INSERT INTO $FTS_TABLE(docid, name, groupTitle) VALUES (new.rowid, new.name, new.groupTitle);
                END
                """.trimIndent()
            )
        }
    }
}
