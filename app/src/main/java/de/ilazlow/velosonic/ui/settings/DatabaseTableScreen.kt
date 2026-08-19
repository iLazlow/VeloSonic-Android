package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R

/** Mirrors iOS's `DatabaseTableView` — a paginated (50/page) raw row browser for one table. */
@Composable
fun DatabaseTableScreen(
    tableName: String,
    onBack: () -> Unit,
    onRowClick: (Long) -> Unit,
    viewModel: DatabaseTableViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val columns = rows.firstOrNull()?.values?.keys?.toList().orEmpty()

    LaunchedEffect(tableName) { viewModel.load(tableName) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(tableName, onBack)
        val scrollState = rememberScrollState()
        val nullValueLabel = stringResource(id = R.string.settings_database_null_value)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            item {
                Row(modifier = Modifier.horizontalScroll(scrollState).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(stringResource(id = R.string.settings_database_column_rowid), modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    columns.forEach { col ->
                        Text(col, modifier = Modifier.width(140.dp), style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
            item { HorizontalDivider() }
            items(rows, key = { it.rowId }) { row ->
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .clickable { onRowClick(row.rowId) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("${row.rowId}", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
                    columns.forEach { col ->
                        Text(
                            row.values[col] ?: nullValueLabel,
                            modifier = Modifier.width(140.dp),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = viewModel::previousPage, enabled = page > 0) { Text(stringResource(id = R.string.settings_database_table_previous)) }
            Text(stringResource(id = R.string.settings_database_table_page_label, page + 1), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = viewModel::nextPage, enabled = hasMore) { Text(stringResource(id = R.string.settings_database_table_next)) }
        }
    }
}
