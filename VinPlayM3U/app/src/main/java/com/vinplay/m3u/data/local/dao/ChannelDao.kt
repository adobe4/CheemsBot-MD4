package com.vinplay.m3u.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.model.TestStatus
import kotlinx.coroutines.flow.Flow

/**
 * All channel access. Filters use nullable/empty bind parameters so a single query serves
 * search + group + kind combinations, and every listing query is LIMIT/OFFSET paged to keep
 * the working set bounded regardless of playlist size.
 */
@Dao
interface ChannelDao {

    // ---- Insert (import) ----

    /** Batched insert; the import pipeline calls this in chunks of ~500 inside a transaction. */
    @Insert
    suspend fun insertAll(channels: List<ChannelEntity>): List<Long>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM channels WHERE playlistId = :playlistId")
    suspend fun maxOrderIndex(playlistId: Long): Long

    // ---- Paged listing (active) ----

    @Query(
        """
        SELECT * FROM channels
        WHERE playlistId = :playlistId
          AND deletedAt IS NULL
          AND (:group IS NULL OR groupTitle = :group)
          AND (:kind IS NULL OR kind = :kind)
          AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY orderIndex ASC, id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pageFiltered(
        playlistId: Long,
        query: String,
        group: String?,
        kind: String?,
        limit: Int,
        offset: Int
    ): List<ChannelEntity>

    @Query(
        """
        SELECT COUNT(*) FROM channels
        WHERE playlistId = :playlistId
          AND deletedAt IS NULL
          AND (:group IS NULL OR groupTitle = :group)
          AND (:kind IS NULL OR kind = :kind)
          AND (:query = '' OR name LIKE '%' || :query || '%')
        """
    )
    fun observeFilteredCount(
        playlistId: Long,
        query: String,
        group: String?,
        kind: String?
    ): Flow<Int>

    /** Ids matching the current filter, for bulk operations (test/delete all filtered). */
    @Query(
        """
        SELECT id FROM channels
        WHERE playlistId = :playlistId
          AND deletedAt IS NULL
          AND (:group IS NULL OR groupTitle = :group)
          AND (:kind IS NULL OR kind = :kind)
          AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY orderIndex ASC, id ASC
        """
    )
    suspend fun idsFiltered(
        playlistId: Long,
        query: String,
        group: String?,
        kind: String?
    ): List<Long>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE playlistId = :playlistId AND deletedAt IS NULL ORDER BY groupTitle ASC")
    fun observeGroups(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ChannelEntity>

    /** Full active set streamed for export; caller pages via limit/offset to avoid OOM. */
    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND deletedAt IS NULL " +
            "ORDER BY orderIndex ASC, id ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun pageForExport(playlistId: Long, limit: Int, offset: Int): List<ChannelEntity>

    // ---- Edit ----

    @Update
    suspend fun update(channel: ChannelEntity)

    @Query("UPDATE channels SET url = :url WHERE id = :id")
    suspend fun updateUrl(id: Long, url: String)

    @Query("UPDATE channels SET groupTitle = :group WHERE id = :id")
    suspend fun updateGroup(id: Long, group: String)

    @Query("UPDATE channels SET playlistId = :targetPlaylistId, orderIndex = :orderIndex WHERE id = :id")
    suspend fun moveToPlaylist(id: Long, targetPlaylistId: Long, orderIndex: Long)

    @Query("UPDATE channels SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun setOrderIndex(id: Long, orderIndex: Long)

    @Transaction
    suspend fun applyReorder(pairs: List<Pair<Long, Long>>) {
        for ((id, order) in pairs) setOrderIndex(id, order)
    }

    /** Rename a group across a whole playlist in one statement. */
    @Query("UPDATE channels SET groupTitle = :newName WHERE playlistId = :playlistId AND groupTitle = :oldName AND deletedAt IS NULL")
    suspend fun renameGroup(playlistId: Long, oldName: String, newName: String)

    // ---- Test results ----

    @Query("UPDATE channels SET testStatus = :status WHERE id IN (:ids)")
    suspend fun markStatus(ids: List<Long>, status: TestStatus)

    @Query("UPDATE channels SET testStatus = :status, testStatusCode = :code, testCheckedAt = :now WHERE id = :id")
    suspend fun updateTestResult(id: Long, status: TestStatus, code: Int?, now: Long)

    // ---- Soft delete / trash ----

    @Query("UPDATE channels SET deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)

    @Query("UPDATE channels SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM channels WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<Long>)

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND deletedAt IS NOT NULL " +
            "ORDER BY deletedAt DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun pageTrash(playlistId: Long, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND deletedAt IS NOT NULL")
    fun observeTrashCount(playlistId: Long): Flow<Int>

    @Query("DELETE FROM channels WHERE playlistId = :playlistId AND deletedAt IS NOT NULL")
    suspend fun emptyTrash(playlistId: Long)

    @Query("SELECT id FROM channels WHERE playlistId = :playlistId AND deletedAt IS NOT NULL")
    suspend fun trashIds(playlistId: Long): List<Long>
}
