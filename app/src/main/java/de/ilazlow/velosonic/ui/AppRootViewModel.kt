package de.ilazlow.velosonic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.db.SyncMetadataDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Mirrors MainView.swift's top-level branch: no server -> Login, server but initial sync not
 *  complete yet -> Syncing (SyncView), server + sync complete -> Ready (the main app shell). */
sealed interface AppRoute {
    data object Loading : AppRoute
    data object Login : AppRoute
    data class Syncing(val host: String) : AppRoute
    data class Ready(val host: String) : AppRoute
}

@HiltViewModel
class AppRootViewModel @Inject constructor(
    serverRepository: ServerRepository,
    syncMetadataDao: SyncMetadataDao
) : ViewModel() {

    /** Waits for EVERY configured server's initial sync, not just the first one (alphabetically
     *  first by name — in practice, whichever server happens to sort first) — gating on a single
     *  "primary" server let the app through to [AppRoute.Ready] the moment that one server
     *  finished, even while a second server's own initial sync was still running in the
     *  background: confirmed live, the app showed only the already-synced server's library while
     *  the other was silently still fetching, looking exactly like data loss. [AppRoute.Syncing]
     *  now reports whichever server isn't done yet, so the sync screen has something concrete to
     *  show instead of just sitting on the first server's completed state. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val route: StateFlow<AppRoute> = serverRepository.observeServers()
        .flatMapLatest { servers ->
            if (servers.isEmpty()) {
                flowOf(AppRoute.Login)
            } else {
                combine(servers.map { syncMetadataDao.observeForHost(it.host) }) { metas ->
                    val pendingIndex = metas.indexOfFirst { it?.isInitialSyncComplete != true }
                    if (pendingIndex == -1) {
                        AppRoute.Ready(servers.first().host)
                    } else {
                        AppRoute.Syncing(servers[pendingIndex].host)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppRoute.Loading)
}
