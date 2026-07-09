package com.vinplay.m3u.data.local

/** Lightweight projection for the playlists list: entity fields + a live channel count. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val channelCount: Int
)
