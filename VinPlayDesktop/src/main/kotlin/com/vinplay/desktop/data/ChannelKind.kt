package com.vinplay.desktop.data

/** High-level classification of a stream, inferred from its URL/extension during parsing. */
enum class ChannelKind {
    LIVE,
    VOD,
    SERIES,
    UNKNOWN;

    companion object {
        /**
         * Infer the kind from a stream URL. Cheap, allocation-light — safe to call per line
         * while parsing 100k+ channel playlists.
         */
        fun fromUrl(url: String): ChannelKind {
            val lower = url.lowercase()
            // Strip query string before extension checks.
            val path = lower.substringBefore('?').substringBefore('#')
            return when {
                path.endsWith(".m3u8") || path.endsWith(".ts") ||
                    path.contains("/live/") || path.endsWith(".mpd") -> LIVE
                path.contains("/series/") -> SERIES
                path.contains("/movie/") || path.endsWith(".mp4") ||
                    path.endsWith(".mkv") || path.endsWith(".avi") ||
                    path.endsWith(".mov") -> VOD
                else -> UNKNOWN
            }
        }
    }
}
