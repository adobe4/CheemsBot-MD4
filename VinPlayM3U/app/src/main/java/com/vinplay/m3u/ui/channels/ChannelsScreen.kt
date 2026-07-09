package com.vinplay.m3u.ui.channels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vinplay.m3u.data.model.TestStatus
import com.vinplay.m3u.ui.components.EmptyState
import com.vinplay.m3u.ui.components.LoadingSkeleton
import com.vinplay.m3u.ui.components.StatusDot
import com.vinplay.m3u.ui.playlists.TextPromptDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onBack: () -> Unit,
    onImport: (Long) -> Unit,
    onOpenTrash: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val testProgress by viewModel.testProgress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var searchOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChannelEntity?>(null) }
    var movingGroup by remember { mutableStateOf<ChannelEntity?>(null) }
    var movingPlaylist by remember { mutableStateOf<ChannelEntity?>(null) }
    var renamingGroup by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null) viewModel.export(uri) { count ->
            scope.launch {
                snackbar.showSnackbar(if (count != null) "Exported $count channels" else "Export failed")
            }
        }
    }

    // Endless pagination: load more when the last loaded item scrolls into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= ui.channels.size - 10
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.playlistName.ifBlank { "Channels" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${"%,d".format(ui.totalFiltered)} channels",
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
                    IconButton(onClick = { onImport(ui.playlistId) }) {
                        Icon(Icons.Default.Download, contentDescription = "Import")
                    }
                    IconButton(onClick = { onOpenTrash(ui.playlistId) }) {
                        BadgedBox(badge = { if (ui.trashCount > 0) Badge { Text("${ui.trashCount}") } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Trash")
                        }
                    }
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(text = { Text("Test all (filtered)") }, onClick = {
                            overflowOpen = false; viewModel.testAllFiltered()
                        })
                        DropdownMenuItem(text = { Text("Delete all (filtered)") }, onClick = {
                            overflowOpen = false
                            viewModel.deleteFiltered()
                            scope.launch {
                                val r = snackbar.showSnackbar("Deleted filtered channels", actionLabel = "Undo")
                                if (r == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                            }
                        })
                        ui.filter.group?.let { g ->
                            DropdownMenuItem(text = { Text("Rename group \"$g\"") }, onClick = {
                                overflowOpen = false; renamingGroup = g
                            })
                        }
                        DropdownMenuItem(text = { Text("Export M3U") }, onClick = {
                            overflowOpen = false
                            exportLauncher.launch("${ui.playlistName.ifBlank { "playlist" }}.m3u")
                        })
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = ui.filter.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search channels") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            FilterBar(
                groups = ui.groups,
                selectedGroup = ui.filter.group,
                selectedKind = ui.filter.kind,
                onGroup = viewModel::setGroup,
                onKind = viewModel::setKind
            )

            if (testProgress.running) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "Testing ${testProgress.done}/${testProgress.total}…",
                        style = MaterialTheme.typography.labelMedium
                    )
                    LinearProgressIndicator(
                        progress = { if (testProgress.total == 0) 0f else testProgress.done.toFloat() / testProgress.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when {
                ui.loading && ui.channels.isEmpty() -> LoadingSkeleton()
                ui.channels.isEmpty() -> EmptyState(
                    icon = Icons.Default.PlayArrow,
                    title = "No channels",
                    subtitle = "Import an M3U or adjust your filters to see channels here."
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(ui.channels, key = { _, c -> c.id }) { index, channel ->
                        ChannelRow(
                            channel = channel,
                            canMoveUp = index > 0,
                            canMoveDown = index < ui.channels.size - 1,
                            onMoveUp = { viewModel.reorder(index, index - 1) },
                            onMoveDown = { viewModel.reorder(index, index + 1) },
                            onPlay = { onPlay(channel.id) },
                            onEdit = { editing = channel },
                            onMoveGroup = { movingGroup = channel },
                            onMovePlaylist = { movingPlaylist = channel },
                            onDelete = {
                                viewModel.softDelete(channel.id)
                                scope.launch {
                                    val r = snackbar.showSnackbar("Deleted \"${channel.name}\"", actionLabel = "Undo")
                                    if (r == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ---- Dialogs ----
    editing?.let { ch ->
        EditChannelDialog(
            channel = ch,
            onSave = { viewModel.updateChannel(it); editing = null },
            onDismiss = { editing = null }
        )
    }
    movingGroup?.let { ch ->
        MoveToGroupDialog(
            groups = ui.groups,
            current = ch.groupTitle,
            onConfirm = { viewModel.moveToGroup(ch.id, it); movingGroup = null },
            onDismiss = { movingGroup = null }
        )
    }
    movingPlaylist?.let { ch ->
        MoveToPlaylistDialog(
            playlists = ui.otherPlaylists,
            onConfirm = { viewModel.moveToPlaylist(ch.id, it); movingPlaylist = null },
            onDismiss = { movingPlaylist = null }
        )
    }
    renamingGroup?.let { old ->
        TextPromptDialog(
            title = "Rename group",
            initial = old,
            confirmLabel = "Rename",
            label = "Group name",
            onConfirm = { viewModel.renameGroup(old, it); renamingGroup = null },
            onDismiss = { renamingGroup = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    groups: List<String>,
    selectedGroup: String?,
    selectedKind: ChannelKind?,
    onGroup: (String?) -> Unit,
    onKind: (ChannelKind?) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = selectedKind == null, onClick = { onKind(null) }, label = { Text("All") })
            ChannelKind.entries.forEach { kind ->
                FilterChip(
                    selected = selectedKind == kind,
                    onClick = { onKind(if (selectedKind == kind) null else kind) },
                    label = { Text(kind.name) }
                )
            }
        }
        if (groups.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onGroup(null) },
                    label = { Text(if (selectedGroup == null) "All groups ✓" else "All groups") }
                )
                groups.forEach { g ->
                    FilterChip(
                        selected = selectedGroup == g,
                        onClick = { onGroup(if (selectedGroup == g) null else g) },
                        label = { Text(g.ifBlank { "(none)" }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onMoveGroup: () -> Unit,
    onMovePlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = statusColor(channel.testStatus))
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
        IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Edit / replace link") }, onClick = { menu = false; onEdit() })
            if (canMoveUp) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMoveUp() })
            if (canMoveDown) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMoveDown() })
            DropdownMenuItem(text = { Text("Move to group") }, onClick = { menu = false; onMoveGroup() })
            DropdownMenuItem(
                text = { Text("Move to playlist") },
                leadingIcon = { Icon(Icons.Default.Upload, null) },
                onClick = { menu = false; onMovePlaylist() }
            )
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun statusColor(status: TestStatus): Color = when (status) {
    TestStatus.OK -> Color(0xFF3FBF6A)
    TestStatus.REDIRECT -> Color(0xFFE0B036)
    TestStatus.DEAD -> Color(0xFFE0483B)
    TestStatus.TIMEOUT -> Color(0xFFE07A3B)
    TestStatus.ERROR -> Color(0xFFB0483B)
    TestStatus.TESTING -> MaterialTheme.colorScheme.primary
    TestStatus.UNTESTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
}
