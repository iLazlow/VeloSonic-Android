package de.ilazlow.velosonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.datastore.DebugSettings
import de.ilazlow.velosonic.data.datastore.DebugSettingsStore
import de.ilazlow.velosonic.data.debug.LogManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    private val debugSettingsStore: DebugSettingsStore,
    val logManager: LogManager
) : ViewModel() {
    val settings: StateFlow<DebugSettings> = debugSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugSettings())

    val tail: StateFlow<List<String>> = logManager.tail

    fun setLoggingEnabled(enabled: Boolean) = viewModelScope.launch { debugSettingsStore.setLoggingEnabled(enabled) }
    fun setRedactServerHost(enabled: Boolean) = viewModelScope.launch { debugSettingsStore.setRedactServerHost(enabled) }
    fun setRedactUserDetails(enabled: Boolean) = viewModelScope.launch { debugSettingsStore.setRedactUserDetails(enabled) }
    fun setMemoryDiagnosticsEnabled(enabled: Boolean) = viewModelScope.launch { debugSettingsStore.setMemoryDiagnosticsEnabled(enabled) }
    fun setRadiantLyricsLoggingEnabled(enabled: Boolean) = viewModelScope.launch { debugSettingsStore.setRadiantLyricsLoggingEnabled(enabled) }

    fun currentSessionFile(): File = logManager.currentSessionFile()
    fun allLogFiles(): List<File> = logManager.allLogFiles()
    fun clearLogs() = logManager.clearAll()
}
