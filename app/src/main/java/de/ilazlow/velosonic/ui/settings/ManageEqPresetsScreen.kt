package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.datastore.EqPreset

/**
 * Scoped-down port of ManagePresetsView.swift: tapping a row applies that preset (same action as
 * the preset dropdown on [EqSettingsScreen], with a checkmark showing the current selection).
 * Built-in presets are read-only here (star icon, no menu) — iOS lets you edit a built-in
 * preset's gains in place via its shared band-slider-grid edit sheet, but that requires
 * persisting a full built-in+custom merged preset list (iOS's flat UserDefaults array does this
 * implicitly); this port keeps built-ins immutable and only lets custom presets be renamed or
 * deleted, which covers the actual use case (saving/managing your own presets) without that
 * storage-model change.
 */
@Composable
fun ManageEqPresetsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val eq by viewModel.eqSettings.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<EqPreset?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<EqPreset?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Manage Presets", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(eq.allPresets, key = { it.id }) { preset ->
                PresetRow(
                    preset = preset,
                    selected = preset.id == eq.selectedPresetId,
                    onApply = { viewModel.applyEqPreset(preset) },
                    onRename = { renameText = preset.name; renameTarget = preset },
                    onDelete = { deleteTarget = preset }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }

    renameTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Preset") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        viewModel.updateEqPreset(preset.copy(name = renameText.trim()))
                        renameTarget = null
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${preset.name}\"?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteEqPreset(preset); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PresetRow(preset: EqPreset, selected: Boolean, onApply: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = preset.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        if (preset.isBuiltIn) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp).size(16.dp)
            )
        } else {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}
