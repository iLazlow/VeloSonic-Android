package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.lyrics.LyricLine

/** Mirrors iOS's `RadiantLyricsCacheDetailView` — one cached entry's full line content plus a
 *  raw-JSON disclosure and a delete action. */
@Composable
fun RadiantLyricsCacheDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    viewModel: RadiantLyricsCacheDetailViewModel = hiltViewModel()
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val rawJson by viewModel.rawJson.collectAsStateWithLifecycle()
    var showRaw by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) { viewModel.load(entryId) }

    val deleteLabel = stringResource(id = R.string.settings_radiant_delete)

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = stringResource(id = R.string.settings_radiant_detail_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = deleteLabel, tint = MaterialTheme.colorScheme.error)
                }
            }
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSectionHeader(stringResource(id = R.string.settings_radiant_section_content)) }
            items(lines) { line: LyricLine ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(text = timeString(line.startMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    Text(
                        text = (line.romanizedText ?: line.text).ifEmpty { " " },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRaw = !showRaw }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(id = R.string.settings_radiant_raw_content), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(if (showRaw) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
            }
            if (showRaw) {
                item {
                    Text(
                        text = rawJson,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(id = R.string.settings_radiant_delete_dialog_title)) },
            text = { Text(stringResource(id = R.string.settings_radiant_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entryId); confirmDelete = false; onBack() }) {
                    Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(id = R.string.settings_radiant_cancel)) } }
        )
    }
}

private fun timeString(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
