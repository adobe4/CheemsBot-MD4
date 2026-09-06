package com.vinplay.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vinplay.desktop.data.Channel
import com.vinplay.desktop.data.ChannelKind
import com.vinplay.desktop.data.Filter
import com.vinplay.desktop.data.Importer
import com.vinplay.desktop.data.LinkTester
import com.vinplay.desktop.data.M3uExport
import com.vinplay.desktop.data.Playlist
import com.vinplay.desktop.data.Store
import com.vinplay.desktop.data.TestStatus
import com.vinplay.desktop.data.defaultHttpClient
import com.vinplay.desktop.player.AppPaths
import com.vinplay.desktop.player.ExternalPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class AppState(private val scope: CoroutineScope) {

    companion object {
        const val PAGE = 300
    }

    private val store = Store(AppPaths.database)
    private val http = defaultHttpClient()
    private val tester = LinkTester(http)
    val importer = Importer(store, http)

    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var total by mutableStateOf<Long?>(null); private set
    var loading by mutableStateOf(false); private set
    var endReached by mutableStateOf(false); private set

    var filter by mutableStateOf(Filter()); private set
    var selected by mutableStateOf<Set<Long>>(emptySet()); private set
    var statusMessage by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null); private set
    var progress by mutableStateOf<Pair<Int, Int>?>(null); private set

    private val filterFlow = MutableStateFlow(Filter())
    private var pageJob: Job? = null

    suspend fun start() {
        store.migrate()
        refreshPlaylists()
        scope.launch {
            // collectLatest + delay debounces typing: a new keystroke cancels the pending load,
            // so a huge library is queried once the user pauses instead of on every character.
            // StateFlow already skips duplicate values.
            filterFlow.collectLatest { f ->
                delay(220)
                load(f)
            }
        }
        load(filter)
    }

    // ---- filtering ----

    fun setQuery(q: String) = updateFilter { it.copy(query = q) }
    fun setKind(k: ChannelKind?) = updateFilter { it.copy(kind = k) }
    fun setPlaylist(id: Long?) = updateFilter { it.copy(playlistId = id) }
    fun setShowDeleted(show: Boolean) = updateFilter { it.copy(showDeleted = show) }

    private fun updateFilter(transform: (Filter) -> Filter) {
        val next = transform(filter)
        filter = next
        filterFlow.value = next
    }

    private fun load(f: Filter) {
        pageJob?.cancel()
        pageJob = scope.launch {
            loading = true
            total = null
            val page = store.page(f, PAGE, 0)
            channels = page
            endReached = page.size < PAGE
            loading = false
            selected = emptySet()
            // Counting is a second query; do it after the rows are on screen so results feel instant.
            total = store.count(f)
        }
    }

    fun loadMore() {
        if (loading || endReached) return
        scope.launch {
            val offset = channels.size
            val page = store.page(filter, PAGE, offset)
            channels = channels + page
            endReached = page.size < PAGE
        }
    }

    fun reload() = load(filter)

    // ---- selection ----

    fun toggle(id: Long) {
        selected = selected.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    fun selectAllLoaded() { selected = channels.map { it.id }.toSet() }
    fun clearSelection() { selected = emptySet() }

    // ---- playlists ----

    suspend fun refreshPlaylists() { playlists = store.playlists() }

    fun createPlaylist(name: String, then: (Long) -> Unit = {}) = scope.launch {
        val id = store.createPlaylist(name)
        refreshPlaylists()
        then(id)
    }

    fun renamePlaylist(id: Long, name: String) = scope.launch {
        store.renamePlaylist(id, name); refreshPlaylists()
    }

    fun deletePlaylist(id: Long) = scope.launch {
        store.deletePlaylist(id)
        if (filter.playlistId == id) setPlaylist(null)
        refreshPlaylists()
        reload()
    }

    // ---- channel actions ----

    fun play(channel: Channel) {
        statusMessage = ExternalPlayer.play(channel) ?: "Playing “${channel.name}” in VLC"
    }

    fun moveSelectedTo(playlistId: Long) = scope.launch {
        val ids = selected.toList()
        store.moveToPlaylist(ids, playlistId)
        refreshPlaylists()
        statusMessage = "Moved ${ids.size} channel(s)"
        reload()
    }

    fun moveSelectedToNew(name: String) = scope.launch {
        val ids = selected.toList()
        val id = store.createPlaylist(name)
        store.moveToPlaylist(ids, id)
        refreshPlaylists()
        statusMessage = "Moved ${ids.size} channel(s) into “$name”"
        reload()
    }

    fun deleteSelected() = scope.launch {
        val ids = selected.toList()
        store.softDelete(ids)
        statusMessage = "Moved ${ids.size} channel(s) to trash"
        refreshPlaylists(); reload()
    }

    fun restoreSelected() = scope.launch {
        val ids = selected.toList()
        store.restore(ids)
        statusMessage = "Restored ${ids.size} channel(s)"
        refreshPlaylists(); reload()
    }

    fun purgeSelected() = scope.launch {
        val ids = selected.toList()
        store.purge(ids)
        statusMessage = "Permanently deleted ${ids.size} channel(s)"
        refreshPlaylists(); reload()
    }

    fun editChannel(id: Long, name: String, url: String, group: String, logo: String?) = scope.launch {
        store.updateChannel(id, name, url, group, logo?.ifBlank { null })
        reload()
    }

    fun removeDuplicates() = scope.launch {
        busy = "Scanning for duplicates…"
        val n = store.removeDuplicates(filter.playlistId)
        busy = null
        statusMessage = if (n > 0) "Moved $n duplicate(s) to trash" else "No duplicates found"
        refreshPlaylists(); reload()
    }

    // ---- link testing ----

    /** Tests just the checked rows. */
    fun testSelected() = testChannels(channels.filter { it.id in selected })

    /** Tests a concrete list of channels (already loaded) — used by the toolbar action. */
    fun testChannels(list: List<Channel>, concurrency: Int = 16) = scope.launch {
        if (list.isEmpty()) { statusMessage = "Nothing to test"; return@launch }
        val done = AtomicInteger(0)
        progress = 0 to list.size
        busy = "Testing links…"
        val gate = Semaphore(concurrency)
        withContext(Dispatchers.IO) {
            val jobs = list.map { ch ->
                launch {
                    gate.withPermit {
                        val r = tester.test(ch.url, ch.userAgent, ch.referrer)
                        store.setTestResult(ch.id, r.status, r.code)
                    }
                    progress = done.incrementAndGet() to list.size
                }
            }
            jobs.forEach { it.join() }
        }
        progress = null
        busy = null
        statusMessage = "Tested ${list.size} channel(s)"
        reload()
    }

    /** Loads every channel matching the filter (capped) and tests them. */
    fun testFiltered() = scope.launch {
        busy = "Collecting channels…"
        val all = mutableListOf<Channel>()
        var offset = 0
        while (true) {
            val page = store.page(filter, 1000, offset)
            if (page.isEmpty()) break
            all += page
            offset += page.size
            if (all.size >= 100_000) break // safety cap
        }
        busy = null
        testChannels(all)
    }

    // ---- export ----

    fun exportTo(file: File) = scope.launch {
        busy = "Exporting…"
        var n = 0
        withContext(Dispatchers.IO) {
            file.bufferedWriter().use { w ->
                M3uExport.writeHeader(w)
                store.channelsForExport(filter) { c -> M3uExport.writeChannel(w, c); n++ }
            }
        }
        busy = null
        statusMessage = "Exported $n channel(s) to ${file.name}"
    }

    // ---- import ----

    fun importFile(playlistId: Long, file: File) = scope.launch {
        busy = "Importing ${file.name}…"
        val r = importer.fromFile(playlistId, file) { busy = "Imported ${"%,d".format(it)}…" }
        finishImport(r.imported, r.error)
    }

    fun importUrl(playlistId: Long, url: String) = scope.launch {
        busy = "Downloading…"
        val r = importer.fromUrl(playlistId, url) { busy = "Imported ${"%,d".format(it)}…" }
        finishImport(r.imported, r.error)
    }

    fun importXtream(playlistId: Long, server: String, user: String, pass: String) = scope.launch {
        busy = "Connecting…"
        val r = importer.fromXtream(playlistId, server, user, pass) { busy = "Imported ${"%,d".format(it)}…" }
        finishImport(r.imported, r.error)
    }

    private suspend fun finishImport(imported: Int, error: String?) {
        busy = null
        statusMessage = error ?: "Imported ${"%,d".format(imported)} channels"
        refreshPlaylists()
        reload()
    }
}
