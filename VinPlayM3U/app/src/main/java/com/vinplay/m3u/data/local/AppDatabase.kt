package com.vinplay.m3u.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vinplay.m3u.data.local.dao.ChannelDao
import com.vinplay.m3u.data.local.dao.PlaylistDao
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.local.entity.PlaylistEntity

@Database(
    entities = [PlaylistEntity::class, ChannelEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao

    companion object {
        const val NAME = "vinplay.db"
    }
}
