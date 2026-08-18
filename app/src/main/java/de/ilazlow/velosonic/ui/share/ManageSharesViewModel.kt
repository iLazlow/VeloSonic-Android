package de.ilazlow.velosonic.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.share.ShareItem
import de.ilazlow.velosonic.data.share.ShareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageSharesViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
    private val serverConfigDao: ServerConfigDao
) : ViewModel() {
    private val _shares = MutableStateFlow<List<ShareItem>>(emptyList())
    val shares: StateFlow<List<ShareItem>> = _shares.asStateFlow()

    private val _serverNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val serverNames: StateFlow<Map<String, String>> = _serverNames.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _isLoading.value = true
        _serverNames.value = serverConfigDao.getAll().associate { it.host to it.name.ifBlank { it.host } }
        _shares.value = shareRepository.listShares()
        _isLoading.value = false
    }

    fun delete(item: ShareItem) = viewModelScope.launch {
        if (shareRepository.deleteShare(item.serverHost, item.share.id)) {
            _shares.value = _shares.value.filterNot { it.serverHost == item.serverHost && it.share.id == item.share.id }
        }
    }
}
