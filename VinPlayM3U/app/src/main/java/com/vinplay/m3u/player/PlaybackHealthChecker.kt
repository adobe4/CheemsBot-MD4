package com.vinplay.m3u.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.vinplay.m3u.data.model.TestStatus
import com.vinplay.m3u.data.net.HttpDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep, playback-based reachability probe. Actually decodes the stream for a few seconds so it can
 * tell apart:
 *  - OK (green): reaches playback and keeps advancing,
 *  - UNSTABLE (yellow): starts, then freezes / repeatedly re-buffers within a few seconds,
 *  - DEAD (red): never starts, or errors.
 *
 * Runs on the main thread (ExoPlayer requirement) and reuses one player instance per call. This is
 * inherently slow (one stream at a time), so callers run it as an explicit opt-in.
 */
@Singleton
class PlaybackHealthChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @OptIn(UnstableApi::class)
    suspend fun check(url: String, userAgent: String?, referrer: String?): TestStatus =
        withContext(Dispatchers.Main) {
            val http = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(8_000)
            val headers = HashMap<String, String>()
            headers["User-Agent"] = userAgent?.takeIf { it.isNotBlank() } ?: HttpDefaults.USER_AGENT
            referrer?.takeIf { it.isNotBlank() }?.let { headers["Referer"] = it }
            http.setDefaultRequestProperties(headers)

            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(2_000, 10_000, 500, 1_000)
                        .build()
                )
                .build()

            try {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true

                val t0 = SystemClock.elapsedRealtime()
                var readyAt = -1L
                var startPos = 0L
                var rebuffers = 0
                var wasReady = false
                var result: TestStatus? = null

                while (result == null) {
                    delay(400)
                    val elapsed = SystemClock.elapsedRealtime() - t0
                    when {
                        player.playerError != null -> result = if (wasReady) TestStatus.UNSTABLE else TestStatus.DEAD
                        player.playbackState == Player.STATE_ENDED -> result = if (wasReady) TestStatus.OK else TestStatus.DEAD
                        !wasReady -> {
                            if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                                wasReady = true
                                readyAt = elapsed
                                startPos = player.currentPosition
                            } else if (elapsed > 8_000) {
                                result = TestStatus.DEAD // never started
                            }
                        }
                        else -> {
                            if (player.playbackState == Player.STATE_BUFFERING) rebuffers++
                            if (elapsed - readyAt >= 5_000) {
                                val advanced = player.currentPosition - startPos
                                // Started but barely progressed, or kept re-buffering => frozen/unstable.
                                result = if (rebuffers >= 2 || advanced < 1_500) TestStatus.UNSTABLE else TestStatus.OK
                            } else if (elapsed > 15_000) {
                                result = TestStatus.UNSTABLE
                            }
                        }
                    }
                }
                result
            } catch (t: Throwable) {
                TestStatus.ERROR
            } finally {
                player.release()
            }
        }
}
