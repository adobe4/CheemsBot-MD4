package com.vinplay.m3u.data.net

import com.vinplay.m3u.data.model.TestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

data class LinkResult(val status: TestStatus, val code: Int?)

/**
 * Probes stream reachability the way a player actually opens a stream: a ranged GET (not HEAD)
 * with a VLC-style User-Agent, following cross-scheme redirects (OkHttp does http<->https by
 * default). HEAD is deliberately avoided because most streaming origins reject or mishandle it,
 * which previously flagged healthy streams as DEAD.
 *
 * A stream is considered reachable if the server returns any non-error status (< 400). 401/403
 * are reported distinctly so the user can tell "needs auth/blocked" from "gone". Concurrency is
 * gated by the caller (semaphore, limit 10); per-call timeout comes from the shared client (8s).
 */
@Singleton
class LinkTester @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun test(url: String): LinkResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HttpDefaults.USER_AGENT)
            .header("Accept", "*/*")
            // Ask for a single byte so we don't pull the whole stream just to prove it's alive.
            .header("Range", "bytes=0-1")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                val status = when {
                    // 2xx/3xx = reachable; 206 (partial) and 416 (range unsatisfiable but the
                    // resource exists) also mean the stream is really there.
                    code in 200..399 || code == 416 -> TestStatus.OK
                    code == 401 || code == 403 -> TestStatus.DEAD // reachable but refused
                    else -> TestStatus.DEAD
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
