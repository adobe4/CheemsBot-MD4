package com.vinplay.m3u.ui.trash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinplay.m3u.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val loadMore by remember {
        derivedStateOf {
            (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= ui.channels.size - 5
        }
    }
    LaunchedEffect(loadMore) { if (loadMore) viewModel.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (ui.channels.isNotEmpty()) {
                        TextButton(onClick = viewModel::restoreAll) { Text("Restore all") }
                        TextButton(onClick = viewModel::emptyTrash) { Text("Empty") }
                    }
                }
            )
        }
    ) { padding ->
        if (ui.channels.isEmpty()) {
            EmptyState(
                icon = Icons.Default.DeleteForever,
                title = "Trash is empty",
                subtitle = "Deleted channels land here so you can restore them.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
                items(ui.channels, key = { it.id }) { channel ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                channel.groupTitle.ifBlank { channel.kind.name },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.restore(channel.id) }) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore")
                        }
                        IconButton(onClick = { viewModel.deleteForever(channel.id) }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Delete forever")
                        }
                    }
                }
            }
        }
    }
}
