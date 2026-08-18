package de.ilazlow.velosonic.data.sync

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.ilazlow.velosonic.data.db.ServerConfigDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Which [SyncEngine] entry point [SyncNowWorker] should drive — the three user-triggered (as
 *  opposed to routine-periodic) sync operations, all long-running enough on a real library to hit
 *  the same "gets killed if backgrounded without foreground priority" problem. */
enum class SyncMode { INITIAL, FULL_RESYNC, FORCED_PARTIAL }

/**
 * Runs one manually-triggered sync as a genuine Android foreground service (via WorkManager's
 * [setForeground]) with a live progress notification — the fix for sync silently dying when the
 * app is backgrounded: a plain `appScope.launch`'d coroutine has no foreground priority, so the OS
 * is free to freeze or kill the process once no Activity is visible, especially over a multi-
 * minute first sync. WorkManager's own retry/backoff (see [SyncScheduler]'s policy, applied the
 * same way here) also covers the case where the process gets killed anyway.
 */
@HiltWorker
class SyncNowWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val serverConfigDao: ServerConfigDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = inputData.getString(KEY_HOST) ?: return Result.failure()
        val mode = inputData.getString(KEY_MODE)?.let { SyncMode.valueOf(it) } ?: return Result.failure()

        SyncNotifications.ensureChannel(applicationContext)
        val serverName = serverConfigDao.getByHost(host)?.name?.ifBlank { host } ?: host
        val notificationId = SyncNotifications.notificationIdFor(host)
        val title = "Syncing $serverName"

        setForeground(
            ForegroundInfo(
                notificationId,
                SyncNotifications.build(applicationContext, title, "Preparing…", 0.0, indeterminate = true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        )

        // Throttled (2/sec) rather than firing on every SyncEngine.state emission — a large
        // library's per-item fetch progress can update many times a second, and each update here
        // is a binder call to the system notification service, not free.
        val observerScope = CoroutineScope(SupervisorJob())
        observerScope.launch {
            syncEngine.state.sample(500).collect { state ->
                if (!state.isSyncing) return@collect
                setForeground(
                    ForegroundInfo(
                        notificationId,
                        SyncNotifications.build(
                            applicationContext,
                            title,
                            state.statusMessage.ifBlank { "Preparing…" },
                            state.progress,
                            indeterminate = false
                        ),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                )
            }
        }

        return try {
            when (mode) {
                SyncMode.INITIAL -> syncEngine.performInitialSync(host)
                SyncMode.FULL_RESYNC -> syncEngine.performFullResync(host)
                SyncMode.FORCED_PARTIAL -> syncEngine.performPartialSync(host, forced = true)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        } finally {
            observerScope.cancel()
        }
    }

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_MODE = "mode"
        private const val ALL_SERVERS_CHAIN_NAME = "sync_now_all_servers"

        /** Unique per (host, mode) so re-tapping "Full Resync" while one's already running just
         *  no-ops against the in-flight request rather than racing a second one. */
        private fun uniqueWorkName(host: String, mode: SyncMode) = "sync_now_${mode.name}_$host"

        private fun buildRequest(host: String, mode: SyncMode) =
            OneTimeWorkRequestBuilder<SyncNowWorker>()
                .setInputData(workDataOf(KEY_HOST to host, KEY_MODE to mode.name))
                .build()

        fun enqueue(context: Context, host: String, mode: SyncMode) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(host, mode),
                ExistingWorkPolicy.KEEP,
                buildRequest(host, mode)
            )
        }

        /** One host at a time, in order — used for the "Resync"/"Full Resync" buttons that act on
         *  every server at once. Confirmed live: enqueuing each host as its own independent
         *  [enqueue] call let WorkManager run them concurrently, which made multiple servers write
         *  to [SyncEngine]'s single shared [SyncEngine.state] at the same time — the Sync Status
         *  section visibly jumped between servers' progress instead of showing one coherent
         *  sequence, on top of spawning one notification per server firing at once. A
         *  [WorkContinuation] chain (each step depends on the previous finishing) makes only one
         *  [SyncNowWorker] — and so only one writer of [SyncEngine.state] — run at a time. */
        fun enqueueSequential(context: Context, hosts: List<String>, mode: SyncMode) {
            if (hosts.isEmpty()) return
            val workManager = WorkManager.getInstance(context)
            var continuation = workManager.beginUniqueWork(
                ALL_SERVERS_CHAIN_NAME,
                ExistingWorkPolicy.REPLACE,
                buildRequest(hosts.first(), mode)
            )
            for (host in hosts.drop(1)) {
                continuation = continuation.then(buildRequest(host, mode))
            }
            continuation.enqueue()
        }
    }
}
