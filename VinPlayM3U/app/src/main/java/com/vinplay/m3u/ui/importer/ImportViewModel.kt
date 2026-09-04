package com.vinplay.m3u.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinplay.m3u.data.net.XtreamClient
import com.vinplay.m3u.data.repository.ImportManager
import com.vinplay.m3u.task.TaskService
import com.vinplay.m3u.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importManager: ImportManager,
    private val xtreamClient: XtreamClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: Long = savedStateHandle.get<Long>(Destinations.ARG_PLAYLIST_ID) ?: 0L

    sealed interface ImportState {
        data object Idle : ImportState
        data class Running(val imported: Int, val note: String? = null) : ImportState
        data class Success(val imported: Int) : ImportState
        data class Failure(val message: String) : ImportState
        /** Handed off to the background service; the screen closes on dismiss. */
        data class Started(val message: String) : ImportState
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state = _state.asStateFlow()

    private val progress = ImportManager.ProgressListener { imported ->
        _state.value = ImportState.Running(imported)
    }

    fun importFromFile(uri: Uri) = runImport {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open the selected file")
        input.use { importManager.importFromStream(playlistId, it, progress) }
    }

    fun importFromUrl(url: String) = runImport {
        importManager.importFromUrl(playlistId, url.trim(), progress)
    }

    /** Downloads/imports in the foreground service, so it continues if the app is closed. */
    fun importFromUrlInBackground(url: String): Boolean {
        val link = url.trim()
        if (!link.startsWith("http", ignoreCase = true)) return false
        TaskService.startImport(context, playlistId, listOf(link))
        return true
    }

    /**
     * Xtream Codes login. Verifies the account against `player_api.php` first so a wrong host,
     * bad password or expired line is reported plainly, then imports the line's `get.php` M3U
     * through the normal background importer.
     */
    fun importXtream(server: String, username: String, password: String) {
        val base = XtreamClient.normalizeBase(server)
        if (base == null) {
            _state.value = ImportState.Failure("Enter a valid server address, e.g. http://example.com:8080")
            return
        }
        // Allow pasting a full get.php/player_api URL into the server field alone.
        var user = username.trim()
        var pass = password.trim()
        if (user.isBlank() || pass.isBlank()) {
            XtreamClient.extractCredentials(server)?.let { (u, p) -> user = u; pass = p }
        }
        if (user.isBlank() || pass.isBlank()) {
            _state.value = ImportState.Failure("Username and password are required.")
            return
        }

        _state.value = ImportState.Running(0, "Checking Xtream account…")
        viewModelScope.launch {
            val problem = xtreamClient.probe(base, user, pass)
            if (problem != null) {
                _state.value = ImportState.Failure(problem)
                return@launch
            }
            TaskService.startImport(context, playlistId, listOf(XtreamClient.m3uUrl(base, user, pass)))
            _state.value = ImportState.Started(
                "Account verified. Importing in the background — watch the notification for progress."
            )
        }
    }

    /** Detects all links in the text and imports them via the background service. */
    fun importBulkInBackground(text: String): Int {
        val links = extractLinks(text)
        if (links.isNotEmpty()) TaskService.startImport(context, playlistId, links)
        return links.size
    }

    fun importFromText(text: String) = runImport {
        importManager.importFromText(playlistId, text, progress)
    }

    /**
     * Bulk import: pull every http(s) link out of pasted text (e.g. ten M3U URLs, one per line or
     * mixed into prose) and import them all in sequence into this playlist.
     */
    fun importBulkLinks(text: String) {
        val links = extractLinks(text)
        if (links.isEmpty()) {
            _state.value = ImportState.Failure("No links found in the pasted text.")
            return
        }
        _state.value = ImportState.Running(0)
        viewModelScope.launch {
            var total = 0
            var failed = 0
            links.forEachIndexed { index, link ->
                val note = "Source ${index + 1}/${links.size}"
                val perSource = ImportManager.ProgressListener { imported ->
                    _state.value = ImportState.Running(total + imported, note)
                }
                runCatching { importManager.importFromUrl(playlistId, link, perSource) }
                    .onSuccess { total += it }
                    .onFailure { failed++ }
            }
            _state.value = if (total == 0 && failed > 0) {
                ImportState.Failure("All $failed link(s) failed to import.")
            } else {
                ImportState.Success(total)
            }
        }
    }

    private fun extractLinks(text: String): List<String> =
        LINK_REGEX.findAll(text).map { it.value.trim().trimEnd(',', ';', ')', ']', '"', '\'') }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    fun reset() { _state.value = ImportState.Idle }

    private inline fun runImport(crossinline block: suspend () -> Int) {
        _state.value = ImportState.Running(0)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _state.value = ImportState.Success(it) }
                .onFailure { _state.value = ImportState.Failure(it.message ?: "Import failed") }
        }
    }

    companion object {
        private val LINK_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    }
}
