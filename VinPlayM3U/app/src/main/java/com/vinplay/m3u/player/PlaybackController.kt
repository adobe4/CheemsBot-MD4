package com.vinplay.m3u.player

import com.vinplay.m3u.data.local.entity.ChannelEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level "now playing" state so a channel can play in a small docked window that stays up while
 * the user keeps browsing/editing other channels, and expand to fullscreen only when they choose.
 * Backed by the shared [PlayerManager] (single ExoPlayer instance reused between mini and full).
 */
@Singleton
class PlaybackController @Inject constructor(
    val playerManager: PlayerManager
) {
    data class NowPlaying(
        val channelId: Long,
        val title: String,
        val logo: String?,
        val fullscreen: Boolean = false
    )

    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    fun play(channel: ChannelEntity) {
        // Keep fullscreen if we were already fullscreen (channel-surf without shrinking).
        val wasFullscreen = _state.value?.fullscreen ?: false
        _state.value = NowPlaying(channel.id, channel.name, channel.tvgLogo, wasFullscreen)
        playerManager.play(channel.url, channel.userAgent, channel.referrer)
    }

    fun toggleFullscreen() = _state.update { it?.copy(fullscreen = !it.fullscreen) }

    fun setFullscreen(full: Boolean) = _state.update { it?.copy(fullscreen = full) }

    fun close() {
        _state.value = null
        playerManager.release()
    }
}
