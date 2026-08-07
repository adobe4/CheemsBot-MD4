package com.vinplay.m3u.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vinplay.m3u.ui.allchannels.AllChannelsScreen
import com.vinplay.m3u.ui.channels.ChannelsScreen
import com.vinplay.m3u.ui.importer.ImportScreen
import com.vinplay.m3u.ui.navigation.Destinations
import com.vinplay.m3u.ui.player.PlaybackOverlay
import com.vinplay.m3u.ui.playlists.PlaylistsScreen
import com.vinplay.m3u.ui.trash.TrashScreen

/** Single-activity Compose navigation graph, with the app-level playback overlay on top. */
@Composable
fun VinPlayRoot() {
    val navController = rememberNavController()

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Destinations.PLAYLISTS) {
            composable(Destinations.PLAYLISTS) {
                PlaylistsScreen(
                    onOpenPlaylist = { navController.navigate(Destinations.channels(it)) },
                    onImportInto = { navController.navigate(Destinations.import(it)) },
                    onOpenAllChannels = { navController.navigate(Destinations.ALL_CHANNELS) }
                )
            }

            composable(Destinations.ALL_CHANNELS) {
                AllChannelsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Destinations.CHANNELS,
                arguments = listOf(navArgument(Destinations.ARG_PLAYLIST_ID) { type = NavType.LongType })
            ) {
                ChannelsScreen(
                    onBack = { navController.popBackStack() },
                    onImport = { navController.navigate(Destinations.import(it)) },
                    onOpenTrash = { navController.navigate(Destinations.trash(it)) }
                )
            }

            composable(
                Destinations.IMPORT,
                arguments = listOf(navArgument(Destinations.ARG_PLAYLIST_ID) { type = NavType.LongType })
            ) {
                ImportScreen(onDone = { navController.popBackStack() })
            }

            composable(
                Destinations.TRASH,
                arguments = listOf(navArgument(Destinations.ARG_PLAYLIST_ID) { type = NavType.LongType })
            ) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
        }

        // Mini/fullscreen player floats above whatever screen is showing.
        PlaybackOverlay()
    }
}
