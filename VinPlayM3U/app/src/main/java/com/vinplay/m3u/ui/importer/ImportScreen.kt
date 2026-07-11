package com.vinplay.m3u.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onDone: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        // Accept any type; many providers report M3U as octet-stream/text.
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFromFile(it) } }

    var url by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var bulk by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import channels") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Sources are merged into this playlist. Import as many as you like — parsing streams, so very large lists won't run out of memory.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Local file
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(Icons.Default.FileOpen, "Local file")
                    Text(
                        "Pick an .m3u / .m3u8 file from device storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose file") }
                }
            }

            // Remote URL
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(Icons.Default.Link, "Remote URL")
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text("https://…/playlist.m3u") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.importFromUrl(url) },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Download & import") }
                }
            }

            // Pasted text
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(Icons.Default.ContentPaste, "Paste text")
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        label = { Text("#EXTM3U …") },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                    Button(
                        onClick = { viewModel.importFromText(pasted) },
                        enabled = pasted.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import pasted") }
                }
            }

            // Bulk import — multiple links at once
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(Icons.Default.Link, "Bulk import (multiple links)")
                    Text(
                        "Paste several M3U links (one per line, or mixed into any text). Every http/https link is detected and imported in turn.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bulk,
                        onValueChange = { bulk = it },
                        label = { Text("https://…/a.m3u\nhttps://…/b.m3u8\n…") },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                    Button(
                        onClick = { viewModel.importBulkLinks(bulk) },
                        enabled = bulk.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Detect & import all") }
                }
            }
        }
    }

    when (val s = state) {
        is ImportViewModel.ImportState.Running -> ProgressDialog(s.imported, s.note)
        is ImportViewModel.ImportState.Success -> ResultDialog(
            title = "Import complete",
            message = "Imported ${s.imported} channels.",
            onDismiss = { viewModel.reset(); onDone() }
        )
        is ImportViewModel.ImportState.Failure -> ResultDialog(
            title = "Import failed",
            message = s.message,
            onDismiss = { viewModel.reset() }
        )
        ImportViewModel.ImportState.Idle -> Unit
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.layout.Spacer(Modifier.height(0.dp))
        Text(
            "  $title",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ProgressDialog(imported: Int, note: String? = null) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Importing…") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator()
                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                Text(
                    "Imported ${"%,d".format(imported)} channels…",
                    textAlign = TextAlign.Center
                )
                note?.let {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    )
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(title) },
        text = { Text(message) }
    )
}
