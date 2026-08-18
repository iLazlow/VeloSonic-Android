package de.ilazlow.velosonic.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.ui.navigation.SettingsApiRoute
import de.ilazlow.velosonic.ui.navigation.SettingsAppearanceRoute
import de.ilazlow.velosonic.ui.navigation.SettingsAudioAnalysisRoute
import de.ilazlow.velosonic.ui.navigation.SettingsDatabaseRoute
import de.ilazlow.velosonic.ui.navigation.SettingsDebugRoute
import de.ilazlow.velosonic.ui.navigation.SettingsLyricsRoute
import de.ilazlow.velosonic.ui.navigation.SettingsPlaybackRoute
import de.ilazlow.velosonic.ui.navigation.SettingsServersRoute
import de.ilazlow.velosonic.ui.navigation.SettingsSharingRoute
import de.ilazlow.velosonic.ui.navigation.SettingsStorageRoute

/**
 * Mirrors SettingsView.swift's shape exactly: an Offline Mode toggle at the top, the nav-hub list
 * (Manage Servers → Playback → Lyrics → Storage → Appearance → Audio Analysis[Beta] → Sharing →
 * Database → API), a Language row, a Support section (GitHub/Discord links + Debug), an Account
 * section with Logout, and a centered app-identity footer. Watch is deliberately omitted — no
 * Wear OS module exists yet, so a row leading to it would have nothing to control.
 */
@Composable
fun SettingsScreen(
    onNavigate: (Any) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsStateWithLifecycle()
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Filled.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text(text = "Offline Mode", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = offlineModeEnabled, onCheckedChange = viewModel::setOfflineModeEnabled)
            }
        }
        item {
            Text(
                text = "When enabled, VeloSonic won't attempt any network requests — browse and play only what's already downloaded or cached.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { NavHubRow(icon = Icons.Filled.Dns, label = "Manage Servers", onClick = { onNavigate(SettingsServersRoute) }) }
        item { NavHubRow(icon = Icons.Filled.PlayCircle, label = "Playback", onClick = { onNavigate(SettingsPlaybackRoute) }) }
        item { NavHubRow(icon = Icons.Filled.QuestionAnswer, label = "Lyrics", onClick = { onNavigate(SettingsLyricsRoute) }) }
        item { NavHubRow(icon = Icons.Filled.Storage, label = "Storage", onClick = { onNavigate(SettingsStorageRoute) }) }
        item { NavHubRow(icon = Icons.Filled.Palette, label = "Appearance", onClick = { onNavigate(SettingsAppearanceRoute) }) }
        item { NavHubRow(icon = Icons.Filled.QueryStats, label = "Audio Analysis", onClick = { onNavigate(SettingsAudioAnalysisRoute) }, trailingBadge = true) }
        item { NavHubRow(icon = Icons.Filled.Share, label = "Sharing", onClick = { onNavigate(SettingsSharingRoute) }) }
        item { NavHubRow(icon = Icons.Filled.Dataset, label = "Database", onClick = { onNavigate(SettingsDatabaseRoute) }) }
        item { NavHubRow(icon = Icons.Filled.Key, label = "API", onClick = { onNavigate(SettingsApiRoute) }) }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
        item { SettingsSectionHeader("Language") }
        item {
            NavHubRow(
                icon = Icons.Filled.Language,
                label = "Change App Language",
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                trailingIcon = Icons.Filled.OpenInNew
            )
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
        item { SettingsSectionHeader("Support") }
        item {
            NavHubRow(
                icon = Icons.Filled.Code,
                label = "GitHub",
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iLazlow/VeloSonic-Android"))) },
                trailingIcon = Icons.Filled.OpenInNew
            )
        }
        item {
            NavHubRow(
                icon = Icons.Filled.Forum,
                label = "Discord",
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/KQjAxf8X9G"))) },
                trailingIcon = Icons.Filled.OpenInNew
            )
        }
        item { NavHubRow(icon = Icons.Filled.BugReport, label = "Debug", onClick = { onNavigate(SettingsDebugRoute) }) }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Log Out",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clickable { showLogoutConfirm = true }
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResourceAppName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (versionName != null) {
                    Text(text = "Version $versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log Out") },
            text = { Text("This removes every configured server, including local databases, caches, and downloads, from this device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(); showLogoutConfirm = false }) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun stringResourceAppName(): String = androidx.compose.ui.res.stringResource(id = R.string.app_name)

