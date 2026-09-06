package com.vinplay.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.Writer
import java.util.concurrent.TimeUnit

/** Result of an import attempt. [error] is null on success. */
data class ImportResult(val imported: Int, val error: String? = null)

/**
 * Streaming importer: the playlist is parsed line-by-line and flushed to SQLite in batched
 * transactions, so a multi-gigabyte M3U imports with a near-constant memory footprint.
 */
class Importer(private val store: Store, private val client: OkHttpClient) {

    companion object {
        const val BATCH = 2_000
    }

    suspend fun fromFile(playlistId: Long, file: File, onProgress: suspend (Int) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                file.bufferedReader().use { reader -> consume(playlistId, reader, onProgress) }
            }.fold(
                onSuccess = { ImportResult(it) },
                onFailure = { ImportResult(0, it.message ?: "Could not read the file") }
            )
        }

    suspend fun fromUrl(playlistId: Long, url: String, onProgress: suspend (Int) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url.trim())
                    .header("User-Agent", HttpDefaults.USER_AGENT)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Server replied HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("Empty response")
                    val reader = body.charStream().buffered()
                    requireM3u(reader)
                    consume(playlistId, reader, onProgress)
                }
            }.fold(
                onSuccess = { ImportResult(it) },
                onFailure = { ImportResult(0, it.message ?: "Download failed") }
            )
        }

    /** Verifies the Xtream account first, then imports its `get.php` M3U. */
    suspend fun fromXtream(
        playlistId: Long,
        server: String,
        username: String,
        password: String,
        onProgress: suspend (Int) -> Unit
    ): ImportResult {
        val base = XtreamClient.normalizeBase(server)
            ?: return ImportResult(0, "That server address isn't valid. Try host:port, e.g. http://example.com:8080")
        // Credentials can also be pasted inside a get.php/player_api URL.
        val creds = XtreamClient.extractCredentials(server)
        val user = username.ifBlank { creds?.first.orEmpty() }
        val pass = password.ifBlank { creds?.second.orEmpty() }
        if (user.isBlank() || pass.isBlank()) return ImportResult(0, "Username and password are required.")

        XtreamClient(client).probe(base, user, pass)?.let { return ImportResult(0, it) }
        return fromUrl(playlistId, XtreamClient.m3uUrl(base, user, pass), onProgress)
    }

    /**
     * Rejects HTML/JSON error pages up front. Without this, a portal that answers a login page with
     * HTTP 200 imports zero channels and looks like a silent failure.
     */
    private fun requireM3u(reader: BufferedReader) {
        reader.mark(8192)
        val head = CharArray(2048)
        val n = reader.read(head)
        val text = if (n > 0) String(head, 0, n) else ""
        reader.reset()
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        if (trimmed.isBlank()) throw IOException("The server returned an empty playlist.")
        if (!trimmed.startsWith("#EXTM3U", true) && !trimmed.startsWith("#EXTINF", true)) {
            val hint = trimmed.take(160).replace(Regex("\\s+"), " ")
            throw IOException("That address did not return a playlist. The server said: $hint")
        }
    }

    private suspend fun consume(playlistId: Long, reader: BufferedReader, onProgress: suspend (Int) -> Unit): Int {
        var order = store.maxOrderIndex(playlistId) + 1
        val batch = ArrayList<ParsedChannel>(BATCH)
        var imported = 0

        suspend fun flush() {
            if (batch.isEmpty()) return
            store.insertBatch(playlistId, batch, order)
            order += batch.size
            imported += batch.size
            batch.clear()
            onProgress(imported)
        }

        M3uParser.parse(reader) { parsed ->
            batch.add(parsed)
            if (batch.size >= BATCH) flush()
        }
        flush()
        return imported
    }
}

/** Writes channels back out as an M3U (`#EXTINF` with tvg attributes and per-channel headers). */
object M3uExport {
    fun writeHeader(w: Writer) = w.write("#EXTM3U\n")

    fun writeChannel(w: Writer, c: Channel) {
        w.write("#EXTINF:-1")
        c.tvgId?.let { w.write(" tvg-id=\"${esc(it)}\"") }
        c.tvgName?.let { w.write(" tvg-name=\"${esc(it)}\"") }
        c.tvgLogo?.let { w.write(" tvg-logo=\"${esc(it)}\"") }
        if (c.groupTitle.isNotBlank()) w.write(" group-title=\"${esc(c.groupTitle)}\"")
        w.write(",${c.name}\n")
        c.userAgent?.let { w.write("#EXTVLCOPT:http-user-agent=$it\n") }
        c.referrer?.let { w.write("#EXTVLCOPT:http-referrer=$it\n") }
        w.write("${c.url}\n")
    }

    private fun esc(v: String) = v.replace("\"", "'").replace("\n", " ")
}

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
