package com.vinplay.m3u.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vinplay.m3u.data.local.PlaylistSummary
import com.vinplay.m3u.data.local.entity.ChannelEntity

/** Full channel editor: rename, replace the stream link, retag group. */
@Composable
fun EditChannelDialog(
    channel: ChannelEntity,
    onSave: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(channel.name) }
    var url by remember { mutableStateOf(channel.url) }
    var group by remember { mutableStateOf(channel.groupTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit channel") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("Stream URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(group, { group = it }, label = { Text("Group") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    onSave(
                        channel.copy(
                            name = name.trim(),
                            url = url.trim(),
                            groupTitle = group.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Pick an existing group or type a new one to move a channel into. */
@Composable
fun MoveToGroupDialog(
    groups: List<String>,
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to group") },
        text = {
            Column(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(custom, { custom = it }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    groups.filter { it.isNotBlank() }.forEach { g ->
                        Text(
                            g,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { custom = g }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = custom.isNotBlank(), onClick = { onConfirm(custom.trim()) }) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Move a channel into a different playlist. */
@Composable
fun MoveToPlaylistDialog(
    playlists: List<PlaylistSummary>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("No other playlists. Create one first.")
            } else {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    playlists.forEach { p ->
                        Text(
                            "${p.name}  (${p.channelCount})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(p.id) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
