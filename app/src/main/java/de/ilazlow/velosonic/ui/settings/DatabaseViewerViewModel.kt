package de.ilazlow.velosonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.db.DatabaseInspectorRepository
import de.ilazlow.velosonic.data.db.TableRow
import de.ilazlow.velosonic.data.db.TableSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DatabaseViewerViewModel @Inject constructor(
    private val repository: DatabaseInspectorRepository
) : ViewModel() {
    private val _tables = MutableStateFlow<List<TableSummary>>(emptyList())
    val tables: StateFlow<List<TableSummary>> = _tables.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        _tables.value = repository.listTables()
        _loading.value = false
    }
}

@HiltViewModel
class DatabaseSettingsViewModel @Inject constructor(
    private val repository: DatabaseInspectorRepository
) : ViewModel() {
    private val _isCleaningUp = MutableStateFlow(false)
    val isCleaningUp: StateFlow<Boolean> = _isCleaningUp.asStateFlow()

    private val _justCleanedUp = MutableStateFlow(false)
    val justCleanedUp: StateFlow<Boolean> = _justCleanedUp.asStateFlow()

    fun cleanUpHistory() = viewModelScope.launch {
        _isCleaningUp.value = true
        repository.cleanUpHistory()
        _isCleaningUp.value = false
        _justCleanedUp.value = true
    }

    fun clearJustCleanedUp() { _justCleanedUp.value = false }
}

private const val PAGE_SIZE = 50

@HiltViewModel
class DatabaseTableViewModel @Inject constructor(
    private val repository: DatabaseInspectorRepository
) : ViewModel() {
    private val _rows = MutableStateFlow<List<TableRow>>(emptyList())
    val rows: StateFlow<List<TableRow>> = _rows.asStateFlow()

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    lateinit var tableName: String
        private set

    fun load(tableName: String) {
        this.tableName = tableName
        _page.value = 0
        loadPage(0)
    }

    fun nextPage() {
        if (!_hasMore.value) return
        loadPage(_page.value + 1)
    }

    fun previousPage() {
        if (_page.value == 0) return
        loadPage(_page.value - 1)
    }

    private fun loadPage(page: Int) = viewModelScope.launch {
        val newRows = repository.getRows(tableName, PAGE_SIZE, page * PAGE_SIZE)
        _rows.value = newRows
        _hasMore.value = newRows.size == PAGE_SIZE
        _page.value = page
    }
}

@HiltViewModel
class DatabaseRowDetailViewModel @Inject constructor(
    private val repository: DatabaseInspectorRepository
) : ViewModel() {
    private val _row = MutableStateFlow<TableRow?>(null)
    val row: StateFlow<TableRow?> = _row.asStateFlow()

    private val _editingEnabled = MutableStateFlow(false)
    val editingEnabled: StateFlow<Boolean> = _editingEnabled.asStateFlow()

    fun setEditingEnabled(enabled: Boolean) { _editingEnabled.value = enabled }

    fun load(table: String, rowId: Long) = viewModelScope.launch {
        _row.value = repository.getRow(table, rowId)
    }

    fun saveColumn(table: String, rowId: Long, column: String, value: String?) = viewModelScope.launch {
        repository.updateRowColumn(table, rowId, column, value)
        _row.value = repository.getRow(table, rowId)
    }
}
