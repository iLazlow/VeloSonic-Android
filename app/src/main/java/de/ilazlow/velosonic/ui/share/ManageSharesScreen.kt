package de.ilazlow.velosonic.ui.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.share.ShareItem
import de.ilazlow.velosonic.ui.settings.SettingsSectionHeader
import de.ilazlow.velosonic.ui.settings.SettingsTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Mirrors ManageSharesView.swift: shares from every configured server, grouped into per-server
 * sections. Copy/Delete live in a "..." overflow menu per row rather than iOS's swipe actions —
 * matches this codebase's own established convention (see ManageServersScreen/
 * ManageEqPresetsScreen) rather than introducing a new gesture pattern for just this one screen.
 */
@Composable
fun ManageSharesScreen(onBack: () -> Unit, viewModel: ManageSharesViewModel = hiltViewModel()) {
    val shares by viewModel.shares.collectAsStateWithLifecycle()
    val serverNames by viewModel.serverNames.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var deleteTarget by remember { mutableStateOf<ShareItem?>(null) }

    val grouped = remember(shares) { shares.groupBy { it.serverHost } }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Manage Shares", onBack)
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            shares.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No shares yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (host, items) ->
                    item { SettingsSectionHeader(serverNames[host] ?: host) }
                    items(items, key = { "${it.serverHost}_${it.share.id}" }) { item ->
                        ShareRow(
                            item = item,
                            onCopy = { clipboard.setText(AnnotatedString(item.share.url)) },
                            onDelete = { deleteTarget = item }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this share?") },
            text = { Text("The link will stop working immediately.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ShareRow(item: ShareItem, onCopy: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val share = item.share
    val firstEntry = share.entry?.firstOrNull()
    val title = firstEntry?.title ?: share.description?.takeIf { it.isNotBlank() } ?: "Share #${share.id}"
    val subtitle = listOfNotNull(firstEntry?.artist, firstEntry?.album).joinToString(" • ").ifBlank { share.description }
    val visits = share.visitCount ?: 0
    val expiryLabel = formatIsoInstant(share.expires)?.let { "Expires $it" } ?: "No expiry"

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${visits} ${if (visits == 1) "visit" else "visits"} • $expiryLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Copy Link") }, onClick = { showMenu = false; onCopy() })
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

private fun formatIsoInstant(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(instant)
}
