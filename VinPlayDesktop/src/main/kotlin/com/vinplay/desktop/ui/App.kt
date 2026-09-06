package com.vinplay.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import com.vinplay.desktop.data.Channel
import com.vinplay.desktop.data.ChannelKind
import com.vinplay.desktop.data.TestStatus
import com.vinplay.desktop.player.AppPaths
import com.vinplay.desktop.player.ExternalPlayer
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Red dark palette.
 *
 * Every `on…` colour here is deliberately light. Material would normally put dark text on a bright
 * container, but this app is dark-only and dark-on-dark was the exact complaint, so contrast is
 * always achieved with light text instead.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF4757),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5A1A21),
    onPrimaryContainer = Color(0xFFFFD9DC),
    inversePrimary = Color(0xFFFF8A94),
    secondary = Color(0xFFFF8A6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF4E2018),
    onSecondaryContainer = Color(0xFFFFDCD2),
    tertiary = Color(0xFFE0B036),
    onTertiary = Color(0xFF1A1200),
    tertiaryContainer = Color(0xFF4A3A08),
    onTertiaryContainer = Color(0xFFFFE8AE),
    background = Color(0xFF121013),
    onBackground = Color(0xFFF0EAEB),
    surface = Color(0xFF1A1619),
    onSurface = Color(0xFFF0EAEB),
    surfaceVariant = Color(0xFF2A2226),
    onSurfaceVariant = Color(0xFFC4B7BA),
    outline = Color(0xFF4A3D41),
    outlineVariant = Color(0xFF322A2E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0A08),
    errorContainer = Color(0xFF5A1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF0EAEB),
    inverseOnSurface = Color(0xFF1A1619),
    scrim = Color(0xFF000000)
)

/** Below this width the sidebar collapses behind a menu button instead of squeezing the list. */
private val COMPACT_WIDTH = 820.dp

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
        // Surface — not just a background modifier — is what supplies LocalContentColor. Without
        // one, every Text with no explicit colour falls back to Material's default of black, which
        // is what made channel names invisible on the dark background.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < COMPACT_WIDTH
                var sidebarOpen by remember { mutableStateOf(true) }
                // Narrow window: start with the sidebar out of the way, but let the user pull it back.
                LaunchedEffect(compact) { sidebarOpen = !compact }

                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) {
                        if (sidebarOpen) {
                            Sidebar(
                                state = state,
                                onImport = { showImport = true },
                                onNewPlaylist = { showNewPlaylist = true },
                                onSettings = { showSettings = true },
                                onClose = if (compact) ({ sidebarOpen = false }) else null
                            )
                            HorizontalDividerVertical()
                        }
                        ChannelPane(
                            state = state,
                            compact = compact,
                            onToggleSidebar = if (!sidebarOpen) ({ sidebarOpen = true }) else null,
                            onMoveTo = { showMoveTo = true },
                            onMoveToNew = { showMoveToNew = true },
                            onEdit = { editing = it },
                            onImport = { showImport = true }
                        )
                    }
                    StatusBar(state)
                }
            }
        }

        if (showImport) ImportDialog(state) { showImport = false }
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
        if (showSettings) SettingsDialog { showSettings = false }
        editing?.let { ch ->
            EditChannelDialog(ch, onSave = { n, u, g, l ->
                state.editChannel(ch.id, n, u, g, l); editing = null
            }, onDismiss = { editing = null })
        }
    }
}

@Composable
private fun HorizontalDividerVertical() {
    Box(
        Modifier.fillMaxHeight().size(width = 1.dp, height = 0.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun Sidebar(
    state: AppState,
    onImport: () -> Unit,
    onNewPlaylist: () -> Unit,
    onSettings: () -> Unit,
    onClose: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.widthIn(min = 220.dp, max = 260.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "VinPlay Manager",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Hide sidebar", Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onNewPlaylist, modifier = Modifier.weight(1f), contentPadding = padding8()) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp)); Text(" New", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), contentPadding = padding8()) {
                Icon(Icons.Default.Download, null, Modifier.size(14.dp)); Text(" Import", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionLabel("LIBRARY")
        SidebarRow(
            label = "All channels",
            count = null,
            selected = state.filter.playlistId == null,
            onClick = { state.setPlaylist(null) }
        )

        Spacer(Modifier.height(10.dp))
        SectionLabel("PLAYLISTS")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (state.playlists.isEmpty()) {
                Text(
                    "None yet — use Import.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
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

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth(), contentPadding = padding8()) {
            Icon(Icons.Default.Settings, null, Modifier.size(14.dp)); Text("  Settings", fontSize = 12.sp)
        }
    }
    }
}

@Composable
private fun padding8() = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
        count?.let {
            Text(
                "%,d".format(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onDelete != null) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.MoreVert, "Playlist actions", Modifier.size(14.dp))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete playlist") },
                        onClick = { menu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelPane(
    state: AppState,
    compact: Boolean,
    onToggleSidebar: (() -> Unit)?,
    onMoveTo: () -> Unit,
    onMoveToNew: () -> Unit,
    onEdit: (Channel) -> Unit,
    onImport: () -> Unit
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.channels.size - 20
        }
    }
    LaunchedEffect(nearEnd, state.channels.size) { if (nearEnd) state.loadMore() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // Search row — the menu button only appears when the sidebar is hidden.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onToggleSidebar != null) {
                IconButton(onClick = onToggleSidebar) { Icon(Icons.Default.Menu, "Show playlists") }
            }
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = state::setQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                placeholder = { Text("Search every playlist…", fontSize = 13.sp) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))

        // Chips and actions wrap instead of being clipped when the window is narrow.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KindChip("All", state.filter.kind == null) { state.setKind(null) }
            ChannelKind.entries.forEach { k ->
                KindChip(k.name, state.filter.kind == k) { state.setKind(if (state.filter.kind == k) null else k) }
            }
            KindChip("Trash", state.filter.showDeleted) { state.setShowDeleted(!state.filter.showDeleted) }
        }
        Spacer(Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SmallAction("Test filtered") { state.testFiltered() }
            SmallAction("Remove duplicates") { state.removeDuplicates() }
            SmallAction("Export .m3u") { pickSaveFile()?.let { state.exportTo(it) } }
            SmallAction("Import") { onImport() }
            OutlinedButton(onClick = { state.reload() }, contentPadding = padding8()) {
                Icon(Icons.Default.Refresh, "Refresh", Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.total?.let { "%,d channels".format(it) } ?: "counting…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.selected.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "${state.selected.size} selected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(end = 6.dp).align(Alignment.CenterVertically)
                )
                TextButton(onClick = { state.selectAllLoaded() }, contentPadding = padding8(), colors = selectionButtonColors()) { Text("Select loaded", fontSize = 12.sp) }
                TextButton(onClick = { state.testSelected() }, contentPadding = padding8(), colors = selectionButtonColors()) { Text("Test", fontSize = 12.sp) }
                TextButton(onClick = onMoveTo, contentPadding = padding8(), colors = selectionButtonColors()) { Text("Move to…", fontSize = 12.sp) }
                TextButton(onClick = onMoveToNew, contentPadding = padding8(), colors = selectionButtonColors()) { Text("Move to new", fontSize = 12.sp) }
                if (state.filter.showDeleted) {
                    TextButton(onClick = { state.restoreSelected() }, contentPadding = padding8(), colors = selectionButtonColors()) { Text("Restore", fontSize = 12.sp) }
                    TextButton(onClick = { state.purgeSelected() }, contentPadding = padding8(), colors = selectionButtonColors()) { Text("Delete forever", fontSize = 12.sp) }
                } else {
                    TextButton(onClick = { state.deleteSelected() }, contentPadding = padding8(), colors = selectionButtonColors()) { Text("Trash", fontSize = 12.sp) }
                }
                TextButton(onClick = { state.clearSelection() }, contentPadding = padding8(), colors = selectionButtonColors()) { Text("Clear", fontSize = 12.sp) }
            }
        }

        Spacer(Modifier.height(8.dp))
        val byId = remember(state.playlists) { state.playlists.associate { it.id to it.name } }

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading && state.channels.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.channels.isEmpty() -> Text(
                    if (state.filter.query.isBlank()) "No channels yet — use Import to add a playlist."
                    else "Nothing matches “${state.filter.query}”.",
                    Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.channels, key = { it.id }) { ch ->
                        ChannelRow(
                            channel = ch,
                            playlistName = byId[ch.playlistId] ?: "",
                            compact = compact,
                            checked = ch.id in state.selected,
                            onCheck = { state.toggle(ch.id) },
                            onPlay = { state.play(ch) },
                            onEdit = { onEdit(ch) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/** Buttons that sit on the red selection bar need its light on-container colour, not primary red. */
@Composable
private fun selectionButtonColors() =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = padding8()) { Text(label, fontSize = 12.sp) }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    playlistName: String,
    compact: Boolean,
    checked: Boolean,
    onCheck: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onEdit() }.padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, { onCheck() }, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(8.dp))
        Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor(channel.testStatus)))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            // The subtitle is the first thing to go when space is tight.
            if (!compact) {
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
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.PlayArrow, "Play in VLC", Modifier.size(18.dp))
        }
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val text = state.busy
                ?: state.progress?.let { "Testing ${it.first}/${it.second}" }
                ?: state.statusMessage
                ?: "Ready"
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        }
    }
}

// ---- dialogs ----

@Composable
private fun ImportDialog(state: AppState, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf(state.filter.playlistId ?: state.playlists.firstOrNull()?.id) }
    var newName by remember { mutableStateOf("New playlist") }
    var createNew by remember { mutableStateOf(state.playlists.isEmpty()) }
    var url by remember { mutableStateOf("") }
    var bulk by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    fun withTarget(action: (Long) -> Unit) {
        val id = target
        if (createNew || id == null) state.createPlaylist(newName) { action(it) } else action(id)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Import channels") },
        text = {
            Column(
                Modifier.widthIn(min = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DialogSection("Destination")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(createNew, { createNew = it })
                    Text("Create a new playlist", fontSize = 13.sp)
                }
                if (createNew) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("New playlist name") }, singleLine = true)
                } else {
                    PlaylistDropdown(state, target) { target = it }
                }

                HorizontalDivider()
                DialogSection("From file")
                OutlinedButton(onClick = { pickOpenFile()?.let { f -> withTarget { state.importFile(it, f) } } }) {
                    Text("Choose .m3u file…")
                }

                HorizontalDivider()
                DialogSection("From URL")
                OutlinedTextField(url, { url = it }, label = { Text("https://…/playlist.m3u") }, singleLine = true)
                OutlinedButton(enabled = url.isNotBlank(), onClick = { withTarget { state.importUrl(it, url) } }) {
                    Text("Download & import")
                }

                HorizontalDivider()
                DialogSection("Bulk links (paste many)")
                Text(
                    "Paste any text containing links — one per line or mixed into a message. " +
                        "Every http/https link is detected and imported in turn.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = bulk,
                    onValueChange = { bulk = it },
                    label = { Text("https://…/a.m3u\nhttps://…/b.m3u8") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 150.dp)
                )
                val found = remember(bulk) { state.countLinks(bulk) }
                OutlinedButton(enabled = found > 0, onClick = { withTarget { state.importBulk(it, bulk) } }) {
                    Text(if (found > 0) "Detect & import all ($found found)" else "Detect & import all")
                }

                HorizontalDivider()
                DialogSection("Xtream Codes login")
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
private fun DialogSection(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
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
            Column(Modifier.widthIn(min = 300.dp).verticalScroll(rememberScrollState())) {
                state.playlists.forEach { p ->
                    Text(
                        p.name,
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(p.id) }.padding(12.dp)
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
            Column(Modifier.widthIn(min = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("Stream URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(group, { group = it }, label = { Text("Group") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(logo, { logo = it }, label = { Text("Logo URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
            Column(Modifier.widthIn(min = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Path to VLC — used to play streams with their required headers.", fontSize = 13.sp)
                OutlinedTextField(vlc, { vlc = it }, label = { Text("vlc.exe") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickOpenFile(exe = true)?.let { vlc = it.absolutePath } }) {
                    Text("Browse…")
                }
                HorizontalDivider()
                Text(
                    "Library: ${AppPaths.database.absolutePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        text = {
            OutlinedTextField(
                value, { value = it }, label = { Text(label) }, singleLine = true,
                modifier = Modifier.widthIn(min = 300.dp)
            )
        }
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
    TestStatus.TESTING -> Color(0xFF5AA9FF)
    TestStatus.UNTESTED -> Color(0xFF55556A)
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
