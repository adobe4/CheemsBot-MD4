package com.vinplay.m3u.data.repository

import com.vinplay.m3u.data.local.dao.ChannelDao
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.parser.M3uParser
import com.vinplay.m3u.data.parser.ParsedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streaming importer. Sources (file / URL / text) are all reduced to a [BufferedReader] that is
 * parsed line-by-line; parsed entries are buffered into [BATCH_SIZE] chunks and inserted inside a
 * single Room transaction each, so peak memory is ~one batch regardless of playlist size.
 */
@Singleton
class ImportManager @Inject constructor(
    private val client: OkHttpClient,
    private val channelDao: ChannelDao,
    private val playlistRepository: PlaylistRepository
) {
    companion object {
        const val BATCH_SIZE = 500
    }

    fun interface ProgressListener {
        suspend fun onProgress(imported: Int)
    }

    /** Downloads a remote M3U with an 8s-timeout streaming client and imports as it arrives. */
    suspend fun importFromUrl(playlistId: Long, url: String, progress: ProgressListener): Int =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("User-Agent", "VinPlayM3U/1.0").build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty response")
                body.charStream().buffered().use { reader ->
                    importFromReader(playlistId, reader, progress)
                }
            }
        }

    suspend fun importFromStream(playlistId: Long, input: InputStream, progress: ProgressListener): Int =
        withContext(Dispatchers.IO) {
            BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).use { reader ->
                importFromReader(playlistId, reader, progress)
            }
        }

    suspend fun importFromText(playlistId: Long, text: String, progress: ProgressListener): Int =
        withContext(Dispatchers.IO) {
            importFromReader(playlistId, text.reader().buffered(), progress)
        }

    private suspend fun importFromReader(
        playlistId: Long,
        reader: BufferedReader,
        progress: ProgressListener
    ): Int {
        var nextOrder = channelDao.maxOrderIndex(playlistId) + 1
        val batch = ArrayList<ChannelEntity>(BATCH_SIZE)
        var imported = 0

        suspend fun flush() {
            if (batch.isEmpty()) return
            channelDao.insertAll(batch) // list insert runs in one transaction
            imported += batch.size
            batch.clear()
            progress.onProgress(imported)
        }

        M3uParser.parse(reader) { parsed: ParsedChannel ->
            batch.add(parsed.toEntity(playlistId, nextOrder++))
            if (batch.size >= BATCH_SIZE) flush()
        }
        flush()

        playlistRepository.touch(playlistId)
        return imported
    }

    private fun ParsedChannel.toEntity(playlistId: Long, order: Long) = ChannelEntity(
        playlistId = playlistId,
        name = name,
        url = url,
        groupTitle = groupTitle,
        tvgId = tvgId,
        tvgName = tvgName,
        tvgLogo = tvgLogo,
        kind = kind,
        orderIndex = order
    )
}
