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
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao

    companion object {
        const val NAME = "vinplay.db"

        /** v2 adds per-channel userAgent/referrer for streams that require specific HTTP headers. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN userAgent TEXT")
                db.execSQL("ALTER TABLE channels ADD COLUMN referrer TEXT")
            }
        }
    }
}
