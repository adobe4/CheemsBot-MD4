package com.vinplay.m3u.data.parser

import com.vinplay.m3u.data.local.entity.ChannelEntity
import java.io.Writer

/** Serializes channels back to M3U text. Writes incrementally so exports never buffer whole. */
object M3uWriter {

    fun writeHeader(writer: Writer) {
        writer.write("#EXTM3U\n")
    }

    fun writeChannel(writer: Writer, channel: ChannelEntity) {
        val sb = StringBuilder(96)
        sb.append("#EXTINF:-1")
        channel.tvgId?.let { sb.append(" tvg-id=\"").append(escape(it)).append('"') }
        channel.tvgName?.let { sb.append(" tvg-name=\"").append(escape(it)).append('"') }
        channel.tvgLogo?.let { sb.append(" tvg-logo=\"").append(escape(it)).append('"') }
        if (channel.groupTitle.isNotBlank()) {
            sb.append(" group-title=\"").append(escape(channel.groupTitle)).append('"')
        }
        sb.append(',').append(channel.name).append('\n')
        sb.append(channel.url).append('\n')
        writer.write(sb.toString())
    }

    private fun escape(v: String): String = v.replace("\"", "'").replace("\n", " ")
}
