package de.ilazlow.velosonic.ui.playlists

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.dto.PlaylistDto
import de.ilazlow.velosonic.data.network.dto.TrackDto
import de.ilazlow.velosonic.data.playlist.ImportPlaylistInfo
import de.ilazlow.velosonic.data.playlist.PlaylistImportSource
import de.ilazlow.velosonic.ui.common.CoverArtTile
import de.ilazlow.velosonic.ui.settings.SettingsTopBar

/**
 * Mirrors PlaylistImportView.swift: paste a Spotify link (or pick an Exportify CSV) → preview the
 * source playlist and how many tracks matched the target server's library → optionally pick a
 * target server → import. See [PlaylistImportViewModel]'s doc comment for the full pipeline.
 */
@Composable
fun PlaylistImportScreen(onBack: () -> Unit, viewModel: PlaylistImportViewModel = hiltViewModel()) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val urlText by viewModel.urlText.collectAsStateWithLifecycle()
    val pendingCsv by viewModel.pendingCsv.collectAsStateWithLifecycle()
    val playlistInfo by viewModel.playlistInfo.collectAsStateWithLifecycle()
    val matchResults by viewModel.matchResults.collectAsStateWithLifecycle()
    val existingPlaylist by viewModel.existingPlaylist.collectAsStateWithLifecycle()
    val selectedServerHost by viewModel.selectedServerHost.collectAsStateWithLifecycle()
    val servers = viewModel.servers
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val filename = queryDisplayName(uri, context) ?: "playlist.csv"
        viewModel.setCsvFile(bytes, filename)
    }

    val isBusy = phase is ImportPhase.LoadingPlaylist || phase is ImportPhase.MatchingTracks
    val isImporting = phase is ImportPhase.Importing
    val detectedSource = viewModel.detectSource(urlText)
    val canLoad = !isBusy && !isImporting &&
        (pendingCsv != null || (detectedSource == PlaylistImportSource.SPOTIFY && urlText.isNotBlank()))
    val matchedCount = matchResults.count { it.isMatched }
    val unmatchedCount = matchResults.size - matchedCount
    val canImport = phase == ImportPhase.ReadyToImport && selectedServerHost != null && matchedCount > 0

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsTopBar(title = "Playlist Import", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                UrlSection(
                    urlText = urlText,
                    onUrlTextChange = viewModel::onUrlTextChange,
                    pendingCsvFilename = pendingCsv?.filename,
                    detectedSource = detectedSource,
                    enabled = !isBusy && !isImporting,
                    canLoad = canLoad,
                    phase = phase,
                    onPaste = { clipboard.getText()?.text?.let { viewModel.onUrlTextChange(it) } },
                    onClear = { viewModel.onUrlTextChange("") },
                    onClearCsv = viewModel::clearCsv,
                    onPickCsv = {
                        filePickerLauncher.launch(
                            arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")
                        )
                    },
                    onLoad = viewModel::loadPlaylist
                )
            }

            val info = playlistInfo
            if (info != null) {
                item { SourcePreviewSection(info = info, matchResults = matchResults, unmatchedCount = unmatchedCount) }
            }

            existingPlaylist?.let { existing ->
                item { ExistingPlaylistSection(existing = existing, serverHost = selectedServerHost, coverArtUrl = viewModel::coverArtUrl) }
            }

            if (info != null && servers.size > 1) {
                item {
                    ServerPickerSection(
                        servers = servers,
                        selectedHost = selectedServerHost,
                        enabled = !isBusy && !isImporting,
                        onServerSelected = viewModel::onServerChanged
                    )
                }
            }

            if (info != null) {
                item {
                    ImportSection(
                        phase = phase,
                        canImport = canImport,
                        matchedCount = matchedCount,
                        unmatchedCount = unmatchedCount,
                        onStartImport = viewModel::startImport,
                        onClose = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun IconLabelButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label)
        }
    }
}

@Composable
private fun UrlSection(
    urlText: String,
    onUrlTextChange: (String) -> Unit,
    pendingCsvFilename: String?,
    detectedSource: PlaylistImportSource?,
    enabled: Boolean,
    canLoad: Boolean,
    phase: ImportPhase,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onClearCsv: () -> Unit,
    onPickCsv: () -> Unit,
    onLoad: () -> Unit
) {
    SectionCard(title = "Playlist URL") {
        if (pendingCsvFilename != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(pendingCsvFilename, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Exportify CSV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClearCsv, enabled = enabled) {
                    Icon(Icons.Filled.Cancel, contentDescription = "Remove file")
                }
            }
        } else {
            OutlinedTextField(
                value = urlText,
                onValueChange = onUrlTextChange,
                placeholder = { Text("Spotify or other URL") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            if (detectedSource != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(detectedSource.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconLabelButton(Icons.Filled.ContentPaste, "Paste", onPaste, enabled)
                IconLabelButton(Icons.Filled.Clear, "Clear", onClear, enabled && urlText.isNotEmpty())
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        IconLabelButton(
            icon = Icons.Filled.UploadFile,
            label = if (pendingCsvFilename != null) "Other CSV File…" else "Import CSV File (Exportify)",
            onClick = onPickCsv,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = onLoad, enabled = canLoad, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoadingPhase(phase)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(loadButtonLabel(phase))
            }
        }

        if (phase is ImportPhase.Failed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(phase.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

private fun isLoadingPhase(phase: ImportPhase) = phase is ImportPhase.LoadingPlaylist || phase is ImportPhase.MatchingTracks

private fun loadButtonLabel(phase: ImportPhase): String = when (phase) {
    is ImportPhase.LoadingPlaylist -> "Loading…"
    is ImportPhase.MatchingTracks -> "Matching tracks…"
    else -> "Load Playlist"
}

@Composable
private fun SourcePreviewSection(info: ImportPlaylistInfo, matchResults: List<ImportTrackMatch>, unmatchedCount: Int) {
    SectionCard(title = "Source Playlist") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverArtTile(url = info.artworkUrl, contentDescription = info.name, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val caption = buildString {
                    append("${info.totalTracks} tracks")
                    if (unmatchedCount > 0) append(" • $unmatchedCount not found")
                }
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unmatchedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (matchResults.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                matchResults.forEach { match ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CoverArtTile(
                            url = match.sourceTrack.artworkUrl,
                            contentDescription = match.sourceTrack.title,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                match.sourceTrack.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (match.isMatched) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                match.sourceTrack.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (match.isMatched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!match.isMatched) {
                            Icon(
                                Icons.Filled.Cancel,
                                contentDescription = "Not found",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExistingPlaylistSection(existing: PlaylistDto, serverHost: String?, coverArtUrl: (String, String?, Int) -> String?) {
    SectionCard(title = "Existing Playlist on Server") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(existing.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Will be updated (ID preserved)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        val entries = existing.entry.orEmpty()
        if (entries.isEmpty()) {
            Text(
                "No tracks yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.take(20).forEach { track: TrackDto ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val artId = track.coverArt ?: track.albumId ?: track.id
                        CoverArtTile(
                            url = serverHost?.let { coverArtUrl(it, artId, 120) },
                            contentDescription = track.title,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerPickerSection(
    servers: List<ServerConfigEntity>,
    selectedHost: String?,
    enabled: Boolean,
    onServerSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = servers.firstOrNull { it.host == selectedHost }
    SectionCard(title = "Target Server") {
        Box {
            OutlinedButton(onClick = { if (enabled) expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.name?.ifBlank { selected.host } ?: "Select a server", modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                servers.forEach { config ->
                    DropdownMenuItem(
                        text = { Text(config.name.ifBlank { config.host }) },
                        onClick = { expanded = false; onServerSelected(config.host) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportSection(
    phase: ImportPhase,
    canImport: Boolean,
    matchedCount: Int,
    unmatchedCount: Int,
    onStartImport: () -> Unit,
    onClose: () -> Unit
) {
    SectionCard(title = "Import") {
        Button(onClick = onStartImport, enabled = canImport, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (phase) {
                    is ImportPhase.Importing -> {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Importing…")
                    }
                    ImportPhase.Done -> {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Import Complete")
                    }
                    else -> Text("Start Import")
                }
            }
        }
        if (matchedCount > 0 || unmatchedCount > 0) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("$matchedCount matched", style = MaterialTheme.typography.bodySmall)
                }
                if (unmatchedCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text("$unmatchedCount skipped (not found)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (phase == ImportPhase.Done) {
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Close")
            }
        }
    }
}

private fun queryDisplayName(uri: Uri, context: Context): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
    }
    return null
}
