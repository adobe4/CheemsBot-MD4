package com.vinplay.m3u.ui.allchannels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinplay.m3u.data.local.entity.ChannelEntity
import com.vinplay.m3u.data.model.ChannelKind
import com.vinplay.m3u.ui.channels.EditChannelDialog
import com.vinplay.m3u.ui.channels.MoveToPlaylistDialog
import com.vinplay.m3u.ui.components.EmptyState
import com.vinplay.m3u.ui.components.LoadingSkeleton
import com.vinplay.m3u.ui.components.LogoThumb
import com.vinplay.m3u.ui.playlists.TextPromptDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AllChannelsScreen(
    onBack: () -> Unit,
    viewModel: AllChannelsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val testProgress by viewModel.testProgress.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectionActive = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()

    var searchOpen by remember { mutableStateOf(true) }
    var overflowOpen by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChannelEntity?>(null) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }

    val loadMore by remember {
        derivedStateOf {
            (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= ui.channels.size - 10
        }
    }
    LaunchedEffect(loadMore) { if (loadMore) viewModel.loadMore() }
    LaunchedEffect(testProgress.running) { viewModel.refresh() }

    Scaffold(
        topBar = {
            if (selectionActive) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAllLoaded() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all loaded")
                        }
                        IconButton(onClick = { moveTo = true }) {
                            Icon(Icons.Default.Upload, contentDescription = "Move to playlist")
                        }
                        IconButton(onClick = { newPlaylist = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Move to new playlist")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("All channels")
                            Text(
                                "${"%,d".format(ui.totalFiltered)} across all playlists",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(text = { Text("Test results (filtered)") }, onClick = {
                                overflowOpen = false; viewModel.testFiltered()
                            })
                            DropdownMenuItem(text = { Text("Deep test — freeze check (yellow)") }, onClick = {
                                overflowOpen = false; viewModel.deepTestFiltered()
                            })
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = ui.filter.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search across all playlists") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = ui.filter.kind == null, onClick = { viewModel.setKind(null) }, label = { Text("All") })
                ChannelKind.entries.forEach { k ->
                    FilterChip(
                        selected = ui.filter.kind == k,
                        onClick = { viewModel.setKind(if (ui.filter.kind == k) null else k) },
                        label = { Text(k.name) }
                    )
                }
            }
            if (testProgress.running) {
                Text(
                    "Testing ${testProgress.done}/${testProgress.total}…",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            when {
                ui.loading && ui.channels.isEmpty() -> LoadingSkeleton()
                ui.channels.isEmpty() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "No channels",
                    subtitle = "Import playlists, then browse and search every channel here."
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(ui.channels, key = { _, c -> c.id }) { _, channel ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectionActive) viewModel.toggleSelect(channel.id) else viewModel.play(channel)
                                    },
                                    onLongClick = { viewModel.toggleSelect(channel.id) }
                                )
                                .background(if (channel.id in selectedIds) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LogoThumb(
                                logo = channel.tvgLogo,
                                status = channel.testStatus,
                                selected = if (selectionActive) channel.id in selectedIds else null
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    buildString {
                                        append(channel.kind.name)
                                        if (channel.groupTitle.isNotBlank()) append(" · ").append(channel.groupTitle)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Per-row actions so a channel found by searching every playlist can be
                            // fixed right here instead of having to open its playlist first.
                            if (!selectionActive) {
                                Box {
                                    IconButton(onClick = { menuFor = channel.id }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Channel actions")
                                    }
                                    DropdownMenu(
                                        expanded = menuFor == channel.id,
                                        onDismissRequest = { menuFor = null }
                                    ) {
                                        DropdownMenuItem(text = { Text("Play") }, onClick = {
                                            menuFor = null; viewModel.play(channel)
                                        })
                                        DropdownMenuItem(text = { Text("Edit channel") }, onClick = {
                                            menuFor = null; editing = channel
                                        })
                                        DropdownMenuItem(text = { Text("Move to trash") }, onClick = {
                                            menuFor = null; viewModel.softDelete(channel)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { channel ->
        EditChannelDialog(
            channel = channel,
            onSave = { viewModel.updateChannel(it); editing = null },
            onDismiss = { editing = null }
        )
    }

    if (moveTo) {
        MoveToPlaylistDialog(
            playlists = ui.playlists,
            onConfirm = { viewModel.moveSelectedToPlaylist(it); moveTo = false },
            onDismiss = { moveTo = false }
        )
    }
    if (newPlaylist) {
        TextPromptDialog(
            title = "Move to new playlist",
            initial = "",
            confirmLabel = "Create & move",
            label = "Playlist name",
            onConfirm = { viewModel.moveSelectedToNewPlaylist(it); newPlaylist = false },
            onDismiss = { newPlaylist = false }
        )
    }
}
