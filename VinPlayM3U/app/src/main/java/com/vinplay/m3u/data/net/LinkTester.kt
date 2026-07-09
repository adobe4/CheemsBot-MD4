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
 * Probes stream reachability the way a player actually opens a stream: a ranged GET (not HEAD)
 * with the channel's User-Agent/Referer if it has them (else a VLC default), following
 * cross-scheme redirects. HEAD is deliberately avoided because most streaming origins reject or
 * mishandle it, which previously flagged healthy streams as DEAD.
 *
 * The shared client has no overall call-timeout (it also streams big imports), so we apply an 8s
 * budget *per call* here to keep "test all" snappy. Concurrency is gated by the caller (semaphore,
 * limit 10).
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
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: HttpDefaults.USER_AGENT)
            .header("Accept", "*/*")
            // Ask for a single byte so we don't pull the whole stream just to prove it's alive.
            .header("Range", "bytes=0-1")
        referrer?.takeIf { it.isNotBlank() }?.let { builder.header("Referer", it) }

        val call = client.newCall(builder.get().build())
        call.timeout().timeout(8, TimeUnit.SECONDS) // per-call budget, independent of the client
        try {
            call.execute().use { resp ->
                val code = resp.code
                val status = when {
                    // 2xx/3xx = reachable; 206 (partial) and 416 (range unsatisfiable but the
                    // resource exists) also mean the stream is really there.
                    code in 200..399 || code == 416 -> TestStatus.OK
                    else -> TestStatus.DEAD // includes 401/403 (reachable but refused) and 4xx/5xx
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
