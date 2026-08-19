package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.lyrics.RadiantLyricsCacheSummary
import java.text.DateFormat
import java.util.Date

/** Mirrors iOS's `RadiantLyricsCacheListView` — every on-disk cached Radiant Lyrics entry, with
 *  per-row delete and a toolbar "clear all" action. */
@Composable
fun RadiantLyricsCacheListScreen(onBack: () -> Unit, onEntryClick: (String) -> Unit, viewModel: RadiantLyricsCacheListViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var confirmClearAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = stringResource(id = R.string.settings_radiant_list_title),
            onBack = onBack,
            actions = {
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { confirmClearAll = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(id = R.string.settings_radiant_clear_all_content_description), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(id = R.string.settings_radiant_empty_state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { entry ->
                    CacheEntryRow(entry = entry, onClick = { onEntryClick(entry.id) }, onDelete = { viewModel.delete(entry.id) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
                item {
                    Text(
                        text = stringResource(id = R.string.settings_radiant_cached_count, entries.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(id = R.string.settings_radiant_clear_all_dialog_title)) },
            text = { Text(stringResource(id = R.string.settings_radiant_clear_all_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); confirmClearAll = false }) {
                    Text(stringResource(id = R.string.settings_radiant_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text(stringResource(id = R.string.settings_radiant_cancel)) } }
        )
    }
}

@Composable
private fun CacheEntryRow(entry: RadiantLyricsCacheSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                BadgeChip(
                    icon = if (entry.hasWordTiming) Icons.Filled.GraphicEq else Icons.Filled.FormatAlignLeft,
                    label = if (entry.hasWordTiming) stringResource(id = R.string.settings_radiant_word_by_word) else stringResource(id = R.string.settings_radiant_line_by_line)
                )
                if (entry.romanized) BadgeChip(icon = Icons.Filled.TextFormat, label = stringResource(id = R.string.settings_radiant_romanized))
                if (entry.aiSynthesized) BadgeChip(icon = Icons.Filled.AutoAwesome, label = stringResource(id = R.string.settings_radiant_ai_synthesize))
            }
        }
        Text(
            text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.cachedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(id = R.string.settings_radiant_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BadgeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
