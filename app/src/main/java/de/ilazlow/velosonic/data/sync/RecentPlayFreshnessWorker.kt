package de.ilazlow.velosonic.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Standalone worker for [SyncEngine.refreshRecentPlayFreshnessAllServers] — see that method's
 *  doc comment for why this is decoupled from the main partial-sync job. */
@HiltWorker
class RecentPlayFreshnessWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            syncEngine.refreshRecentPlayFreshnessAllServers()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
