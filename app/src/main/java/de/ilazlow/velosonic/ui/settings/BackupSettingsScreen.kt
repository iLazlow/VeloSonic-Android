package de.ilazlow.velosonic.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Simplified port of DatabaseBackupView.swift — see [de.ilazlow.velosonic.data.backup.BackupRepository]'s
 * doc comment for what's deliberately not ported (iCloud sync, encrypted Keychain sidecar).
 * Export hands the zip to Android's native share sheet exactly like iOS's `ActivityShareSheet`;
 * restore uses the system document picker (Storage Access Framework) rather than iOS's
 * `.fileImporter`, the idiomatic Android equivalent.
 */
@Composable
fun BackupSettingsScreen(onBack: () -> Unit, viewModel: BackupSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreBackup(it) }
    }

    LaunchedEffect(state) {
        val ready = state as? BackupUiState.ExportReady ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
        viewModel.reset()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Backup", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSectionHeader("Export") }
            item {
                Text(
                    text = "Saves your library database and app settings to a file you can share or store elsewhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item {
                Button(
                    onClick = viewModel::exportBackup,
                    enabled = state !is BackupUiState.Working,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) { Text("Export Backup") }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
            item { SettingsSectionHeader("Restore") }
            item {
                Text(
                    text = "Restoring replaces your current library database and settings. Server passwords aren't included — you may need to log in again afterward.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item {
                OutlinedButton(
                    onClick = { openDocumentLauncher.launch(arrayOf("application/zip")) },
                    enabled = state !is BackupUiState.Working,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) { Text("Restore from Backup") }
            }
            if (state is BackupUiState.Error) {
                item {
                    Text(
                        text = "Something went wrong. Make sure you picked a valid VeloSonic backup file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (state is BackupUiState.RestoreStaged) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restart Required") },
            text = { Text("Close and reopen the app to finish restoring your backup.") },
            confirmButton = { TextButton(onClick = viewModel::reset) { Text("OK") } }
        )
    }
}
