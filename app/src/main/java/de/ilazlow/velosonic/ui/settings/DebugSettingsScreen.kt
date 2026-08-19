package de.ilazlow.velosonic.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import java.text.DateFormat
import java.util.Date

/** Mirrors DebugSettingsView.swift: master logging toggle, conditional privacy/memory/radiant
 *  sub-toggles, a current-session tail viewer, and share/copy/clear actions. */
@Composable
fun DebugSettingsScreen(onBack: () -> Unit, onViewFullLog: () -> Unit, viewModel: DebugSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tail by viewModel.tail.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var justCopied by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val filteredLines = remember(tail, searchQuery) {
        if (searchQuery.isBlank()) tail else tail.filter { it.contains(searchQuery, ignoreCase = true) }
    }
    val shareLogLabel = stringResource(id = R.string.settings_debug_share_log)

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(id = R.string.settings_debug_title), onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_debug_enable_logging),
                    subtitle = stringResource(id = R.string.settings_debug_enable_logging_subtitle),
                    checked = settings.loggingEnabled,
                    onCheckedChange = viewModel::setLoggingEnabled
                )
            }
            if (settings.loggingEnabled) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader(stringResource(id = R.string.settings_debug_section_privacy)) }
                item {
                    SettingsSwitchRow(
                        title = stringResource(id = R.string.settings_debug_redact_server_host),
                        checked = settings.redactServerHost,
                        onCheckedChange = viewModel::setRedactServerHost
                    )
                }
                item {
                    SettingsSwitchRow(
                        title = stringResource(id = R.string.settings_debug_redact_user_details),
                        checked = settings.redactUserDetails,
                        onCheckedChange = viewModel::setRedactUserDetails
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item {
                    SettingsSwitchRow(
                        title = stringResource(id = R.string.settings_debug_memory_diagnostics),
                        checked = settings.memoryDiagnosticsEnabled,
                        onCheckedChange = viewModel::setMemoryDiagnosticsEnabled
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item {
                    SettingsSwitchRow(
                        title = stringResource(id = R.string.settings_debug_radiant_lyrics_logging),
                        checked = settings.radiantLyricsLoggingEnabled,
                        onCheckedChange = viewModel::setRadiantLyricsLoggingEnabled
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader(stringResource(id = R.string.settings_debug_section_current_session)) }
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(id = R.string.settings_debug_search_log)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (filteredLines.isEmpty()) {
                            Text(stringResource(id = R.string.settings_debug_no_log_entries), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            filteredLines.takeLast(80).forEach { line ->
                                Text(text = line, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onViewFullLog).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(id = R.string.settings_debug_view_full_log), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { /* tail is already live-updating; no manual refresh action needed */ }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(stringResource(id = R.string.settings_debug_refresh))
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(filteredLines.joinToString("\n")))
                            justCopied = true
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(if (justCopied) stringResource(id = R.string.settings_debug_copied) else stringResource(id = R.string.settings_debug_copy_log))
                        }
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader(stringResource(id = R.string.settings_debug_section_share)) }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val file = viewModel.currentSessionFile()
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, shareLogLabel))
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = shareLogLabel, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(id = R.string.settings_debug_log_file_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(viewModel.currentSessionFile().name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(id = R.string.settings_debug_clear_logs), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                val otherFiles = viewModel.allLogFiles().filter { it.name != viewModel.currentSessionFile().name }
                if (otherFiles.isNotEmpty()) {
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    item { SettingsSectionHeader(stringResource(id = R.string.settings_debug_section_all_logs)) }
                    items(otherFiles) { file ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    DateFormat.getDateTimeInstance().format(Date(file.lastModified())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(id = R.string.settings_debug_clear_logs_dialog_title)) },
            text = { Text(stringResource(id = R.string.settings_debug_clear_logs_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearLogs(); confirmClear = false }) {
                    Text(stringResource(id = R.string.settings_debug_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(id = R.string.settings_debug_cancel)) } }
        )
    }
}
