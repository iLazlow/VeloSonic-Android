package de.ilazlow.velosonic.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.backup.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data class ExportReady(val file: File) : BackupUiState
    data object RestoreStaged : BackupUiState
    data object Error : BackupUiState
}

@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {
    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun exportBackup() {
        _state.value = BackupUiState.Working
        viewModelScope.launch {
            _state.value = try {
                BackupUiState.ExportReady(backupRepository.createBackupFile())
            } catch (e: Exception) {
                BackupUiState.Error
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        _state.value = BackupUiState.Working
        viewModelScope.launch {
            _state.value = if (backupRepository.stageRestore(uri)) BackupUiState.RestoreStaged else BackupUiState.Error
        }
    }

    fun reset() {
        _state.value = BackupUiState.Idle
    }
}
