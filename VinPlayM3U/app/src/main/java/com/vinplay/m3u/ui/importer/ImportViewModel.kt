package com.vinplay.m3u.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vinplay.m3u.data.repository.ImportManager
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
        data class Running(val imported: Int) : ImportState
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

    fun importFromText(text: String) = runImport {
        importManager.importFromText(playlistId, text, progress)
    }

    fun reset() { _state.value = ImportState.Idle }

    private inline fun runImport(crossinline block: suspend () -> Int) {
        _state.value = ImportState.Running(0)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _state.value = ImportState.Success(it) }
                .onFailure { _state.value = ImportState.Failure(it.message ?: "Import failed") }
        }
    }
}
