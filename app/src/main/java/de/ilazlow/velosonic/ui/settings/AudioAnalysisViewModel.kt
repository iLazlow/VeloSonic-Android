package de.ilazlow.velosonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.analysis.AnalysisCounts
import de.ilazlow.velosonic.analysis.AudioAnalysisRepository
import de.ilazlow.velosonic.data.datastore.AudioAnalysisSettings
import de.ilazlow.velosonic.data.datastore.AudioAnalysisSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioAnalysisViewModel @Inject constructor(
    private val repository: AudioAnalysisRepository,
    private val settingsStore: AudioAnalysisSettingsStore
) : ViewModel() {
    val settings: StateFlow<AudioAnalysisSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioAnalysisSettings())

    val isRunning: StateFlow<Boolean> = repository.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val progress: StateFlow<Pair<Int, Int>> = repository.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    private val _counts = MutableStateFlow(AnalysisCounts())
    val counts: StateFlow<AnalysisCounts> = _counts.asStateFlow()

    init {
        refreshCounts()
    }

    fun refreshCounts() = viewModelScope.launch { _counts.value = repository.counts() }

    fun startFullScan() = repository.start(scanSkippedOnly = false)

    fun startSkippedScan() = repository.start(scanSkippedOnly = true)

    fun stop() = repository.stop()

    fun clearSkippedQueue() = viewModelScope.launch {
        repository.clearSkippedQueue()
        refreshCounts()
    }

    fun deleteAllAnalysisData() = viewModelScope.launch {
        repository.deleteAllAnalysisData()
        refreshCounts()
    }

    fun setAnalyzeNonDownloaded(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setAnalyzeNonDownloaded(enabled)
    }

    fun setConcurrentSongs(count: Int) = viewModelScope.launch {
        settingsStore.setConcurrentSongs(count)
    }
}
