package de.ilazlow.velosonic.analysis

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.db.AnalysisSkipDao
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.TrackAnalysisDao
import de.ilazlow.velosonic.data.db.TrackDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val UNIQUE_WORK_NAME = "audio_analysis"

data class AnalysisCounts(val analyzed: Int = 0, val skipped: Int = 0, val total: Int = 0)

/**
 * UI-facing façade over [AudioAnalysisWorker] — start/stop/observe via WorkManager's own
 * unique-work + `getWorkInfosForUniqueWorkFlow` machinery, which already gives "survives process
 * death, only one run at a time, live progress" for free, matching what iOS hand-rolled with
 * `isAnalyzing`/`BGProcessingTaskRequest`/manual 2-second polling.
 */
@Singleton
class AudioAnalysisRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverConfigDao: ServerConfigDao,
    private val trackDao: TrackDao,
    private val trackAnalysisDao: TrackAnalysisDao,
    private val analysisSkipDao: AnalysisSkipDao
) {
    private val workManager get() = WorkManager.getInstance(context)

    val isRunning: Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
        .map { infos -> infos.any { !it.state.isFinished } }

    /** (analyzed, total) for whichever pass is currently running — (0, 0) when idle. */
    val progress: Flow<Pair<Int, Int>> = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
        .map { infos ->
            val running = infos.firstOrNull { !it.state.isFinished }
            val analyzed = running?.progress?.getInt(KEY_PROGRESS_ANALYZED, 0) ?: 0
            val total = running?.progress?.getInt(KEY_PROGRESS_TOTAL, 0) ?: 0
            analyzed to total
        }

    fun start(scanSkippedOnly: Boolean = false) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<AudioAnalysisWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_SCAN_SKIPPED_ONLY to scanSkippedOnly))
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun stop() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    suspend fun counts(): AnalysisCounts {
        var total = 0
        var analyzed = 0
        var skipped = 0
        for (config in serverConfigDao.getAll()) {
            total += trackDao.countForServer(config.host)
            analyzed += trackAnalysisDao.getAllForServer(config.host).size
            skipped += analysisSkipDao.getSkippedIds(config.host).size
        }
        return AnalysisCounts(analyzed, skipped, total)
    }

    suspend fun clearSkippedQueue() {
        for (config in serverConfigDao.getAll()) analysisSkipDao.deleteByServer(config.host)
    }

    suspend fun deleteAllAnalysisData() {
        for (config in serverConfigDao.getAll()) {
            trackAnalysisDao.deleteByServer(config.host)
            analysisSkipDao.deleteByServer(config.host)
        }
    }
}
