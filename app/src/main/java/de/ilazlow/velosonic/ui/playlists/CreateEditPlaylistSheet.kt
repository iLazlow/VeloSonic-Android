package de.ilazlow.velosonic.ui.playlists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.playlist.SPCriteria
import de.ilazlow.velosonic.domain.isNavidrome
import kotlinx.coroutines.launch

/**
 * Mirrors CreatePlaylistSheet/EditPlaylistSheet.swift's Form-in-a-sheet shape (name field, public
 * toggle, server picker on create when multiple servers exist), EditPlaylistSheet's artwork-upload
 * row (edit mode only, gated by [showArtworkPicker] i.e.
 * [de.ilazlow.velosonic.domain.supportsPlaylistArtworkUpload]), and both sheets' smart-playlist
 * handling: a create-only "Smart Playlist" toggle (Navidrome servers only) that reveals
 * [SmartPlaylistEditorSection], and — in edit mode — that same editor shown unconditionally
 * whenever the playlist being edited already is one, lazily fetching its rules via
 * [fetchSmartRules] if they weren't already cached locally. Artwork upload and the smart-rules
 * editor are mutually exclusive in edit mode, matching iOS's `if isSmart {...} else {artwork}`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditPlaylistSheet(
    isEdit: Boolean,
    initialName: String = "",
    initialPublic: Boolean = false,
    configs: List<ServerConfigEntity> = emptyList(),
    currentCoverArtUrl: String? = null,
    showArtworkPicker: Boolean = false,
    isSmartPlaylist: Boolean = false,
    smartRulesJson: String? = null,
    fetchSmartRules: (suspend () -> String?)? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        isPublic: Boolean,
        serverHost: String,
        newCoverBytes: ByteArray?,
        newCoverMimeType: String?,
        isSmartPlaylist: Boolean,
        smartRulesJson: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var isPublic by remember { mutableStateOf(initialPublic) }
    var selectedHost by remember { mutableStateOf(configs.firstOrNull()?.host.orEmpty()) }
    val sheetState = rememberModalBottomSheetState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pickedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var pickedCoverBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickedCoverMimeType by remember { mutableStateOf<String?>(null) }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickedCoverUri = uri
        coroutineScope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            pickedCoverBytes = bytes
            pickedCoverMimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        }
    }

    // Create-mode toggle only — edit mode can't convert a playlist's type, it can only edit an
    // already-smart one's rules (isSmartPlaylist parameter, fixed for the sheet's lifetime).
    var createIsSmartPlaylist by remember { mutableStateOf(false) }
    var criteria by remember { mutableStateOf(smartRulesJson?.let { SPCriteria.fromJsonString(it) } ?: SPCriteria()) }
    var rulesLoading by remember { mutableStateOf(false) }
    var rulesLoadFailed by remember { mutableStateOf(false) }
    var retryToken by remember { mutableStateOf(0) }
    val effectiveIsSmart = if (isEdit) isSmartPlaylist else createIsSmartPlaylist
    val selectedConfigIsNavidrome = configs.firstOrNull { it.host == selectedHost }?.isNavidrome ?: false

    LaunchedEffect(retryToken) {
        if (isEdit && isSmartPlaylist && smartRulesJson == null && fetchSmartRules != null) {
            rulesLoading = true
            rulesLoadFailed = false
            val result = fetchSmartRules()
            if (result != null) criteria = SPCriteria.fromJsonString(result) ?: SPCriteria() else rulesLoadFailed = true
            rulesLoading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text(
                    text = if (isEdit) "Edit Playlist" else "New Playlist",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                TextButton(
                    onClick = {
                        // pickedCoverBytes stays null unless the user actually picked a new image
                        // this session — a plain rename/public-toggle save must never touch it.
                        val rulesJson = if (effectiveIsSmart) criteria.toJsonString() else null
                        onConfirm(name.trim(), isPublic, selectedHost, pickedCoverBytes, pickedCoverMimeType, effectiveIsSmart, rulesJson)
                    },
                    // Edit mode has no server picker (you can't move a playlist to another
                    // server), so selectedHost is legitimately blank there — only require it
                    // when creating, where it's actually shown/needed. While editing an existing
                    // smart playlist, block Save until its rules have actually loaded.
                    enabled = name.isNotBlank() && (isEdit || selectedHost.isNotBlank()) &&
                        !(isEdit && effectiveIsSmart && (rulesLoading || rulesLoadFailed))
                ) {
                    Text(if (isEdit) "Save" else "Create")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (!isEdit && configs.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Server", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                configs.forEach { config ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedHost = config.host }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedHost == config.host, onClick = { selectedHost = config.host })
                        Text(config.name.ifBlank { config.host })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Public", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isPublic, onCheckedChange = { isPublic = it })
            }

            if (!isEdit) {
                if (selectedConfigIsNavidrome) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Smart Playlist", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = createIsSmartPlaylist, onCheckedChange = { createIsSmartPlaylist = it })
                    }
                }
                if (createIsSmartPlaylist && selectedConfigIsNavidrome) {
                    SmartPlaylistEditorSection(criteria = criteria, onCriteriaChange = { criteria = it })
                }
            } else if (effectiveIsSmart) {
                when {
                    rulesLoading -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Loading rules…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    rulesLoadFailed -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                "Failed to load rules",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { retryToken++ }) { Text("Retry") }
                        }
                    }
                    else -> SmartPlaylistEditorSection(criteria = criteria, onCriteriaChange = { criteria = it })
                }
            } else if (showArtworkPicker) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val previewModel = pickedCoverUri ?: currentCoverArtUrl
                    Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) {
                        if (previewModel != null) {
                            AsyncImage(
                                model = previewModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        text = if (pickedCoverUri != null) "New cover selected" else "Change Cover",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
