package com.vinplay.m3u.ui.navigation

/** Central route table. Argument keys are kept next to their builders to avoid drift. */
object Destinations {
    const val PLAYLISTS = "playlists"
    const val ALL_CHANNELS = "allChannels"

    const val ARG_PLAYLIST_ID = "playlistId"
    const val ARG_CHANNEL_ID = "channelId"

    const val CHANNELS = "channels/{$ARG_PLAYLIST_ID}"
    fun channels(playlistId: Long) = "channels/$playlistId"

    const val IMPORT = "import/{$ARG_PLAYLIST_ID}"
    fun import(playlistId: Long) = "import/$playlistId"

    const val TRASH = "trash/{$ARG_PLAYLIST_ID}"
    fun trash(playlistId: Long) = "trash/$playlistId"

    const val PLAYER = "player/{$ARG_CHANNEL_ID}"
    fun player(channelId: Long) = "player/$channelId"
}
