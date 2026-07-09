package com.vinplay.m3u.data.repository

import com.vinplay.m3u.data.local.dao.ChannelDao
import com.vinplay.m3u.data.parser.M3uWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes a playlist's active channels to an M3U stream, paging the DB to avoid OOM. */
@Singleton
class ExportManager @Inject constructor(
    private val channelDao: ChannelDao
) {
    companion object {
        private const val PAGE = 1000
    }

    suspend fun export(playlistId: Long, output: OutputStream): Int = withContext(Dispatchers.IO) {
        var written = 0
        OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
            M3uWriter.writeHeader(writer)
            var offset = 0
            while (true) {
                val page = channelDao.pageForExport(playlistId, PAGE, offset)
                if (page.isEmpty()) break
                for (channel in page) {
                    M3uWriter.writeChannel(writer, channel)
                    written++
                }
                offset += page.size
                if (page.size < PAGE) break
            }
            writer.flush()
        }
        written
    }
}
