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
 * Probes stream reachability. Tries a cheap HEAD first; many streaming origins reject HEAD
 * (405/501) or don't support it, so it falls back to a 1-byte Range GET before giving up.
 * The shared OkHttpClient caps concurrency via its dispatcher; callers additionally gate with
 * a semaphore (limit 10) so a "test all" over 100k channels stays polite and bounded.
 */
@Singleton
class LinkTester @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun test(url: String): LinkResult = withContext(Dispatchers.IO) {
        val head = runCatching { probe(url, head = true) }.getOrElse { return@withContext it.toResult() }
        // Fall back to a ranged GET when HEAD is unsupported/blocked.
        if (head.status == TestStatus.DEAD && (head.code == 405 || head.code == 501 || head.code == 403)) {
            runCatching { probe(url, head = false) }.getOrElse { it.toResult() }
        } else {
            head
        }
    }

    private fun probe(url: String, head: Boolean): LinkResult {
        val builder = Request.Builder().url(url)
        if (head) builder.head() else builder.get().header("Range", "bytes=0-1")
        client.newCall(builder.build()).execute().use { resp ->
            val code = resp.code
            val status = when (code) {
                in 200..299 -> TestStatus.OK
                in 300..399 -> TestStatus.REDIRECT
                else -> TestStatus.DEAD
            }
            return LinkResult(status, code)
        }
    }

    private fun Throwable.toResult(): LinkResult = when (this) {
        is SocketTimeoutException -> LinkResult(TestStatus.TIMEOUT, null)
        is IOException -> LinkResult(TestStatus.ERROR, null)
        else -> LinkResult(TestStatus.ERROR, null)
    }
}
