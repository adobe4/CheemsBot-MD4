package com.vinplay.m3u.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin lifecycle-friendly wrapper around a single [ExoPlayer]. Media3's default source factory
 * already resolves HLS, DASH, SmoothStreaming, progressive and MPEG-TS from the URI/response,
 * so callers just hand it a URL. The player is created lazily and released on [release].
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: ExoPlayer? = null

    data class PlaybackState(
        val isBuffering: Boolean = false,
        val isPlaying: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = error.errorCodeName)
        }
    }

    /** Returns the shared player, creating it on first use. */
    fun getOrCreate(): ExoPlayer =
        player ?: ExoPlayer.Builder(context).build().also {
            it.addListener(listener)
            player = it
        }

    fun play(url: String) {
        val exo = getOrCreate()
        _state.value = PlaybackState(isBuffering = true)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    fun pause() { player?.playWhenReady = false }
    fun resume() { player?.playWhenReady = true }

    fun release() {
        player?.removeListener(listener)
        player?.release()
        player = null
        _state.value = PlaybackState()
    }
}
