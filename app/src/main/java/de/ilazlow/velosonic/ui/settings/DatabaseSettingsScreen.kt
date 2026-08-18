package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/** Mirrors DatabaseSettingsView.swift's top level: Clean Up History action, Database Viewer nav
 *  row, Backup nav row (Backup's own content is unchanged — see [BackupSettingsScreen] — it's
 *  just reached from here now instead of the Settings root directly). */
@Composable
fun DatabaseSettingsScreen(
    onBack: () -> Unit,
    onNavigateToViewer: () -> Unit,
    onNavigateToBackup: () -> Unit,
    viewModel: DatabaseSettingsViewModel = hiltViewModel()
) {
    val isCleaningUp by viewModel.isCleaningUp.collectAsStateWithLifecycle()
    val justCleanedUp by viewModel.justCleanedUp.collectAsStateWithLifecycle()

    LaunchedEffect(justCleanedUp) {
        if (justCleanedUp) {
            delay(2000)
            viewModel.clearJustCleanedUp()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Database", onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isCleaningUp, onClick = viewModel::cleanUpHistory)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = "Clean Up History", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            when {
                isCleaningUp -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                justCleanedUp -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToViewer)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = "Database Viewer", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToBackup)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = "Backup", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
