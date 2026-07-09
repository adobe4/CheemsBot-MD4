package com.vinplay.m3u.data.repository

import com.vinplay.m3u.data.local.dao.ChannelDao
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.model.TestStatus
import com.vinplay.m3u.data.net.LinkTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val playlistRepository: PlaylistRepository,
    private val linkTester: LinkTester
) {
    // ---- Reads ----

    suspend fun page(playlistId: Long, filter: ChannelFilter, limit: Int, offset: Int): List<ChannelEntity> =
        channelDao.pageFiltered(playlistId, filter.query, filter.group, filter.kindName, limit, offset)

    fun filteredCount(playlistId: Long, filter: ChannelFilter): Flow<Int> =
        channelDao.observeFilteredCount(playlistId, filter.query, filter.group, filter.kindName)

    fun groups(playlistId: Long): Flow<List<String>> = channelDao.observeGroups(playlistId)

    suspend fun get(id: Long): ChannelEntity? = channelDao.getById(id)

    fun trashCount(playlistId: Long): Flow<Int> = channelDao.observeTrashCount(playlistId)

    suspend fun trashPage(playlistId: Long, limit: Int, offset: Int): List<ChannelEntity> =
        channelDao.pageTrash(playlistId, limit, offset)

    // ---- Edits ----

    suspend fun update(channel: ChannelEntity) {
        channelDao.update(channel)
        playlistRepository.touch(channel.playlistId)
    }

    suspend fun updateUrl(id: Long, url: String, playlistId: Long) {
        channelDao.updateUrl(id, url.trim())
        playlistRepository.touch(playlistId)
    }

    suspend fun moveToGroup(id: Long, group: String, playlistId: Long) {
        channelDao.updateGroup(id, group.trim())
        playlistRepository.touch(playlistId)
    }

    suspend fun moveToPlaylist(id: Long, targetPlaylistId: Long, sourcePlaylistId: Long) {
        val order = channelDao.maxOrderIndex(targetPlaylistId) + 1
        channelDao.moveToPlaylist(id, targetPlaylistId, order)
        playlistRepository.touch(targetPlaylistId)
        playlistRepository.touch(sourcePlaylistId)
    }

    suspend fun renameGroup(playlistId: Long, oldName: String, newName: String) {
        channelDao.renameGroup(playlistId, oldName, newName.trim())
        playlistRepository.touch(playlistId)
    }

    /** Persist a new ordering for a contiguous slice the user dragged. */
    suspend fun reorder(pairs: List<Pair<Long, Long>>, playlistId: Long) {
        channelDao.applyReorder(pairs)
        playlistRepository.touch(playlistId)
    }

    // ---- Soft delete / trash ----

    suspend fun softDelete(ids: List<Long>, playlistId: Long) {
        channelDao.softDelete(ids, System.currentTimeMillis())
        playlistRepository.touch(playlistId)
    }

    suspend fun restore(ids: List<Long>, playlistId: Long) {
        channelDao.restore(ids)
        playlistRepository.touch(playlistId)
    }

    suspend fun hardDelete(ids: List<Long>) = channelDao.hardDelete(ids)

    suspend fun emptyTrash(playlistId: Long) = channelDao.emptyTrash(playlistId)

    suspend fun trashIds(playlistId: Long): List<Long> = channelDao.trashIds(playlistId)

    /** Soft-delete every channel matching the current filter (bulk action). Returns affected ids for undo. */
    suspend fun softDeleteFiltered(playlistId: Long, filter: ChannelFilter): List<Long> {
        val ids = channelDao.idsFiltered(playlistId, filter.query, filter.group, filter.kindName)
        if (ids.isNotEmpty()) softDelete(ids, playlistId)
        return ids
    }

    // ---- Link testing ----

    /** Progress of an in-flight "test all" run. */
    data class TestProgress(val done: Int, val total: Int, val running: Boolean)

    private val _testProgress = MutableStateFlow(TestProgress(0, 0, false))
    val testProgress: StateFlow<TestProgress> = _testProgress.asStateFlow()

    /**
     * Test every channel matching [filter]. Runs up to [concurrency] probes at once via a
     * semaphore; results are written back per-channel so the UI reflects them live.
     */
    suspend fun testFiltered(playlistId: Long, filter: ChannelFilter, concurrency: Int = 10) {
        val ids = channelDao.idsFiltered(playlistId, filter.query, filter.group, filter.kindName)
        if (ids.isEmpty()) return
        val semaphore = Semaphore(concurrency)
        _testProgress.value = TestProgress(0, ids.size, true)
        val done = AtomicInteger(0)
        withContext(Dispatchers.IO) {
            coroutineScope {
                for (id in ids) {
                    launch {
                        semaphore.withPermit {
                            val channel = channelDao.getById(id) ?: return@withPermit
                            channelDao.markStatus(listOf(id), TestStatus.TESTING)
                            val result = linkTester.test(channel.url)
                            channelDao.updateTestResult(id, result.status, result.code, System.currentTimeMillis())
                        }
                        _testProgress.value = TestProgress(done.incrementAndGet(), ids.size, true)
                    }
                }
            }
        }
        _testProgress.value = TestProgress(done.get(), ids.size, false)
    }
}
