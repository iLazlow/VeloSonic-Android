package de.ilazlow.velosonic.data.lyrics

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.TrackDao
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_CONCURRENT = 5

/** Per server, per worker run — not the whole pending backlog at once. Confirmed live: with no
 *  cap, a real device's first backfill against a ~50k-track library kept the radio continuously
 *  active long enough to visibly drain its battery while the app sat "idle" in the background.
 *  See [LyricsSyncScheduler.scheduleNextChunk] for how the remaining backlog gets picked up. */
private const val BATCH_LIMIT_PER_SERVER = 200

const val LYRICS_SYNC_KEY_RESET_FIRST = "reset_first"
/** Set by [LyricsSyncScheduler.startNow] — a manual, foreground-triggered sync chains straight
 *  through its own remaining batches (same unique work name, effectively no delay) instead of the
 *  automatic/periodic path's conservative 30s-delayed, battery-gated [LyricsSyncScheduler.scheduleNextChunk],
 *  matching what someone who just tapped "Sync Now" and is watching the progress bar actually
 *  expects, while leaving the idle-battery-drain fix that [BATCH_LIMIT_PER_SERVER] exists for
 *  completely intact for the periodic case. */
const val LYRICS_SYNC_KEY_MANUAL = "manual"
const val LYRICS_SYNC_PROGRESS_CHECKED = "checked"
const val LYRICS_SYNC_PROGRESS_TOTAL = "total"
const val LYRICS_SYNC_PROGRESS_CURRENT_TITLE = "current_title"

/**
 * Background lyrics backfill — mirrors LyricsSyncManager.swift: every never-yet-checked track
 * (`lyricsCheckedAt IS NULL`, see TrackDao.getPendingLyricsCheck), fetched with a 5-wide
 * concurrency window so one slow/hanging lookup doesn't stall the whole backfill. Every server
 * is iterated regardless of OpenSubsonic support — [LyricsRepository.fetchAndPersist] already
 * gates the Navidrome branch on that per-track, and lrclib doesn't need it at all. Naturally
 * resumable across app restarts since it only ever looks at rows that haven't been stamped yet —
 * no separate progress bookkeeping needed, except what's reported here for the Settings UI (see
 * [LyricsSyncScheduler]/`LyricsSyncViewModel`), which mirrors LyricsSyncManager's
 * checkedTracks/totalTracks/currentTrackTitle exactly (library-wide counts, not just this run).
 */
@HiltWorker
class LyricsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val serverConfigDao: ServerConfigDao,
    private val trackDao: TrackDao,
    private val lyricsRepository: LyricsRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val configs = serverConfigDao.getAll()

            if (inputData.getBoolean(LYRICS_SYNC_KEY_RESET_FIRST, false)) {
                configs.forEach { trackDao.resetLyricsCheckedAt(it.host) }
            }

            val total = trackDao.countAll()
            val checked = AtomicInteger(trackDao.countAllLyricsChecked())
            setProgress(progressData(checked.get(), total, ""))

            for (config in configs) {
                val pending = trackDao.getPendingLyricsCheck(config.host, BATCH_LIMIT_PER_SERVER)
                if (pending.isEmpty()) continue
                val semaphore = Semaphore(MAX_CONCURRENT)
                coroutineScope {
                    pending.map { track ->
                        async {
                            semaphore.withPermit {
                                lyricsRepository.fetchAndPersist(track)
                                setProgress(progressData(checked.incrementAndGet(), total, track.title))
                            }
                        }
                    }.forEach { it.await() }
                }
            }

            if (configs.any { trackDao.countPendingLyricsCheck(it.host) > 0 }) {
                if (inputData.getBoolean(LYRICS_SYNC_KEY_MANUAL, false)) {
                    LyricsSyncScheduler.scheduleNextManualChunk(applicationContext)
                } else {
                    LyricsSyncScheduler.scheduleNextChunk(applicationContext)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun progressData(checked: Int, total: Int, currentTitle: String) = workDataOf(
        LYRICS_SYNC_PROGRESS_CHECKED to checked,
        LYRICS_SYNC_PROGRESS_TOTAL to total,
        LYRICS_SYNC_PROGRESS_CURRENT_TITLE to currentTitle
    )
}
