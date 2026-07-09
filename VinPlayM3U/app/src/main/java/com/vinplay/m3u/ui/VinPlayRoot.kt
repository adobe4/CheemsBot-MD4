package com.vinplay.m3u.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vinplay.m3u.ui.channels.ChannelsScreen
import com.vinplay.m3u.ui.importer.ImportScreen
import com.vinplay.m3u.ui.navigation.Destinations
import com.vinplay.m3u.ui.player.PlayerScreen
import com.vinplay.m3u.ui.playlists.PlaylistsScreen
import com.vinplay.m3u.ui.trash.TrashScreen

/** Single-activity Compose navigation graph for the whole app. */
@Composable
fun VinPlayRoot() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Destinations.PLAYLISTS) {
        composable(Destinations.PLAYLISTS) {
            PlaylistsScreen(
                onOpenPlaylist = { navController.navigate(Destinations.channels(it)) },
                onImportInto = { navController.navigate(Destinations.import(it)) }
            )
        }

        composable(
            Destinations.CHANNELS,
            arguments = listOf(navArgument(Destinations.ARG_PLAYLIST_ID) { type = NavType.LongType })
        ) {
            ChannelsScreen(
                onBack = { navController.popBackStack() },
                onImport = { navController.navigate(Destinations.import(it)) },
                onOpenTrash = { navController.navigate(Destinations.trash(it)) },
                onPlay = { navController.navigate(Destinations.player(it)) }
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

        composable(
            Destinations.PLAYER,
            arguments = listOf(navArgument(Destinations.ARG_CHANNEL_ID) { type = NavType.LongType })
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}
