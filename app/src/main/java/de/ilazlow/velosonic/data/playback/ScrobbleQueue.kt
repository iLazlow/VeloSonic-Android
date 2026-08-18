package de.ilazlow.velosonic.data.playback

import de.ilazlow.velosonic.data.datastore.PendingScrobble
import de.ilazlow.velosonic.data.datastore.ScrobbleQueueStore
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.NetworkAvailability
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val FALLBACK_FLUSH_INTERVAL_MS = 5 * 60 * 1000L

/**
 * Mirrors iOS's `ScrobbleQueue` — a scrobble that fails to send live (offline mode, or a
 * transient failure) is kept in [ScrobbleQueueStore] rather than just lost, and retried once the
 * network is actually reachable again. Only wraps [PlaybackSubsonicClient.scrobble] (the call
 * that actually records a play in Navidrome's history / submits to Last.fm) — [reportPlayback]'s
 * live position pings are ephemeral and, same as iOS, aren't worth queuing.
 */
@Singleton
class ScrobbleQueue @Inject constructor(
    private val store: ScrobbleQueueStore,
    private val subsonicClient: PlaybackSubsonicClient,
    networkAvailability: NetworkAvailability,
    @ApplicationScope appScope: CoroutineScope
) {
    @Volatile private var isFlushing = false

    init {
        // Flush the moment the network becomes reachable again — Offline Mode switched off, or
        // real connectivity restored — mirrors iOS's app-foreground flush trigger.
        appScope.launch {
            networkAvailability.canReachNetwork.drop(1).distinctUntilChanged().filter { it }.collect { flushQueue() }
        }
        // Fallback periodic flush while the process stays alive, in case that transition is
        // somehow missed — mirrors iOS's 5-minute timer.
        appScope.launch {
            while (true) {
                delay(FALLBACK_FLUSH_INTERVAL_MS)
                flushQueue()
            }
        }
    }

    suspend fun scrobbleOrQueue(config: ServerConfigEntity, trackId: String, submission: Boolean) {
        val now = System.currentTimeMillis()
        val ok = subsonicClient.scrobble(config, trackId, submission, time = now)
        if (!ok) {
            store.enqueue(PendingScrobble(trackId = trackId, serverHost = config.host, timestampMs = now, submission = submission))
        }
    }

    suspend fun flushQueue() {
        if (isFlushing) return
        val pending = store.snapshot()
        if (pending.isEmpty()) return
        isFlushing = true
        try {
            val remaining = mutableListOf<PendingScrobble>()
            for (entry in pending) {
                val config = subsonicClient.configFor(entry.serverHost)
                val ok = config != null && subsonicClient.scrobble(config, entry.trackId, entry.submission, time = entry.timestampMs)
                if (!ok) remaining += entry
            }
            store.replaceAll(remaining)
        } finally {
            isFlushing = false
        }
    }
}
