package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.ui.login.LoginViewModel

/** Same form/fields as LoginScreen (reuses [LoginViewModel] as-is), just in a compact sheet
 *  instead of a full-screen flow, for adding an additional server from Settings — or, when
 *  [existingConfig] is passed, for editing one already saved. Changing the host in edit mode
 *  migrates every host-scoped local record via [de.ilazlow.velosonic.data.sync.ServerMigrationManager]
 *  (confirmed first, mirroring iOS's migration dialog) rather than creating a second server. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddServerSheet(
    onDismiss: () -> Unit,
    existingConfig: ServerConfigEntity? = null,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(existingConfig) {
        existingConfig?.let(viewModel::loadForEdit)
    }

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = if (uiState.isEditMode) "Edit Server" else "Add Server", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.serverName,
                onValueChange = viewModel::onServerNameChange,
                label = { Text(stringResource(id = R.string.login_server_name_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::onHostChange,
                label = { Text(stringResource(id = R.string.login_server_url)) },
                singleLine = true,
                enabled = !uiState.isMigrating,
                supportingText = if (uiState.isEditMode) {
                    { Text("Changing this migrates your local library data to the new address.") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(id = R.string.login_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(id = R.string.login_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            uiState.errorMessageRes?.let { errorRes ->
                Text(text = stringResource(id = errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.isMigrating) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Migrating local data…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { uiState.migrationProgress }, modifier = Modifier.fillMaxWidth())
                }
            }
            Button(onClick = viewModel::connect, enabled = uiState.canSubmit, modifier = Modifier.fillMaxWidth()) {
                if (uiState.isConnecting || uiState.isConnected) {
                    LoadingIndicator(modifier = Modifier.padding(vertical = 2.dp))
                } else {
                    Text(if (uiState.isEditMode) "Save" else stringResource(id = R.string.login_connect))
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }

    if (uiState.pendingHostChangeConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHostChangeConfirm,
            title = { Text("Change Server Address?") },
            text = { Text("This migrates your library, downloads, and cached data from \"${uiState.originalHost}\" to \"${uiState.host}\". This can't be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAndConnect) { Text("Migrate") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissHostChangeConfirm) { Text("Cancel") }
            }
        )
    }
}
