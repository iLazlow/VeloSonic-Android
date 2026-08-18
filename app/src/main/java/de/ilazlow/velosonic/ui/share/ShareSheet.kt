package de.ilazlow.velosonic.ui.share

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.ui.settings.SettingsSwitchRow

/**
 * Scoped-down port of ShareSheet.swift: always creates a real server-side share (iOS's "App
 * Link" mode — a locally-built `velosonic://host/type/id` URL instead of the server's own share
 * URL — isn't offered as a separate choice here; the [de.ilazlow.velosonic] deep-link handler
 * still opens `velosonic://` links directly in-app when tapped from elsewhere, this sheet just
 * doesn't hand out that URL form itself, only the real public one). No expiry date-picker —
 * iOS's is a full custom date; a fixed preset list (Never/1/7/30 days) covers the same intent
 * with far less UI for a rarely-changed value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    target: ShareTarget,
    onDismiss: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var description by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf(ShareExpiryOption.NEVER) }
    var downloadAllowed by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    LaunchedEffect(state) {
        val created = state as? ShareUiState.Created ?: return@LaunchedEffect
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, created.url)
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
        viewModel.reset()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = "Share \"${target.title}\"",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )

            Text(text = "Expires", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ShareExpiryOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = expiry == option,
                        onClick = { expiry = option },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ShareExpiryOption.entries.size)
                    ) {
                        Text(option.label)
                    }
                }
            }

            SettingsSwitchRow(
                title = "Allow Downloads",
                checked = downloadAllowed,
                onCheckedChange = { downloadAllowed = it }
            )

            if (state is ShareUiState.Error) {
                Text(
                    text = "Couldn't create the share link. Check your connection and try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp)) {
                Button(
                    onClick = { viewModel.createShare(target, description, expiry, downloadAllowed) },
                    enabled = state !is ShareUiState.Creating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state is ShareUiState.Creating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create Share Link")
                    }
                }
            }
        }
    }
}
