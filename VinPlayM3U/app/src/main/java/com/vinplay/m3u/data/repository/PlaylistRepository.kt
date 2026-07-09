package com.vinplay.m3u.data.repository

import com.vinplay.m3u.data.local.PlaylistSummary
import com.vinplay.m3u.data.local.dao.PlaylistDao
import com.vinplay.m3u.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    fun summaries(): Flow<List<PlaylistSummary>> = playlistDao.observeSummaries()

    fun observe(id: Long): Flow<PlaylistEntity?> = playlistDao.observeById(id)

    suspend fun get(id: Long): PlaylistEntity? = playlistDao.getById(id)

    suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return playlistDao.insert(
            PlaylistEntity(name = name.trim().ifBlank { "Untitled playlist" }, createdAt = now, updatedAt = now)
        )
    }

    suspend fun rename(id: Long, name: String) =
        playlistDao.rename(id, name.trim().ifBlank { "Untitled playlist" }, System.currentTimeMillis())

    suspend fun touch(id: Long) = playlistDao.touch(id, System.currentTimeMillis())

    suspend fun delete(id: Long) = playlistDao.delete(id)
}
