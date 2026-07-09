package com.vinplay.m3u.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.repository.ChannelRepository
import com.vinplay.m3u.player.PlayerManager
import com.vinplay.m3u.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val channelRepository: ChannelRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val channelId: Long = savedStateHandle.get<Long>(Destinations.ARG_CHANNEL_ID) ?: 0L

    private val _channel = MutableStateFlow<ChannelEntity?>(null)
    val channel = _channel.asStateFlow()

    val playbackState = playerManager.state

    init {
        viewModelScope.launch {
            val ch = channelRepository.get(channelId)
            _channel.value = ch
            if (ch != null) playerManager.play(ch.url, ch.userAgent, ch.referrer)
        }
    }

    fun exoPlayer(): ExoPlayer = playerManager.getOrCreate()

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
