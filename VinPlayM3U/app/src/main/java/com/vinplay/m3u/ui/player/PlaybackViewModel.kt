package com.vinplay.m3u.ui.player

import androidx.lifecycle.ViewModel
import androidx.media3.exoplayer.ExoPlayer
import com.vinplay.m3u.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Exposes the app-level [PlaybackController] to the overlay UI. */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val controller: PlaybackController
) : ViewModel() {

    val nowPlaying = controller.state
    val playbackState = controller.playerManager.state

    fun exoPlayer(): ExoPlayer = controller.playerManager.getOrCreate()
    fun toggleFullscreen() = controller.toggleFullscreen()
    fun setFullscreen(full: Boolean) = controller.setFullscreen(full)
    fun close() = controller.close()
}
