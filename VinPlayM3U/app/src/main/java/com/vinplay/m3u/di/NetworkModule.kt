package com.vinplay.m3u.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Shared client. Deliberately has NO overall `callTimeout`: it also streams remote M3U
     * downloads, which for a large playlist legitimately take much longer than any per-request
     * budget — an overall call-timeout here was aborting imports with a "timeout". Connect/read
     * timeouts still guard against a dead host or a stalled socket. The link tester layers its own
     * short 8s *per-call* timeout on top of this client so testing stays snappy.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
}
