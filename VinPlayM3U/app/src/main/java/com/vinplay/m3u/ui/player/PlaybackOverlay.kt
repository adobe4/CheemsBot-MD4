package com.vinplay.m3u.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.vinplay.m3u.player.PlayerManager

/**
 * App-level playback overlay: a small docked window while browsing, expandable to fullscreen.
 * A single [PlayerView] is reused across both states (the ExoPlayer keeps playing on toggle).
 */
@Composable
fun PlaybackOverlay(viewModel: PlaybackViewModel = hiltViewModel()) {
    val now by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle(
        initialValue = PlayerManager.PlaybackState()
    )
    val target = now ?: return
    val fullscreen = target.fullscreen

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = if (fullscreen) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier.align(Alignment.BottomEnd).padding(12.dp).width(260.dp)
            }
        ) {
            if (fullscreen) {
                AndroidView(
                    factory = { PlayerView(it).apply { useController = true } },
                    update = { it.player = viewModel.exoPlayer(); it.useController = true },
                    modifier = Modifier.fillMaxSize()
                )
                if (playback.isBuffering) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    IconButton(onClick = { viewModel.setFullscreen(false) }) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Shrink", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.close() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            } else {
                Card {
                    Box {
                        AndroidView(
                            factory = { PlayerView(it).apply { useController = false } },
                            update = { it.player = viewModel.exoPlayer(); it.useController = false },
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                        )
                        if (playback.isBuffering) {
                            CircularProgressIndicator(
                                Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            target.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.setFullscreen(true) }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen")
                        }
                        IconButton(onClick = { viewModel.close() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
            }
        }
    }
}
