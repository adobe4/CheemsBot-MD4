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
 */
object M3uParser {

    private val ATTR_REGEX = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    /**
     * @return number of channels emitted.
     * @param onChannel invoked for every parsed entry, in file order.
     */
    suspend fun parse(reader: BufferedReader, onChannel: suspend (ParsedChannel) -> Unit): Int {
        var count = 0
        var pending: Pending? = null
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
                }
                raw.startsWith("#EXTGRP", ignoreCase = true) -> {
                    currentGroupOverride = raw.substringAfter(':', "").trim().ifBlank { null }
                }
                raw.startsWith("#") -> {
                    // #EXTVLCOPT, #KODIPROP, comments, etc. — ignored.
                }
                else -> {
                    // A URL line. Pair it with the pending #EXTINF (or synthesize a bare entry).
                    val url = raw
                    val meta = pending
                    val group = meta?.group?.ifBlank { null }
                        ?: currentGroupOverride
                        ?: ""
                    onChannel(
                        ParsedChannel(
                            name = meta?.name?.ifBlank { null } ?: deriveNameFromUrl(url),
                            url = url,
                            groupTitle = group,
                            tvgId = meta?.tvgId,
                            tvgName = meta?.tvgName,
                            tvgLogo = meta?.tvgLogo,
                            kind = ChannelKind.fromUrl(url)
                        )
                    )
                    count++
                    pending = null
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
