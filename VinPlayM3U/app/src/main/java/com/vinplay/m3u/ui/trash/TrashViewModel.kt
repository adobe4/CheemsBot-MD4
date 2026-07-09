package com.vinplay.m3u.ui.trash

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.repository.ChannelRepository
import com.vinplay.m3u.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: ChannelRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: Long = savedStateHandle.get<Long>(Destinations.ARG_PLAYLIST_ID) ?: 0L

    data class UiState(
        val loading: Boolean = true,
        val channels: List<ChannelEntity> = emptyList(),
        val endReached: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init { reload() }

    private fun reload() = viewModelScope.launch {
        _ui.value = _ui.value.copy(loading = true)
        val page = repository.trashPage(playlistId, PAGE, 0)
        _ui.value = UiState(loading = false, channels = page, endReached = page.size < PAGE)
    }

    fun loadMore() = viewModelScope.launch {
        if (_ui.value.endReached) return@launch
        val offset = _ui.value.channels.size
        val page = repository.trashPage(playlistId, PAGE, offset)
        _ui.value = _ui.value.copy(
            channels = _ui.value.channels + page,
            endReached = page.size < PAGE
        )
    }

    fun restore(id: Long) = viewModelScope.launch {
        repository.restore(listOf(id), playlistId)
        reload()
    }

    fun restoreAll() = viewModelScope.launch {
        repository.restore(repository.trashIds(playlistId), playlistId)
        reload()
    }

    fun deleteForever(id: Long) = viewModelScope.launch {
        repository.hardDelete(listOf(id))
        reload()
    }

    fun emptyTrash() = viewModelScope.launch {
        repository.emptyTrash(playlistId)
        reload()
    }

    companion object { const val PAGE = 100 }
}
