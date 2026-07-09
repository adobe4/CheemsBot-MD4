package com.vinplay.m3u.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.vinplay.m3u.data.local.PlaylistSummary
import com.vinplay.m3u.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    /** Playlists with a non-deleted channel count, newest activity first. */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, p.updatedAt AS updatedAt,
               (SELECT COUNT(*) FROM channels c
                 WHERE c.playlistId = p.id AND c.deletedAt IS NULL) AS channelCount
        FROM playlists p
        ORDER BY p.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>
}
