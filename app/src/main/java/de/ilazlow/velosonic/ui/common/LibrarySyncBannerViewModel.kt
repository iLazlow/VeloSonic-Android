package de.ilazlow.velosonic.ui.common

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.sync.SyncEngine
import de.ilazlow.velosonic.data.sync.SyncState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LibrarySyncBannerViewModel @Inject constructor(
    syncEngine: SyncEngine
) : ViewModel() {
    val state: StateFlow<SyncState> = syncEngine.state
}
