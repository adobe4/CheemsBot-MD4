package com.vinplay.desktop.data


/** A single entry produced by [M3uParser] before it is assigned an order/playlist and persisted. */
data class ParsedChannel(
    val name: String,
    val url: String,
    val groupTitle: String,
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val kind: ChannelKind,
    val userAgent: String? = null,
    val referrer: String? = null
)
