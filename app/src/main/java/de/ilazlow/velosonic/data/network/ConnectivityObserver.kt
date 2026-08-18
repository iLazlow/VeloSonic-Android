package de.ilazlow.velosonic.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps [ConnectivityManager] as a live [StateFlow] — mirrors iOS's `NetworkMonitor`
 *  (`NWPathMonitor`-backed). Backs the Home screen's connectivity banner. */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope appScope: CoroutineScope
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    val isOnline: StateFlow<Boolean> = callbackFlow {
        fun hasInternet(network: Network): Boolean {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(hasInternet(network))
            }

            override fun onLost(network: Network) {
                trySend(connectivityManager.activeNetwork?.let(::hasInternet) ?: false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(hasInternet(network))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        trySend(connectivityManager.activeNetwork?.let(::hasInternet) ?: false)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.stateIn(appScope, SharingStarted.WhileSubscribed(5_000), true)
}
