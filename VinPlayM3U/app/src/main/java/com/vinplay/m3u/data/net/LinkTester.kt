package com.vinplay.m3u.data.net

import com.vinplay.m3u.data.model.TestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class LinkResult(val status: TestStatus, val code: Int?)

/**
 * Probes stream reachability the way a player opens a stream, but with extra checks to cut the
 * false results that a plain status code gives on IPTV:
 *  - a ranged GET (not HEAD) with the channel's User-Agent/Referer if set,
 *  - reject non-2xx/3xx (401/403/404/5xx) as DEAD,
 *  - a small body sniff + content-type check so a portal's HTML "error/login" page returned with
 *    HTTP 200 is correctly flagged DEAD, and an .m3u8 that really is a playlist is confirmed OK,
 *  - one retry on transient timeout/IO before giving up.
 *
 * Live IPTV inherently flaps, so this is a strong heuristic rather than a guarantee.
 */
@Singleton
class LinkTester @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun test(
        url: String,
        userAgent: String? = null,
        referrer: String? = null
    ): LinkResult = withContext(Dispatchers.IO) {
        var last = probe(url, userAgent, referrer)
        if (last.status == TestStatus.TIMEOUT || last.status == TestStatus.ERROR) {
            last = probe(url, userAgent, referrer) // single retry for transient failures
        }
        last
    }

    private fun probe(url: String, userAgent: String?, referrer: String?): LinkResult {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: HttpDefaults.USER_AGENT)
            .header("Accept", "*/*")
            .header("Range", "bytes=0-4095")
        referrer?.takeIf { it.isNotBlank() }?.let { builder.header("Referer", it) }

        val call = client.newCall(builder.get().build())
        call.timeout().timeout(8, TimeUnit.SECONDS)
        return try {
            call.execute().use { resp ->
                val code = resp.code
                if (code !in 200..399) return LinkResult(TestStatus.DEAD, code)

                val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                val body = runCatching { resp.peekBody(4096).string() }.getOrDefault("")
                val isPlaylist = body.contains("#EXTM3U")
                val looksHls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
                    contentType.contains("mpegurl")

                val status = when {
                    isPlaylist -> TestStatus.OK
                    // Portals often answer a dead stream with an HTML/JSON error page at 200.
                    contentType.contains("text/html") || contentType.contains("application/json") -> TestStatus.DEAD
                    // Declared HLS but no #EXTM3U in the first 4KB → almost certainly not a real playlist.
                    looksHls && body.isNotBlank() -> TestStatus.DEAD
                    else -> TestStatus.OK
                }
                LinkResult(status, code)
            }
        } catch (e: SocketTimeoutException) {
            LinkResult(TestStatus.TIMEOUT, null)
        } catch (e: IOException) {
            LinkResult(TestStatus.ERROR, null)
        }
    }
}
