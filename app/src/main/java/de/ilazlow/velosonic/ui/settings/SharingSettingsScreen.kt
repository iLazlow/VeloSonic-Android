package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.datastore.ShareLinkType

/** Mirrors SharingSettingsView.swift — a single picker for what kind of link "Share" creates by
 *  default, distinct from [de.ilazlow.velosonic.ui.share.ManageSharesScreen] (the list of
 *  existing shares, reached from Manage Servers). */
@Composable
fun SharingSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val sharing by viewModel.sharingSettings.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Sharing", onBack)
        SettingsSectionHeader("Default Link Type")
        SettingsPickerRow(
            label = "Link Type",
            valueLabel = sharing.linkType.label,
            options = ShareLinkType.entries,
            optionLabel = { it.label },
            onSelect = viewModel::setShareLinkType
        )
        Text(
            text = if (sharing.linkType == ShareLinkType.PUBLIC_LINK) {
                "Anyone with the link can play this in a browser, no VeloSonic account needed."
            } else {
                "Opens directly in VeloSonic on a recipient's device if they have it installed and this server configured."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}
