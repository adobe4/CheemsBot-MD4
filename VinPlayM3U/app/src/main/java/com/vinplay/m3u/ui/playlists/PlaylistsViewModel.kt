package com.vinplay.m3u.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinplay.m3u.data.local.PlaylistSummary
import com.vinplay.m3u.data.local.entity.PlaylistEntity
import com.vinplay.m3u.data.repository.ChannelRepository
import com.vinplay.m3u.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    val playlists = repository.summaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Holds a just-deleted playlist so the snackbar can restore it (name + channels are gone,
     *  but recreating the shell lets the user re-import). */
    var lastDeleted: PlaylistEntity? = null
        private set

    fun create(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(repository.create(name))
    }

    fun rename(id: Long, name: String) = viewModelScope.launch { repository.rename(id, name) }

    fun duplicate(id: Long) = viewModelScope.launch { channelRepository.duplicatePlaylist(id) }

    fun delete(id: Long) = viewModelScope.launch {
        lastDeleted = repository.get(id)
        repository.delete(id)
    }

    fun undoDelete() = viewModelScope.launch {
        val prev = lastDeleted ?: return@launch
        repository.create(prev.name)
        lastDeleted = null
    }
}
