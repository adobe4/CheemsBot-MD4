package com.vinplay.m3u.ui.channels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinplay.m3u.data.local.PlaylistSummary
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.model.ChannelKind
import com.vinplay.m3u.data.repository.ChannelFilter
import com.vinplay.m3u.data.repository.ChannelRepository
import com.vinplay.m3u.data.repository.ExportManager
import com.vinplay.m3u.data.repository.PlaylistRepository
import com.vinplay.m3u.player.PlaybackController
import com.vinplay.m3u.task.TaskService
import com.vinplay.m3u.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository,
    private val exportManager: ExportManager,
    private val playbackController: PlaybackController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Play a channel in the app-level mini player (stays up while browsing). */
    fun play(channel: ChannelEntity) = playbackController.play(channel)

    companion object {
        const val PAGE_FIRST = 200
        const val PAGE_NEXT = 200
    }

    private val playlistId: Long = savedStateHandle.get<Long>(Destinations.ARG_PLAYLIST_ID) ?: 0L

    data class UiState(
        val playlistId: Long = 0L,
        val playlistName: String = "",
        val loading: Boolean = true,
        val channels: List<ChannelEntity> = emptyList(),
        val totalFiltered: Int = 0,
        val endReached: Boolean = false,
        val groups: List<String> = emptyList(),
        val filter: ChannelFilter = ChannelFilter(),
        val otherPlaylists: List<PlaylistSummary> = emptyList(),
        val trashCount: Int = 0
    )

    private val _ui = MutableStateFlow(UiState(playlistId = playlistId))
    val ui = _ui.asStateFlow()

    val testProgress = channelRepository.testProgress

    private val filterFlow = MutableStateFlow(ChannelFilter())
    private var loadJob: Job? = null

    /**
     * One debounced count per filter instead of a live Flow that re-counted on every database
     * change — counting is a full query and was competing with the search itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCount() {
        viewModelScope.launch {
            filterFlow.collectLatest { f ->
                delay(250)
                _ui.value = _ui.value.copy(totalFiltered = channelRepository.filteredCountOnce(playlistId, f))
            }
        }
    }

    init {
        // Reactive side-channels: count, groups, trash count, playlist name, sibling playlists.
        observeCount()
        viewModelScope.launch {
            channelRepository.groups(playlistId).collectLatest { groups ->
                _ui.value = _ui.value.copy(groups = groups)
            }
        }
        viewModelScope.launch {
            channelRepository.trashCount(playlistId).collectLatest { c ->
                _ui.value = _ui.value.copy(trashCount = c)
            }
        }
        viewModelScope.launch {
            playlistRepository.observe(playlistId).collectLatest { p ->
                _ui.value = _ui.value.copy(playlistName = p?.name ?: "")
            }
        }
        viewModelScope.launch {
            playlistRepository.summaries().collectLatest { all ->
                _ui.value = _ui.value.copy(otherPlaylists = all.filter { it.id != playlistId })
            }
        }
        reload(reset = true)
    }

    // ---- Filtering ----

    fun setQuery(q: String) = updateFilter { it.copy(query = q) }
    fun setGroup(group: String?) = updateFilter { it.copy(group = group) }
    fun setKind(kind: ChannelKind?) = updateFilter { it.copy(kind = kind) }

    private inline fun updateFilter(transform: (ChannelFilter) -> ChannelFilter) {
        val next = transform(_ui.value.filter)
        _ui.value = _ui.value.copy(filter = next)
        filterFlow.value = next
        reload(reset = true)
    }

    // ---- Paging ----

    private fun reload(reset: Boolean) {
        loadJob?.cancel()
        val keep = if (reset) PAGE_FIRST else _ui.value.channels.size.coerceAtLeast(PAGE_FIRST)
        loadJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val page = channelRepository.page(playlistId, _ui.value.filter, keep, 0)
            _ui.value = _ui.value.copy(
                channels = page,
                loading = false,
                endReached = page.size < keep
            )
        }
    }

    /** Re-query the currently loaded window (used after background test updates statuses). */
    fun refresh() = reload(reset = false)

    fun loadMore() {
        if (_ui.value.loading || _ui.value.endReached) return
        val offset = _ui.value.channels.size
        viewModelScope.launch {
            val page = channelRepository.page(playlistId, _ui.value.filter, PAGE_NEXT, offset)
            _ui.value = _ui.value.copy(
                channels = _ui.value.channels + page,
                endReached = page.size < PAGE_NEXT
            )
        }
    }

    // ---- Undo bookkeeping ----

    private var lastDeletedIds: List<Long> = emptyList()

    fun softDelete(id: Long) = viewModelScope.launch {
        lastDeletedIds = listOf(id)
        channelRepository.softDelete(listOf(id), playlistId)
        reload(reset = false)
    }

    fun deleteFiltered() = viewModelScope.launch {
        lastDeletedIds = channelRepository.softDeleteFiltered(playlistId, _ui.value.filter)
        reload(reset = true)
    }

    /** Scan the whole playlist for same-name + same-url duplicates and soft-delete the extras. */
    fun removeDuplicates(onDone: (Int) -> Unit) = viewModelScope.launch {
        lastDeletedIds = channelRepository.removeDuplicates(playlistId)
        reload(reset = false)
        onDone(lastDeletedIds.size)
    }

    fun undoDelete() = viewModelScope.launch {
        if (lastDeletedIds.isEmpty()) return@launch
        channelRepository.restore(lastDeletedIds, playlistId)
        lastDeletedIds = emptyList()
        reload(reset = false)
    }

    // ---- Edits ----

    fun updateChannel(updated: ChannelEntity) = viewModelScope.launch {
        channelRepository.update(updated)
        reload(reset = false)
    }

    fun updateUrl(id: Long, url: String) = viewModelScope.launch {
        channelRepository.updateUrl(id, url, playlistId)
        reload(reset = false)
    }

    fun moveToGroup(id: Long, group: String) = viewModelScope.launch {
        channelRepository.moveToGroup(id, group, playlistId)
        reload(reset = false)
    }

    fun moveToPlaylist(id: Long, targetPlaylistId: Long) = viewModelScope.launch {
        channelRepository.moveToPlaylist(id, targetPlaylistId, playlistId)
        reload(reset = false)
    }

    fun renameGroup(oldName: String, newName: String) = viewModelScope.launch {
        channelRepository.renameGroup(playlistId, oldName, newName)
        // If we were filtered to the old group, follow the rename.
        if (_ui.value.filter.group == oldName) setGroup(newName) else reload(reset = false)
    }

    /**
     * Persist a drag reorder. [from]/[to] are indices within the currently loaded window; we
     * rewrite the affected slice's orderIndex values to the swapped order.
     */
    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        val current = _ui.value.channels
        if (from !in current.indices || to !in current.indices || from == to) return@launch
        val mutable = current.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        // Optimistic UI update.
        _ui.value = _ui.value.copy(channels = mutable)
        // Recompute contiguous order indices for the touched range and persist.
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val pairs = (lo..hi).map { i -> mutable[i].id to current[lo + (i - lo)].orderIndex }
        channelRepository.reorder(pairs, playlistId)
    }

    // ---- Multi-select ----

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    fun toggleSelect(id: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    fun selectAllLoaded() {
        _selectedIds.value = _ui.value.channels.map { it.id }.toSet()
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return@launch
        lastDeletedIds = ids
        channelRepository.softDelete(ids, playlistId)
        clearSelection()
        reload(reset = false)
    }

    fun moveSelectedToGroup(group: String) = viewModelScope.launch {
        channelRepository.moveManyToGroup(_selectedIds.value.toList(), group, playlistId)
        clearSelection()
        reload(reset = false)
    }

    fun moveSelectedToPlaylist(targetPlaylistId: Long) = viewModelScope.launch {
        channelRepository.moveManyToPlaylist(_selectedIds.value.toList(), targetPlaylistId, playlistId)
        clearSelection()
        reload(reset = false)
    }

    fun copySelectedToPlaylist(targetPlaylistId: Long, onDone: (Int) -> Unit) = viewModelScope.launch {
        val ids = _selectedIds.value.toList()
        channelRepository.copyManyToPlaylist(ids, targetPlaylistId)
        clearSelection()
        onDone(ids.size)
    }

    // ---- Batch name edit / kind ----

    /** Find & replace across names. [scopeToFilter]=false hits the whole playlist. */
    fun replaceInNames(find: String, replacement: String, scopeToFilter: Boolean, onDone: (Int) -> Unit) =
        viewModelScope.launch {
            val changed = channelRepository.replaceInNames(
                playlistId, find, replacement, _ui.value.filter, scopeToFilter
            )
            reload(reset = false)
            onDone(changed)
        }

    /** Set kind for every channel matching the current filter. */
    fun setKindForFiltered(kind: ChannelKind, onDone: (Int) -> Unit) = viewModelScope.launch {
        val changed = channelRepository.setKindFiltered(playlistId, _ui.value.filter, kind)
        reload(reset = false)
        onDone(changed)
    }

    // ---- Bulk test ----

    /** Runs in the foreground service so it survives leaving the screen and shows a notification. */
    fun testAllFiltered() {
        TaskService.startTest(context, playlistId, _ui.value.filter)
    }

    /** Deep (playback) test — detects streams that start then freeze and marks them yellow. */
    fun deepTestFiltered() {
        TaskService.startPlaybackTest(context, playlistId, _ui.value.filter)
    }

    // ---- Export ----

    fun export(uri: Uri, onResult: (Int?) -> Unit) = viewModelScope.launch {
        val count = runCatching {
            val out = context.contentResolver.openOutputStream(uri) ?: error("no stream")
            out.use { exportManager.export(playlistId, it) }
        }.getOrNull()
        onResult(count)
    }
}
