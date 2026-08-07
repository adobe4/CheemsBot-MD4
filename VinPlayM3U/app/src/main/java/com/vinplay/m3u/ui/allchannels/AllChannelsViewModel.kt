package com.vinplay.m3u.ui.allchannels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinplay.m3u.data.local.PlaylistSummary
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.model.ChannelKind
import com.vinplay.m3u.data.repository.ChannelFilter
import com.vinplay.m3u.data.repository.ChannelRepository
import com.vinplay.m3u.data.repository.PlaylistRepository
import com.vinplay.m3u.player.PlaybackController
import com.vinplay.m3u.task.TaskService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The global "All channels" hub: browse/search/test/select channels from every playlist. */
@HiltViewModel
class AllChannelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    companion object {
        const val PAGE_FIRST = 200
        const val PAGE_NEXT = 200
    }

    data class UiState(
        val loading: Boolean = true,
        val channels: List<ChannelEntity> = emptyList(),
        val totalFiltered: Int = 0,
        val endReached: Boolean = false,
        val filter: ChannelFilter = ChannelFilter(),
        val playlists: List<PlaylistSummary> = emptyList()
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    val testProgress = channelRepository.testProgress

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    private val filterFlow = MutableStateFlow(ChannelFilter())
    private var loadJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCount() = viewModelScope.launch {
        filterFlow.flatMapLatest { channelRepository.globalCount(it) }
            .collectLatest { _ui.value = _ui.value.copy(totalFiltered = it) }
    }

    init {
        observeCount()
        viewModelScope.launch {
            playlistRepository.summaries().collectLatest { _ui.value = _ui.value.copy(playlists = it) }
        }
        reload()
    }

    fun setQuery(q: String) = updateFilter { it.copy(query = q) }
    fun setKind(kind: ChannelKind?) = updateFilter { it.copy(kind = kind) }

    private inline fun updateFilter(transform: (ChannelFilter) -> ChannelFilter) {
        val next = transform(_ui.value.filter)
        _ui.value = _ui.value.copy(filter = next)
        filterFlow.value = next
        reload()
    }

    private fun reload() {
        loadJob?.cancel()
        val keep = _ui.value.channels.size.coerceAtLeast(PAGE_FIRST)
        loadJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val page = channelRepository.pageGlobal(_ui.value.filter, keep, 0)
            _ui.value = _ui.value.copy(channels = page, loading = false, endReached = page.size < keep)
        }
    }

    fun refresh() = reload()

    fun loadMore() {
        if (_ui.value.loading || _ui.value.endReached) return
        val offset = _ui.value.channels.size
        viewModelScope.launch {
            val page = channelRepository.pageGlobal(_ui.value.filter, PAGE_NEXT, offset)
            _ui.value = _ui.value.copy(channels = _ui.value.channels + page, endReached = page.size < PAGE_NEXT)
        }
    }

    // Selection
    fun toggleSelect(id: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }
    fun selectAllLoaded() { _selectedIds.value = _ui.value.channels.map { it.id }.toSet() }
    fun clearSelection() { _selectedIds.value = emptySet() }

    fun play(channel: ChannelEntity) = playbackController.play(channel)

    fun moveSelectedToPlaylist(targetPlaylistId: Long) = viewModelScope.launch {
        channelRepository.moveManyToPlaylistGlobal(_selectedIds.value.toList(), targetPlaylistId)
        clearSelection()
        reload()
    }

    fun moveSelectedToNewPlaylist(name: String) = viewModelScope.launch {
        val newId = playlistRepository.create(name)
        channelRepository.moveManyToPlaylistGlobal(_selectedIds.value.toList(), newId)
        clearSelection()
        reload()
    }

    fun testFiltered() = TaskService.startTestGlobal(context, _ui.value.filter)
    fun deepTestFiltered() = TaskService.startPlaybackTestGlobal(context, _ui.value.filter)
}
