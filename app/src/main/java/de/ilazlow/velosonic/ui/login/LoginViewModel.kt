package de.ilazlow.velosonic.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.AddServerError
import de.ilazlow.velosonic.data.AddServerResult
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.security.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val serverName: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    /** True when prefilled via [LoginViewModel.loadForEdit]. */
    val isEditMode: Boolean = false,
    /** The host this edit started from — compared against [host] on submit to decide whether a
     *  [de.ilazlow.velosonic.data.sync.ServerMigrationManager] migration is needed. Irrelevant
     *  outside edit mode. */
    val originalHost: String = "",
    /** True once the user has submitted a changed host and needs to confirm the migration before
     *  it actually runs — mirrors iOS's `showMigrationConfirm` dialog. */
    val pendingHostChangeConfirm: Boolean = false,
    val isMigrating: Boolean = false,
    val migrationProgress: Float = 0f,
    @StringRes val errorMessageRes: Int? = null,
    /** Step-by-step trace of the last connection attempt — mirrors iOS's inline login debug
     *  console. Reset at the start of every [LoginViewModel.confirmAndConnect] call, empty until
     *  the first attempt, so the toggle to reveal it only appears once there's something to see. */
    val debugLog: List<String> = emptyList()
) {
    val canSubmit: Boolean
        get() = host.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isConnecting && !isConnected && !isMigrating
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val credentialStore: CredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onHostChange(value: String) = _uiState.update { it.copy(host = value, errorMessageRes = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorMessageRes = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessageRes = null) }
    fun onServerNameChange(value: String) = _uiState.update { it.copy(serverName = value) }

    /** Prefills the form from an existing server for editing — mirrors iOS's
     *  `LoginView(existingConfig:)`. Submitting re-pings/re-authenticates and upserts the same
     *  host row in place (see [ServerRepository.addServer]'s upsert-by-host behavior), so this
     *  doubles as both "fix my saved password" and "rename this server" without any separate
     *  update path. */
    fun loadForEdit(config: ServerConfigEntity) {
        _uiState.value = LoginUiState(
            host = config.host,
            username = config.username,
            password = credentialStore.loadPassword(config.host, config.username).orEmpty(),
            serverName = config.name,
            isEditMode = true,
            originalHost = config.host
        )
    }

    /** In edit mode with a changed host, this only raises the migration confirmation — actually
     *  committing (host-changed or not) happens in [confirmAndConnect]. Every other case
     *  (add-server, or an edit whose host didn't change) skips the confirmation and commits
     *  immediately, matching iOS's `performLogin` — the confirmation dialog there only ever
     *  appears when `existing.host != hostUrl`. */
    fun connect() {
        val state = _uiState.value
        if (!state.canSubmit) return

        if (state.isEditMode && state.host.trim().trimEnd('/') != state.originalHost) {
            _uiState.update { it.copy(pendingHostChangeConfirm = true) }
            return
        }
        confirmAndConnect()
    }

    fun dismissHostChangeConfirm() = _uiState.update { it.copy(pendingHostChangeConfirm = false) }

    fun confirmAndConnect() {
        val state = _uiState.value
        _uiState.update {
            it.copy(pendingHostChangeConfirm = false, isConnecting = true, errorMessageRes = null, debugLog = emptyList())
        }
        val appendLog: (String) -> Unit = { line -> _uiState.update { it.copy(debugLog = it.debugLog + line) } }
        viewModelScope.launch {
            val result = if (state.isEditMode) {
                serverRepository.editServer(
                    originalHost = state.originalHost,
                    hostInput = state.host,
                    username = state.username,
                    password = state.password,
                    name = state.serverName,
                    onMigrationProgress = { progress ->
                        _uiState.update { it.copy(isMigrating = true, migrationProgress = progress) }
                    },
                    onLog = appendLog
                )
            } else {
                serverRepository.addServer(
                    hostInput = state.host,
                    username = state.username,
                    password = state.password,
                    name = state.serverName,
                    onLog = appendLog
                )
            }
            when (result) {
                is AddServerResult.Success -> _uiState.update {
                    it.copy(isConnecting = false, isMigrating = false, isConnected = true)
                }
                is AddServerResult.Failure -> _uiState.update {
                    it.copy(isConnecting = false, isMigrating = false, errorMessageRes = result.error.toStringRes())
                }
            }
        }
    }

    @StringRes
    private fun AddServerError.toStringRes(): Int = when (this) {
        AddServerError.INVALID_HOST -> R.string.login_error_invalid_host
        AddServerError.PING_FAILED -> R.string.login_error_ping_failed
    }
}
