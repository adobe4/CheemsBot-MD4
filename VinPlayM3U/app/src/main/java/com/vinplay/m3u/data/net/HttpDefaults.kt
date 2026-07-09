package com.vinplay.m3u.data.net

/** Shared HTTP defaults tuned for IPTV origins. */
object HttpDefaults {
    /**
     * Many IPTV portals reject the stock OkHttp/ExoPlayer User-Agent but happily serve a
     * VLC-style one, so we present that by default for both link testing and playback.
     */
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
}
