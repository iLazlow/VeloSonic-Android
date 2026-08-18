package de.ilazlow.velosonic.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background partial sync — mirrors the iOS client's BGProcessingTask handler, which
 * fires opportunistically and relies on performPartialSync's own internal 3-hour gate to decide
 * whether there's actually anything to do. `forced = false` here for the same reason: this is
 * the routine background tick, not a user-requested "sync now" (that's a future manual trigger
 * once a Settings/Library UI exists to host it, which would call SyncEngine directly).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            syncEngine.performPartialSyncAllServers(forced = false)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
