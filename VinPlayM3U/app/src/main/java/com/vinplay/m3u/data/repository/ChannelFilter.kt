package com.vinplay.m3u.data.repository

import com.vinplay.m3u.data.model.ChannelKind

/**
 * The active listing filter. [group]/[kind] are null when "All". [query] is empty when not
 * searching. Passed straight through to the DAO's parameterized queries.
 */
data class ChannelFilter(
    val query: String = "",
    val group: String? = null,
    val kind: ChannelKind? = null
) {
    val kindName: String? get() = kind?.name
}
