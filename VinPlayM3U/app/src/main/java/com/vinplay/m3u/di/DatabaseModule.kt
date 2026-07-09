package com.vinplay.m3u.di

import android.content.Context
import androidx.room.Room
import com.vinplay.m3u.data.local.AppDatabase
import com.vinplay.m3u.data.local.dao.ChannelDao
import com.vinplay.m3u.data.local.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Foreign keys enforce cascade delete of channels with their playlist.
            .build()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()
}
