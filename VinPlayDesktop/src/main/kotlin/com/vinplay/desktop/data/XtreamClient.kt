package com.vinplay.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Xtream Codes support. Panels expose the whole line as an M3U at
 * `get.php?username=…&password=…&type=m3u_plus`, so an Xtream account is imported by turning the
 * credentials into that URL and reusing the normal streaming M3U importer.
 *
 * [probe] hits `player_api.php` first so a bad host / wrong password / expired line is reported as
 * a clear message instead of an empty playlist.
 */

class XtreamClient constructor(
    private val client: OkHttpClient
) {
    companion object {
        /**
         * Reduces anything the user might paste — `host`, `host:8080`, `http://host:8080/c/`, or a
         * full `get.php?…` / `player_api.php?…` URL — to a bare `scheme://host[:port]` base.
         */
        fun normalizeBase(input: String): String? {
            var s = input.trim()
            if (s.isEmpty()) return null
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            val url = s.toHttpUrlOrNull() ?: return null
            val isDefaultPort = (url.scheme == "http" && url.port == 80) ||
                (url.scheme == "https" && url.port == 443)
            val port = if (isDefaultPort) "" else ":${url.port}"
            return "${url.scheme}://${url.host}$port"
        }

        /** Pulls username/password out of a pasted get.php / player_api.php URL, if present. */
        fun extractCredentials(input: String): Pair<String, String>? {
            var s = input.trim()
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            val url = s.toHttpUrlOrNull() ?: return null
            val user = url.queryParameter("username") ?: return null
            val pass = url.queryParameter("password") ?: return null
            if (user.isBlank() || pass.isBlank()) return null
            return user to pass
        }

        fun m3uUrl(base: String, username: String, password: String): String =
            "$base/get.php?username=${enc(username)}&password=${enc(password)}" +
                "&type=m3u_plus&output=ts"

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    }

    /** Returns a human-readable problem, or null when the account is usable. */
    suspend fun probe(base: String, username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            val url = "$base/player_api.php?username=${enc(username)}&password=${enc(password)}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", HttpDefaults.USER_AGENT)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@use "Server replied HTTP ${resp.code} to the login check. " +
                            "Check the server address and port."
                    }
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use "The server returned an empty login response."
                    val json = runCatching { JSONObject(body) }.getOrNull()
                        ?: return@use "That address didn't answer like an Xtream server. " +
                            "Use the panel's host and port, e.g. http://example.com:8080"
                    val info = json.optJSONObject("user_info")
                        ?: return@use "The server did not return account info — check the server URL."
                    if (info.optInt("auth", 0) != 1) {
                        return@use "The server rejected this username/password."
                    }
                    val status = info.optString("status", "")
                    if (status.isNotBlank() && !status.equals("Active", true)) {
                        return@use "This account is not active (status: $status)."
                    }
                    null
                }
            } catch (e: Exception) {
                "Could not reach the server: ${e.message ?: "network error"}"
            }
        }
}
