package de.ilazlow.velosonic.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

private const val UNIQUE_WORK_NAME = "recent_play_freshness"

/** Same shape as [LyricsSyncScheduler] — a small, separate periodic worker. 15 minutes is
 *  WorkManager's minimum periodic interval; it's also cheap enough to justify running that
 *  often (2 lightweight `getAlbumList2` calls per server, not a real sync pass), which is the
 *  whole point — Home's "Recently Played"/"Frequently Played" should feel current, not lag
 *  behind the main partial sync's 3-hour gate. */
object RecentPlayFreshnessScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<RecentPlayFreshnessWorker>(Duration.ofMinutes(15))
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
