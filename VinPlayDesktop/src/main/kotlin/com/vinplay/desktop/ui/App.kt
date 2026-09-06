package com.vinplay.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinplay.desktop.data.Channel
import com.vinplay.desktop.data.ChannelKind
import com.vinplay.desktop.data.TestStatus
import com.vinplay.desktop.player.AppPaths
import com.vinplay.desktop.player.ExternalPlayer
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    secondary = Color(0xFF00A38C),
    background = Color(0xFF0B0B10),
    surface = Color(0xFF14141C),
    surfaceVariant = Color(0xFF1E1E28)
)

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    LaunchedEffect(Unit) { state.start() }

    var showImport by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var showMoveTo by remember { mutableStateOf(false) }
    var showMoveToNew by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Channel?>(null) }

    MaterialTheme(colorScheme = DarkColors) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.weight(1f)) {
                Sidebar(
                    state = state,
                    onImport = { showImport = true },
                    onNewPlaylist = { showNewPlaylist = true },
                    onSettings = { showSettings = true }
                )
                Divider(Modifier.fillMaxHeight().width(1.dp))
                ChannelPane(
                    state = state,
                    onMoveTo = { showMoveTo = true },
                    onMoveToNew = { showMoveToNew = true },
                    onEdit = { editing = it }
                )
            }
            StatusBar(state)
        }

        if (showImport) {
            ImportDialog(state) { showImport = false }
        }
        if (showNewPlaylist) {
            TextPrompt("New playlist", "Name", "") { name ->
                if (name != null) state.createPlaylist(name)
                showNewPlaylist = false
            }
        }
        if (showMoveToNew) {
            TextPrompt("Move to new playlist", "Playlist name", "") { name ->
                if (name != null) state.moveSelectedToNew(name)
                showMoveToNew = false
            }
        }
        if (showMoveTo) {
            PickPlaylistDialog(state, onPick = { state.moveSelectedTo(it); showMoveTo = false }) {
                showMoveTo = false
            }
        }
        if (showSettings) {
            SettingsDialog { showSettings = false }
        }
        editing?.let { ch ->
            EditChannelDialog(ch, onSave = { n, u, g, l ->
                state.editChannel(ch.id, n, u, g, l); editing = null
            }, onDismiss = { editing = null })
        }
    }
}

@Composable
private fun Sidebar(state: AppState, onImport: () -> Unit, onNewPlaylist: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.width(260.dp).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Text("VinPlay Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNewPlaylist, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(" New")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Text(" Import")
            }
        }
        Spacer(Modifier.height(12.dp))

        SidebarRow(
            label = "All channels",
            count = null,
            selected = state.filter.playlistId == null,
            onClick = { state.setPlaylist(null) }
        )
        Divider(Modifier.padding(vertical = 8.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            state.playlists.forEach { p ->
                SidebarRow(
                    label = p.name,
                    count = p.channelCount,
                    selected = state.filter.playlistId == p.id,
                    onClick = { state.setPlaylist(p.id) },
                    onDelete = { state.deletePlaylist(p.id) }
                )
            }
        }

        Divider(Modifier.padding(vertical = 8.dp))
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null, Modifier.size(16.dp)); Text("  Settings")
        }
    }
}

@Composable
private fun SidebarRow(
    label: String,
    count: Long?,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        count?.let {
            Text(
                "%,d".format(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onDelete != null) {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, "Playlist actions", Modifier.size(14.dp))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Delete playlist") },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun ChannelPane(
    state: AppState,
    onMoveTo: () -> Unit,
    onMoveToNew: () -> Unit,
    onEdit: (Channel) -> Unit
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.channels.size - 20
        }
    }
    LaunchedEffect(nearEnd, state.channels.size) { if (nearEnd) state.loadMore() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = state::setQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search every playlist…") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.filter.kind == null, { state.setKind(null) }, { Text("All") })
            ChannelKind.entries.forEach { k ->
                FilterChip(
                    selected = state.filter.kind == k,
                    onClick = { state.setKind(if (state.filter.kind == k) null else k) },
                    label = { Text(k.name) }
                )
            }
            FilterChip(state.filter.showDeleted, { state.setShowDeleted(!state.filter.showDeleted) }, { Text("Trash") })
            Spacer(Modifier.weight(1f))
            Text(
                state.total?.let { "%,d channels".format(it) } ?: "counting…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.testFiltered() }) { Text("Test filtered") }
            OutlinedButton(onClick = { state.removeDuplicates() }) { Text("Remove duplicates") }
            OutlinedButton(onClick = {
                pickSaveFile()?.let { state.exportTo(it) }
            }) { Text("Export .m3u") }
            OutlinedButton(onClick = { state.reload() }) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            }
        }

        if (state.selected.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${state.selected.size} selected", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.selectAllLoaded() }) { Text("Select loaded") }
                TextButton(onClick = { state.testSelected() }) { Text("Test") }
                TextButton(onClick = onMoveTo) { Text("Move to…") }
                TextButton(onClick = onMoveToNew) { Text("Move to new") }
                if (state.filter.showDeleted) {
                    TextButton(onClick = { state.restoreSelected() }) { Text("Restore") }
                    TextButton(onClick = { state.purgeSelected() }) { Text("Delete forever") }
                } else {
                    TextButton(onClick = { state.deleteSelected() }) { Text("Trash") }
                }
                TextButton(onClick = { state.clearSelection() }) { Text("Clear") }
            }
        }

        Spacer(Modifier.height(8.dp))
        val byId = remember(state.playlists) { state.playlists.associate { it.id to it.name } }

        Box(Modifier.weight(1f)) {
            if (state.loading && state.channels.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (state.channels.isEmpty()) {
                Text(
                    "No channels here yet — use Import to add a playlist.",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(state = listState) {
                    items(state.channels, key = { it.id }) { ch ->
                        ChannelRow(
                            channel = ch,
                            playlistName = byId[ch.playlistId] ?: "",
                            checked = ch.id in state.selected,
                            onCheck = { state.toggle(ch.id) },
                            onPlay = { state.play(ch) },
                            onEdit = { onEdit(ch) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    playlistName: String,
    checked: Boolean,
    onCheck: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onEdit() }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, { onCheck() })
        Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(channel.testStatus)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(channel.kind.name)
                    if (channel.groupTitle.isNotBlank()) append(" · ").append(channel.groupTitle)
                    if (playlistName.isNotBlank()) append(" · ").append(playlistName)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, "Play in VLC") }
    }
}

@Composable
private fun StatusBar(state: AppState) {
    Column {
        state.progress?.let { (done, total) ->
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val text = state.busy
                ?: state.progress?.let { "Testing ${it.first}/${it.second}" }
                ?: state.statusMessage
                ?: "Ready"
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---- dialogs ----

@Composable
private fun ImportDialog(state: AppState, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf(state.filter.playlistId) }
    var newName by remember { mutableStateOf("New playlist") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    fun withTarget(action: (Long) -> Unit) {
        val id = target
        if (id != null) action(id) else state.createPlaylist(newName) { action(it) }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Import channels") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Destination", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(target == null, { target = if (target == null) state.playlists.firstOrNull()?.id else null })
                    Text("Create a new playlist")
                }
                if (target == null) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("New playlist name") }, singleLine = true)
                } else {
                    PlaylistDropdown(state, target) { target = it }
                }

                Divider()
                Text("From file", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { pickOpenFile()?.let { f -> withTarget { state.importFile(it, f) } } }) {
                    Text("Choose .m3u file…")
                }

                Divider()
                Text("From URL", fontWeight = FontWeight.Bold)
                OutlinedTextField(url, { url = it }, label = { Text("https://…/playlist.m3u") }, singleLine = true)
                OutlinedButton(enabled = url.isNotBlank(), onClick = { withTarget { state.importUrl(it, url) } }) {
                    Text("Download & import")
                }

                Divider()
                Text("Xtream Codes login", fontWeight = FontWeight.Bold)
                OutlinedTextField(server, { server = it }, label = { Text("Server (http://host:port)") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true)
                OutlinedButton(
                    enabled = server.isNotBlank(),
                    onClick = { withTarget { state.importXtream(it, server, user, pass) } }
                ) { Text("Connect & import") }
            }
        }
    )
}

@Composable
private fun PlaylistDropdown(state: AppState, selected: Long?, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = state.playlists.firstOrNull { it.id == selected }?.name ?: "Choose playlist"
    Box {
        OutlinedButton(onClick = { open = true }) { Text(name) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            state.playlists.forEach { p ->
                DropdownMenuItem(text = { Text(p.name) }, onClick = { onPick(p.id); open = false })
            }
        }
    }
}

@Composable
private fun PickPlaylistDialog(state: AppState, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Move to playlist") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.playlists.forEach { p ->
                    Text(
                        p.name,
                        Modifier.fillMaxWidth().clickable { onPick(p.id) }.padding(12.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun EditChannelDialog(
    channel: Channel,
    onSave: (String, String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(channel.name) }
    var url by remember { mutableStateOf(channel.url) }
    var group by remember { mutableStateOf(channel.groupTitle) }
    var logo by remember { mutableStateOf(channel.tvgLogo ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(name, url, group, logo) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Stream URL") }, singleLine = true)
                OutlinedTextField(group, { group = it }, label = { Text("Group") }, singleLine = true)
                OutlinedTextField(logo, { logo = it }, label = { Text("Logo URL") }, singleLine = true)
            }
        }
    )
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    var vlc by remember { mutableStateOf(ExternalPlayer.detectVlc() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                AppPaths.saveSetting(ExternalPlayer.VLC_PATH_KEY, vlc); onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Path to VLC — used to play streams with their required headers.")
                OutlinedTextField(vlc, { vlc = it }, label = { Text("vlc.exe") }, singleLine = true)
                OutlinedButton(onClick = { pickOpenFile(exe = true)?.let { vlc = it.absolutePath } }) {
                    Text("Browse…")
                }
                Divider()
                Text("Library: ${AppPaths.database.absolutePath}", style = MaterialTheme.typography.labelSmall)
            }
        }
    )
}

@Composable
private fun TextPrompt(title: String, label: String, initial: String, onResult: (String?) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onResult(value) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Cancel") } },
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) }
    )
}

// ---- helpers ----

fun statusColor(status: TestStatus): Color = when (status) {
    TestStatus.OK -> Color(0xFF3FBF6A)
    TestStatus.UNSTABLE -> Color(0xFFE6C200)
    TestStatus.REDIRECT -> Color(0xFFE0B036)
    TestStatus.DEAD -> Color(0xFFE0483B)
    TestStatus.TIMEOUT -> Color(0xFFE07A3B)
    TestStatus.ERROR -> Color(0xFFB0483B)
    TestStatus.TESTING -> Color(0xFF7C5CFF)
    TestStatus.UNTESTED -> Color(0xFF5A5A66)
}

private fun pickOpenFile(exe: Boolean = false): File? {
    val chooser = JFileChooser()
    if (!exe) chooser.fileFilter = FileNameExtensionFilter("Playlists (*.m3u, *.m3u8, *.txt)", "m3u", "m3u8", "txt")
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun pickSaveFile(): File? {
    val chooser = JFileChooser()
    chooser.selectedFile = File("playlist.m3u")
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
