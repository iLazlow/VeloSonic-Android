package de.ilazlow.velosonic.data.lyrics

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration

private const val PERIODIC_WORK_NAME = "periodic_lyrics_sync"
private const val CHUNK_WORK_NAME = "lyrics_sync_chunk_continuation"

/** Separate unique work name from the periodic background pass — mirrors the Lyrics Sync
 *  section's own start/stop button, distinct from whatever the periodic worker happens to be
 *  doing quietly in the background (WorkManager doesn't let one unique name mix a periodic and a
 *  one-time request without the one-time enqueue replacing the periodic schedule). */
const val LYRICS_SYNC_MANUAL_WORK_NAME = "manual_lyrics_sync"

/** Same shape as [de.ilazlow.velosonic.data.sync.SyncScheduler] — a separate periodic worker
 *  (not folded into the main library sync) since a per-track lyrics lookup across a large
 *  library can take a long time and must never block the library sync it has nothing to do
 *  with, mirroring LyricsSyncManager's own decoupled background loop on iOS. */
object LyricsSyncScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<LyricsSyncWorker>(Duration.ofHours(6))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** User-triggered from the Lyrics Sync section — mirrors `LyricsSyncManager.startSync`.
     *  [ExistingWorkPolicy.KEEP] so tapping Sync while one's already running just keeps observing
     *  it instead of spawning a duplicate. Marked [LYRICS_SYNC_KEY_MANUAL] so it chains through
     *  its own remaining backlog via [scheduleNextManualChunk] instead of the automatic path's
     *  slow, battery-gated [scheduleNextChunk] — see that constant's doc comment. */
    fun startNow(context: Context, resetFirst: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<LyricsSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(LYRICS_SYNC_KEY_RESET_FIRST to resetFirst, LYRICS_SYNC_KEY_MANUAL to true))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            LYRICS_SYNC_MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Mirrors `LyricsSyncManager.stopSync`. Also cancels any pending chunk continuation (see
     *  [scheduleNextChunk]) so stopping mid-backlog actually stops it rather than resuming a beat
     *  later from wherever [LyricsSyncWorker] left off. */
    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LYRICS_SYNC_MANUAL_WORK_NAME)
        workManager.cancelUniqueWork(CHUNK_WORK_NAME)
    }

    /** [LyricsSyncWorker] processes a bounded batch per run rather than the whole pending backlog
     *  at once — confirmed live: an unbounded run against a 50k+ track library kept a real
     *  device's radio continuously active long enough to visibly drain its battery while the app
     *  sat "idle" in the background. This schedules the next batch as its own WorkManager job
     *  (same constraints, plus a short initial delay) instead of looping internally, so each
     *  chunk boundary is a real checkpoint where WorkManager re-evaluates
     *  network/battery-not-low before continuing — a battery drop mid-backlog actually pauses it.
     *  Only reached for the *periodic* worker's own runs — a manual run's continuation is
     *  [scheduleNextManualChunk] instead (see [LYRICS_SYNC_KEY_MANUAL]). */
    fun scheduleNextChunk(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<LyricsSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(Duration.ofSeconds(30))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CHUNK_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Continuation for a *manual* run — reuses [LYRICS_SYNC_MANUAL_WORK_NAME] itself (not the
     *  separate [CHUNK_WORK_NAME] the periodic path uses) so [de.ilazlow.velosonic.ui.settings.LyricsSyncViewModel]'s
     *  existing observation of that one unique work name keeps following the whole chain straight
     *  through to completion with no UI changes needed, and re-enqueues without an artificial
     *  delay or the periodic path's battery-not-low gate — this is a foreground, user-initiated
     *  "do it now" action, not a background trickle meant to conserve battery while idle.
     *  [ExistingWorkPolicy.APPEND_OR_REPLACE], not [ExistingWorkPolicy.REPLACE]: this is called
     *  from *inside* the currently-running worker's own `doWork()`, still executing under this
     *  exact same unique name — REPLACE cancels running work too, which would cancel the very
     *  call site enqueuing it; APPEND_OR_REPLACE chains the next chunk after the current one
     *  finishes instead of racing to cancel it. */
    fun scheduleNextManualChunk(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<LyricsSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(LYRICS_SYNC_KEY_MANUAL to true))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            LYRICS_SYNC_MANUAL_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
