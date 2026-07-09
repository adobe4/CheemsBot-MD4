package com.vinplay.m3u.data.parser

import com.vinplay.m3u.data.model.ChannelKind
import kotlinx.coroutines.ensureActive
import java.io.BufferedReader
import kotlin.coroutines.coroutineContext

/**
 * Streaming M3U/M3U8 parser.
 *
 * It reads the source one line at a time and emits each [ParsedChannel] through [onChannel]
 * as soon as its URL line is seen — it never accumulates the full playlist in memory, so a
 * 100k+ channel file is parsed with a constant footprint. The caller (import pipeline) is
 * responsible for buffering emissions into DB batches.
 *
 * Per-channel HTTP headers are captured too: `#EXTVLCOPT:http-user-agent` / `http-referrer`,
 * `#EXTHTTP:{...}` JSON, and ffmpeg/Kodi-style `URL|User-Agent=...&Referer=...` pipe options.
 * Many IPTV streams only play when their specific User-Agent/Referer is sent, so preserving
 * these is what makes "works in VLC" streams work here.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val JSON_UA_REGEX = Regex("\"[Uu]ser-?[Aa]gent\"\\s*:\\s*\"([^\"]*)\"")
    private val JSON_REF_REGEX = Regex("\"[Rr]eferer\"\\s*:\\s*\"([^\"]*)\"")

    /**
     * @return number of channels emitted.
     * @param onChannel invoked for every parsed entry, in file order.
     */
    suspend fun parse(reader: BufferedReader, onChannel: suspend (ParsedChannel) -> Unit): Int {
        var count = 0
        var pending: Pending? = null
        var pendingUa: String? = null
        var pendingReferrer: String? = null
        // #EXTGRP applies to subsequent entries until overridden.
        var currentGroupOverride: String? = null

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            coroutineContext.ensureActive() // cooperative cancellation for huge files
            val raw = line!!.trim()
            if (raw.isEmpty()) continue

            when {
                raw.startsWith("#EXTM3U", ignoreCase = true) -> {
                    // Header line; nothing to capture.
                }
                raw.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(raw)
                    // New entry begins: reset any header lines left over from a previous entry.
                    pendingUa = null
                    pendingReferrer = null
                }
                raw.startsWith("#EXTGRP", ignoreCase = true) -> {
                    currentGroupOverride = raw.substringAfter(':', "").trim().ifBlank { null }
                }
                raw.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val opt = raw.substringAfter(':', "")
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> pendingUa = value.ifBlank { null }
                        "http-referrer", "http-referer" -> pendingReferrer = value.ifBlank { null }
                    }
                }
                raw.startsWith("#EXTHTTP", ignoreCase = true) -> {
                    val json = raw.substringAfter(':', "")
                    JSON_UA_REGEX.find(json)?.groupValues?.get(1)?.let { pendingUa = it.ifBlank { null } }
                    JSON_REF_REGEX.find(json)?.groupValues?.get(1)?.let { pendingReferrer = it.ifBlank { null } }
                }
                raw.startsWith("#KODIPROP", ignoreCase = true) -> {
                    val prop = raw.substringAfter(':', "")
                    if (prop.substringBefore('=').trim().lowercase().endsWith("http-user-agent")) {
                        pendingUa = prop.substringAfter('=', "").trim().ifBlank { null }
                    }
                }
                raw.startsWith("#") -> {
                    // Other comments/directives — ignored.
                }
                else -> {
                    // A URL line. It may carry ffmpeg/Kodi-style pipe headers we must peel off.
                    val (cleanUrl, pipeUa, pipeReferrer) = splitPipeOptions(raw)
                    val meta = pending
                    val group = meta?.group?.ifBlank { null } ?: currentGroupOverride ?: ""
                    onChannel(
                        ParsedChannel(
                            name = meta?.name?.ifBlank { null } ?: deriveNameFromUrl(cleanUrl),
                            url = cleanUrl,
                            groupTitle = group,
                            tvgId = meta?.tvgId,
                            tvgName = meta?.tvgName,
                            tvgLogo = meta?.tvgLogo,
                            kind = ChannelKind.fromUrl(cleanUrl),
                            userAgent = pipeUa ?: pendingUa,
                            referrer = pipeReferrer ?: pendingReferrer
                        )
                    )
                    count++
                    pending = null
                    pendingUa = null
                    pendingReferrer = null
                }
            }
        }
        return count
    }

    private data class Pending(
        val name: String,
        val group: String,
        val tvgId: String?,
        val tvgName: String?,
        val tvgLogo: String?
    )

    private data class UrlWithHeaders(val url: String, val userAgent: String?, val referrer: String?)

    /**
     * Splits `http://host/stream|User-Agent=x&Referer=y` into the bare URL and its headers.
     * Only treats the pipe suffix as headers when it looks like `key=value(&key=value)*`, so a
     * literal pipe elsewhere in the URL is left untouched.
     */
    private fun splitPipeOptions(raw: String): UrlWithHeaders {
        val pipe = raw.indexOf('|')
        if (pipe < 0) return UrlWithHeaders(raw, null, null)
        val url = raw.substring(0, pipe)
        val opts = raw.substring(pipe + 1)
        if (!opts.contains('=')) return UrlWithHeaders(raw, null, null)
        var ua: String? = null
        var referrer: String? = null
        for (pair in opts.split('&')) {
            val k = pair.substringBefore('=').trim().lowercase()
            val v = pair.substringAfter('=', "").trim()
            when (k) {
                "user-agent" -> ua = v.ifBlank { null }
                "referer", "referrer" -> referrer = v.ifBlank { null }
            }
        }
        return UrlWithHeaders(url, ua, referrer)
    }

    private fun parseExtInf(line: String): Pending {
        // Format: #EXTINF:<duration> [attr="v" ...],<Display Name>
        val afterColon = line.substringAfter(':', "")
        val commaIdx = indexOfUnquotedComma(afterColon)
        val attrsPart = if (commaIdx >= 0) afterColon.substring(0, commaIdx) else afterColon
        val name = if (commaIdx >= 0) afterColon.substring(commaIdx + 1).trim() else ""

        var group = ""
        var tvgId: String? = null
        var tvgName: String? = null
        var tvgLogo: String? = null
        for (m in ATTR_REGEX.findAll(attrsPart)) {
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2]
            when (key) {
                "group-title" -> group = value
                "tvg-id" -> tvgId = value.ifBlank { null }
                "tvg-name" -> tvgName = value.ifBlank { null }
                "tvg-logo" -> tvgLogo = value.ifBlank { null }
            }
        }
        return Pending(name, group, tvgId, tvgName, tvgLogo)
    }

    /** The name/attrs comma is the first comma that is not inside a quoted attribute value. */
    private fun indexOfUnquotedComma(s: String): Int {
        var inQuote = false
        for (i in s.indices) {
            when (s[i]) {
                '"' -> inQuote = !inQuote
                ',' -> if (!inQuote) return i
            }
        }
        return -1
    }

    private fun deriveNameFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/').ifBlank { path }
        return last.substringBeforeLast('.').ifBlank { last }.ifBlank { "Channel" }
    }
}
