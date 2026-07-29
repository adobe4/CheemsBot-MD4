package com.djkuku.shared

data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "DJ Kuku",
    val audioUrl: String = "",
    val coverUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val downloads: Long = 0
)

data class Story(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val audioUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
