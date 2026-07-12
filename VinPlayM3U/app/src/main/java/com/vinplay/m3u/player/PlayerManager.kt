package com.vinplay.m3u.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.vinplay.m3u.data.net.HttpDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin lifecycle-friendly wrapper around a single [ExoPlayer]. Media3's default source factory
 * resolves HLS, DASH, SmoothStreaming, progressive and MPEG-TS from the URI/response, so callers
 * just hand it a URL.
 *
 * Crucially the player is built on an HTTP data source that:
 *  - sends a VLC-style User-Agent (many IPTV portals reject the stock ExoPlayer one), and
 *  - allows cross-protocol redirects (http<->https), which ExoPlayer disables by default. IPTV
 *    portals very commonly answer on http and 302 to https (or vice-versa); without this the
 *    stream fails even though it plays fine in VLC and other players.
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
            _state.value = _state.value.copy(error = "${error.errorCodeName} (${error.errorCode})")
        }
    }

    // Held so per-channel headers can be swapped in before each prepare(). The MediaSourceFactory
    // reads this same instance's default request properties when it creates a data source, so
    // updating them here takes effect on the next stream without rebuilding the player.
    @OptIn(UnstableApi::class)
    private val httpFactory: DefaultHttpDataSource.Factory by lazy {
        DefaultHttpDataSource.Factory()
            .setUserAgent(HttpDefaults.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaSourceFactory(): MediaSource.Factory {
        // DefaultDataSource also handles file:// / content:// for local media.
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        // Xtream-style live URLs (http://host:port/user/pass/id) carry no extension, so ExoPlayer
        // falls back to progressive + the TS extractor. Live MPEG-TS frequently starts mid-GOP
        // (no leading IDR) and needs constant-bitrate seeking; these flags make ExoPlayer tolerant
        // of that the same way TiviMate / IPTV Smarters do, instead of erroring out.
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            .setConstantBitrateSeekingEnabled(true)

        return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
    }

    /**
     * A lean LoadControl: small buffers so live channels start fast and the player stays light on
     * memory (the default 50s buffer is wasteful for zapping between channels), while still keeping
     * enough to ride out jitter.
     */
    @OptIn(UnstableApi::class)
    private fun buildLoadControl(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 20_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

    /** Returns the shared player, creating it on first use. */
    @OptIn(UnstableApi::class)
    fun getOrCreate(): ExoPlayer =
        player ?: ExoPlayer.Builder(context)
            .setMediaSourceFactory(buildMediaSourceFactory())
            .setLoadControl(buildLoadControl())
            .build()
            .also {
                it.addListener(listener)
                player = it
            }

    @OptIn(UnstableApi::class)
    fun play(url: String, userAgent: String? = null, referrer: String? = null) {
        // Apply this channel's required headers (or the VLC default) before preparing.
        val headers = HashMap<String, String>()
        headers["User-Agent"] = userAgent?.takeIf { it.isNotBlank() } ?: HttpDefaults.USER_AGENT
        referrer?.takeIf { it.isNotBlank() }?.let { headers["Referer"] = it }
        httpFactory.setDefaultRequestProperties(headers)

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
