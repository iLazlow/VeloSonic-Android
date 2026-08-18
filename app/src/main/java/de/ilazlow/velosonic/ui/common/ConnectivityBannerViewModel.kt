package de.ilazlow.velosonic.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.datastore.OfflineModeStore
import de.ilazlow.velosonic.data.network.ConnectivityObserver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ConnectivityStatus { OFFLINE_MODE, NO_NETWORK, CONNECTED }

@HiltViewModel
class ConnectivityBannerViewModel @Inject constructor(
    offlineModeStore: OfflineModeStore,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val status: StateFlow<ConnectivityStatus> = combine(
        offlineModeStore.isEnabled,
        connectivityObserver.isOnline
    ) { offlineModeEnabled, isOnline ->
        when {
            offlineModeEnabled -> ConnectivityStatus.OFFLINE_MODE
            !isOnline -> ConnectivityStatus.NO_NETWORK
            else -> ConnectivityStatus.CONNECTED
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectivityStatus.CONNECTED)
}
