package de.ilazlow.velosonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.lyrics.LyricLine
import de.ilazlow.velosonic.data.lyrics.RadiantLyricsCacheStore
import de.ilazlow.velosonic.data.lyrics.RadiantLyricsCacheSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadiantLyricsCacheListViewModel @Inject constructor(
    private val cacheStore: RadiantLyricsCacheStore
) : ViewModel() {
    private val _entries = MutableStateFlow<List<RadiantLyricsCacheSummary>>(emptyList())
    val entries: StateFlow<List<RadiantLyricsCacheSummary>> = _entries.asStateFlow()

    init { reload() }

    fun reload() = viewModelScope.launch { _entries.value = cacheStore.listEntries() }

    fun delete(id: String) = viewModelScope.launch {
        cacheStore.deleteEntry(id)
        reload()
    }

    fun clearAll() = viewModelScope.launch {
        cacheStore.clearAll()
        reload()
    }
}

@HiltViewModel
class RadiantLyricsCacheDetailViewModel @Inject constructor(
    private val cacheStore: RadiantLyricsCacheStore
) : ViewModel() {
    private val _lines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lines: StateFlow<List<LyricLine>> = _lines.asStateFlow()

    private val _rawJson = MutableStateFlow("")
    val rawJson: StateFlow<String> = _rawJson.asStateFlow()

    fun load(id: String) = viewModelScope.launch {
        _lines.value = cacheStore.cachedLines(id).orEmpty()
        _rawJson.value = cacheStore.rawJson(id).orEmpty()
    }

    fun delete(id: String) = viewModelScope.launch {
        cacheStore.deleteEntry(id)
    }
}
