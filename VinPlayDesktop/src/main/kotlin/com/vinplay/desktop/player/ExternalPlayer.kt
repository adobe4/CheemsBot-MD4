package com.vinplay.desktop.player

import com.vinplay.desktop.data.Channel
import com.vinplay.desktop.data.HttpDefaults
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Properties

/** Per-user data location for the database and settings. */
object AppPaths {
    val dir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.let { File(it) }
                ?: File(System.getProperty("user.home"), "AppData/Roaming")
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support")
            else -> File(System.getProperty("user.home"), ".local/share")
        }
        File(base, "VinPlayManager").apply { mkdirs() }
    }

    val database: File get() = File(dir, "library.db")
    private val settingsFile: File get() = File(dir, "settings.properties")

    fun loadSettings(): Properties = Properties().apply {
        if (settingsFile.exists()) settingsFile.inputStream().use { load(it) }
    }

    fun saveSetting(key: String, value: String) {
        val p = loadSettings()
        p.setProperty(key, value)
        settingsFile.outputStream().use { p.store(it, "VinPlay Manager") }
    }
}

/**
 * Plays a stream in the user's own player. VLC is preferred because it handles the awkward live
 * IPTV streams (mid-GOP MPEG-TS, custom headers) that other players refuse; per-channel
 * User-Agent/Referer are passed through so header-gated streams still work.
 */
object ExternalPlayer {

    const val VLC_PATH_KEY = "vlcPath"

    private val commonVlcPaths = listOf(
        "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
        "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe",
        "/usr/bin/vlc",
        "/snap/bin/vlc",
        "/Applications/VLC.app/Contents/MacOS/VLC"
    )

    /** Configured path if set and valid, else the first VLC found in a standard location. */
    fun detectVlc(): String? {
        AppPaths.loadSettings().getProperty(VLC_PATH_KEY)
            ?.takeIf { it.isNotBlank() && File(it).exists() }
            ?.let { return it }
        return commonVlcPaths.firstOrNull { File(it).exists() }
    }

    /** @return null on success, or a message explaining why nothing could be launched. */
    fun play(channel: Channel): String? {
        val vlc = detectVlc()
        if (vlc != null) {
            val cmd = mutableListOf(
                vlc,
                channel.url,
                "--http-user-agent=${channel.userAgent?.takeIf { it.isNotBlank() } ?: HttpDefaults.USER_AGENT}"
            )
            channel.referrer?.takeIf { it.isNotBlank() }?.let { cmd += "--http-referrer=$it" }
            return runCatching { ProcessBuilder(cmd).start(); null }
                .getOrElse { "Could not start VLC: ${it.message}" }
        }
        // No VLC: hand the URL to the OS. Headers can't be passed this way, so header-gated
        // streams may fail — that's why VLC is preferred.
        return runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(channel.url))
                null
            } else {
                "VLC was not found. Set its location in Settings to play streams."
            }
        }.getOrElse { "VLC was not found, and the system player could not open this stream." }
    }
}
