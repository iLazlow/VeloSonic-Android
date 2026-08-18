package de.ilazlow.velosonic.ui.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.sync.SyncEngine
import de.ilazlow.velosonic.data.sync.SyncMode
import de.ilazlow.velosonic.data.sync.SyncNowWorker
import de.ilazlow.velosonic.data.sync.SyncState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Mostly observes SyncEngine's progress — the first attempt was already kicked off by
 * ServerRepository.addServer right after login (unlike the iOS SyncView, which calls
 * performInitialSync from its own onAppear) — but owns [retry] for when that attempt fails
 * (e.g. a transient network blip), since nothing else will ever try again on its own. Both routes
 * run through [SyncNowWorker] (a foreground-service WorkManager job), not a plain coroutine, so
 * the sync survives the app being backgrounded instead of getting silently killed mid-fetch.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncEngine: SyncEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val state: StateFlow<SyncState> = syncEngine.state

    fun retry(host: String) {
        SyncNowWorker.enqueue(context, host, SyncMode.INITIAL)
    }
}
