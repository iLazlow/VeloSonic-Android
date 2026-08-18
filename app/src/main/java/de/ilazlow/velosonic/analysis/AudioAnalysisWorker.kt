package de.ilazlow.velosonic.analysis

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.ilazlow.velosonic.data.datastore.AudioAnalysisSettingsStore
import de.ilazlow.velosonic.data.db.AnalysisSkipDao
import de.ilazlow.velosonic.data.db.AnalysisSkipEntity
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.TrackAnalysisDao
import de.ilazlow.velosonic.data.db.TrackAnalysisEntity
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.db.TrackEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

const val KEY_SCAN_SKIPPED_ONLY = "scan_skipped_only"
const val KEY_PROGRESS_ANALYZED = "progress_analyzed"
const val KEY_PROGRESS_TOTAL = "progress_total"

private const val BATCH_FLUSH_SIZE = 20

/** A track is excluded from all future normal scans after just 1 failure — matches
 *  SDAnalysisSkip.skipThreshold, whose name is misleading (it reads like a retry allowance but
 *  is literally 1, so the very first failure already satisfies `failureCount >= skipThreshold`). */
private const val SKIP_THRESHOLD = 1

/**
 * Manual, user-triggered analysis pass — mirrors AudioAnalysisManager.swift's `runLoop`: resumes
 * from wherever a previous run left off (only tracks with neither a [TrackAnalysisEntity] row
 * nor a qualifying [AnalysisSkipEntity] are considered pending), semaphore-bounds concurrency at
 * [de.ilazlow.velosonic.data.datastore.AudioAnalysisSettings.concurrentSongs], batches DB writes
 * every [BATCH_FLUSH_SIZE] results instead of one write per track, and reports live progress via
 * [setProgress] for the settings screen to observe. Every configured server is processed in turn,
 * regardless of "visible in combined views" — analysis data isn't a combined-view concern.
 *
 * Unlike iOS's BGProcessingTask-scheduled continuation, there's no automatic background
 * trigger — WorkManager's own `enqueueUniqueWork`/`cancelUniqueWork` from
 * [AudioAnalysisRepository] already gives start/stop/survive-process-death for free, so no
 * separate scheduler class is needed the way [de.ilazlow.velosonic.data.lyrics.LyricsSyncScheduler]
 * exists for a periodic job.
 */
@HiltWorker
class AudioAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val serverConfigDao: ServerConfigDao,
    private val trackDao: TrackDao,
    private val trackAnalysisDao: TrackAnalysisDao,
    private val analysisSkipDao: AnalysisSkipDao,
    private val analysisEngine: AudioAnalysisEngine,
    private val settingsStore: AudioAnalysisSettingsStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scanSkippedOnly = inputData.getBoolean(KEY_SCAN_SKIPPED_ONLY, false)
        return try {
            val settings = settingsStore.settings.first()
            val configs = serverConfigDao.getAll()
            for (config in configs) {
                if (isStopped) break
                processServer(config.host, scanSkippedOnly, settings.concurrentSongs)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun processServer(serverHost: String, scanSkippedOnly: Boolean, concurrentSongs: Int) {
        val allTracks = trackDao.getAllForServer(serverHost)
        val analyzedIds = trackAnalysisDao.getAllForServer(serverHost).map { it.id }.toHashSet()
        val skippedIds = analysisSkipDao.getSkippedIds(serverHost).toHashSet()

        val pending = if (scanSkippedOnly) {
            allTracks.filter { it.id in skippedIds && it.id !in analyzedIds }
        } else {
            allTracks.filter { it.id !in analyzedIds && it.id !in skippedIds }
        }
        if (pending.isEmpty()) return

        val totalCount = allTracks.size
        val processedCount = AtomicInteger(analyzedIds.size)
        val writeMutex = Mutex()
        val pendingWrites = mutableListOf<TrackAnalysisEntity>()
        val semaphore = Semaphore(concurrentSongs.coerceIn(1, 16))

        suspend fun flush(force: Boolean = false) {
            val batch = writeMutex.withLock {
                if (pendingWrites.size >= BATCH_FLUSH_SIZE || (force && pendingWrites.isNotEmpty())) {
                    val copy = pendingWrites.toList()
                    pendingWrites.clear()
                    copy
                } else {
                    emptyList()
                }
            }
            if (batch.isNotEmpty()) trackAnalysisDao.upsertAll(batch)
        }

        coroutineScope {
            pending.map { track ->
                async {
                    if (isStopped) return@async
                    val entity = semaphore.withPermit { analyzeOne(track) }
                    if (entity != null) {
                        writeMutex.withLock { pendingWrites.add(entity) }
                        processedCount.incrementAndGet()
                        flush()
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS_ANALYZED to processedCount.get(),
                                KEY_PROGRESS_TOTAL to totalCount
                            )
                        )
                    }
                }
            }.forEach { it.await() }
        }
        flush(force = true)
    }

    /** Returns the row to persist, or null if this track was routed to the failure skip-queue
     *  (nothing to write yet — it'll be retried by a future "scan skipped" pass). */
    private suspend fun analyzeOne(track: TrackEntity): TrackAnalysisEntity? = try {
        analysisEngine.analyze(track)
    } catch (e: AnalysisTooShortException) {
        analysisEngine.tooShortStub(track)
    } catch (e: Exception) {
        recordFailure(track, e)
        null
    }

    private suspend fun recordFailure(track: TrackEntity, e: Exception) {
        val existing = analysisSkipDao.getById(track.id)
        val reason = e.message ?: e::class.simpleName ?: "unknown"
        val updated = existing?.copy(
            failureCount = existing.failureCount + 1,
            lastFailureAt = System.currentTimeMillis(),
            lastFailureReason = reason
        ) ?: AnalysisSkipEntity(
            id = track.id,
            trackId = track.id,
            serverHost = track.serverHost,
            failureCount = SKIP_THRESHOLD,
            lastFailureAt = System.currentTimeMillis(),
            lastFailureReason = reason
        )
        analysisSkipDao.upsert(updated)
    }
}
