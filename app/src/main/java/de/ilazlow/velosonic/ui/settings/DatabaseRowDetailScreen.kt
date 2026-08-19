package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R

/** Mirrors iOS's `DatabaseRowDetailView` — every column/value for one row, gated behind an
 *  "enable editing" toggle before any field becomes writable (raw `UPDATE` on save). */
@Composable
fun DatabaseRowDetailScreen(
    tableName: String,
    rowId: Long,
    onBack: () -> Unit,
    viewModel: DatabaseRowDetailViewModel = hiltViewModel()
) {
    val row by viewModel.row.collectAsStateWithLifecycle()
    val editingEnabled by viewModel.editingEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(tableName, rowId) { viewModel.load(tableName, rowId) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(id = R.string.settings_database_row_title, rowId), onBack)
        SettingsSwitchRow(
            title = stringResource(id = R.string.settings_database_enable_editing),
            subtitle = stringResource(id = R.string.settings_database_enable_editing_subtitle),
            checked = editingEnabled,
            onCheckedChange = viewModel::setEditingEnabled
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        val currentRow = row
        if (currentRow == null) {
            Text(stringResource(id = R.string.settings_database_loading), modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(currentRow.values.entries.toList(), key = { it.key }) { (column, value) ->
                    RowFieldEditor(
                        column = column,
                        value = value,
                        editable = editingEnabled,
                        onSave = { newValue -> viewModel.saveColumn(tableName, rowId, column, newValue) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun RowFieldEditor(column: String, value: String?, editable: Boolean, onSave: (String?) -> Unit) {
    var text by remember(column, value) { mutableStateOf(value ?: "") }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text = column, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (editable) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            if (text != (value ?: "")) {
                androidx.compose.material3.TextButton(
                    onClick = { onSave(text.ifEmpty { null }) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(id = R.string.settings_database_save))
                }
            }
        } else {
            Text(
                text = value ?: stringResource(id = R.string.settings_database_null_value),
                style = MaterialTheme.typography.bodyMedium,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
