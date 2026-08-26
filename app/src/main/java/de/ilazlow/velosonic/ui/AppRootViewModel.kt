package de.ilazlow.velosonic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.ServerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Mirrors MainView.swift's top-level branch, minus the blocking Syncing step it never actually
 *  had a real equivalent of anyway — this used to gate [Ready] behind every configured server's
 *  [de.ilazlow.velosonic.data.db.SyncMetadataEntity.isInitialSyncComplete], showing a full-screen
 *  blocking sync spinner (SyncScreen) in between. Removed: the app now drops straight into
 *  [AppShell] the moment at least one server is configured, and [de.ilazlow.velosonic.data.sync.SyncEngine]
 *  writes the freshly-synced library into Room incrementally as it fetches (see
 *  [de.ilazlow.velosonic.data.sync.SyncEngine]'s initial-sync path) rather than in one atomic
 *  commit at the very end — so the screens the user lands on fill in progressively instead of
 *  sitting empty for the whole sync. [de.ilazlow.velosonic.ui.common.LibrarySyncStatusBanner]
 *  (mounted globally in [AppShell]) is what now tells the user a sync is still in progress,
 *  in place of the old full-screen blocker. */
sealed interface AppRoute {
    data object Loading : AppRoute
    data object Login : AppRoute
    data object Ready : AppRoute
}

@HiltViewModel
class AppRootViewModel @Inject constructor(
    serverRepository: ServerRepository
) : ViewModel() {
    val route: StateFlow<AppRoute> = serverRepository.observeServers()
        .map { servers -> if (servers.isEmpty()) AppRoute.Login else AppRoute.Ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppRoute.Loading)
}
