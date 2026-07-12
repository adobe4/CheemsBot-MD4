package com.vinplay.m3u.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: Long = savedStateHandle.get<Long>(Destinations.ARG_PLAYLIST_ID) ?: 0L

    sealed interface ImportState {
        data object Idle : ImportState
        data class Running(val imported: Int, val note: String? = null) : ImportState
        data class Success(val imported: Int) : ImportState
        data class Failure(val message: String) : ImportState
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
