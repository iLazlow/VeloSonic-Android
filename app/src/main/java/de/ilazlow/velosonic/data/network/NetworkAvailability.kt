package de.ilazlow.velosonic.data.network

import de.ilazlow.velosonic.data.datastore.OfflineModeStore
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single shared source of "can this screen actually reach the network right now" — Offline Mode
 * off AND real connectivity present. Mirrors iOS's `NetworkMonitor.isConnected`, which folds
 * `OfflineModeSettings.isEnabled` into the same signal rather than treating them as two separate
 * checks call sites have to combine themselves (see `NetworkMonitor.applyOfflineMode`). Consumed
 * by every screen that needs to gray out/disable actions requiring a live request — track rows
 * for non-cached songs, Home's live section refresh, etc.
 */
@Singleton
class NetworkAvailability @Inject constructor(
    offlineModeStore: OfflineModeStore,
    connectivityObserver: ConnectivityObserver,
    @ApplicationScope appScope: CoroutineScope
) {
    val canReachNetwork: StateFlow<Boolean> = combine(
        offlineModeStore.isEnabled,
        connectivityObserver.isOnline
    ) { offlineModeEnabled, isOnline -> !offlineModeEnabled && isOnline }
        .stateIn(appScope, SharingStarted.Eagerly, true)
}
