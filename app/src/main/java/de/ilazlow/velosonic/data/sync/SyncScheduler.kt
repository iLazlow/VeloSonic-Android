package de.ilazlow.velosonic.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

private const val UNIQUE_WORK_NAME = "periodic_library_sync"

/**
 * Schedules the periodic background partial sync. WorkManager's minimum periodic interval is
 * 15 minutes; the actual "did enough time pass to bother" decision is SyncEngine's own 3-hour
 * gate (see performPartialSync), same division of responsibility as the iOS client's
 * opportunistically-firing BGProcessingTask.
 */
object SyncScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofMinutes(30))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
