package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.db.ServerConfigEntity

/**
 * Mirrors iOS's `ServerListView` — reached from Manage Servers' "Manage Addresses" row, this is
 * the server list (add/edit-by-delete-and-re-add/remove). No iOS counterpart for reordering (see
 * the drag-handle doc comment below), so it's grafted directly onto the same rows rather than
 * living as its own separate screen/list — one list of servers, not a list of servers plus a
 * second differently-ordered list of the same servers.
 */
@Composable
fun ServerListScreen(
    onBack: () -> Unit,
    onServerClick: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    var localOrder by remember { mutableStateOf<List<ServerConfigEntity>>(emptyList()) }
    // Re-syncs added/removed servers (and refreshes edited fields on existing ones) without
    // clobbering the position of a server the user just dragged — a plain `localOrder = servers`
    // here would fight [SettingsViewModel.setServerOrder]'s own re-emission after every drag.
    LaunchedEffect(servers) {
        val byHost = servers.associateBy { it.host }
        val survivingInOrder = localOrder.mapNotNull { byHost[it.host] }
        val newlyAdded = servers.filter { it.host !in localOrder.map { s -> s.host }.toSet() }
        localOrder = survivingInOrder + newlyAdded
    }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = remember { mutableFloatStateOf(0f) }

    var showAddServer by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ServerConfigEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Manage Addresses", onBack)
        if (localOrder.size > 1) {
            Text(
                text = "Drag to reorder. This order is used everywhere multiple servers are combined or listed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
            itemsIndexed(localOrder, key = { _, server -> server.host }) { index, server ->
                val isDragged = index == draggedIndex
                val currentIndex by rememberUpdatedState(index)
                var showMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffsetY else 0f }
                        .alpha(if (isDragged) 0.9f else 1f)
                        .background(
                            if (isDragged) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surface
                        )
                        .onGloballyPositioned { coords ->
                            if (rowHeightPx.floatValue == 0f) rowHeightPx.floatValue = coords.size.height.toFloat()
                        }
                        .clickable(onClick = { onServerClick(server.host) })
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name.ifBlank { server.host },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${server.host} · ${server.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Remove Server", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; removeTarget = server }
                            )
                        }
                    }
                    if (localOrder.size > 1) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedIndex = currentIndex
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                        viewModel.setServerOrder(localOrder.map { it.host })
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val from = draggedIndex ?: return@detectDragGestures
                                    val height = rowHeightPx.floatValue
                                    dragOffsetY += dragAmount.y
                                    if (height <= 0f) return@detectDragGestures
                                    val moveBy = (dragOffsetY / height).toInt()
                                    if (moveBy == 0) return@detectDragGestures
                                    val to = (from + moveBy).coerceIn(0, localOrder.lastIndex)
                                    if (to != from) {
                                        localOrder = localOrder.toMutableList().apply { add(to, removeAt(from)) }
                                        dragOffsetY -= moveBy * height
                                        draggedIndex = to
                                    }
                                }
                            }
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showAddServer = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Add Server")
                }
            }
        }
    }

    if (showAddServer) {
        AddServerSheet(onDismiss = { showAddServer = false })
    }

    removeTarget?.let { config ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove \"${config.name.ifBlank { config.host }}\"?") },
            text = { Text("This removes its cached library data and saved credentials from this device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeServer(config); removeTarget = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }
}
