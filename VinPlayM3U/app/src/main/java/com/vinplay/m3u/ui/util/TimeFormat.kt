package com.vinplay.m3u.ui.util

import java.util.concurrent.TimeUnit

/** Compact "3m ago" / "2h ago" / "5d ago" style relative time for list subtitles. */
fun formatRelative(epochMillis: Long): String {
    val diff = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        else -> "${days / 30}mo ago"
    }
}
