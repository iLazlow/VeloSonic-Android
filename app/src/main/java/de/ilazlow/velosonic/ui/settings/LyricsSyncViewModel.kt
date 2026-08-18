package de.ilazlow.velosonic.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.datastore.OfflineModeStore
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.lyrics.LYRICS_SYNC_MANUAL_WORK_NAME
import de.ilazlow.velosonic.data.lyrics.LYRICS_SYNC_PROGRESS_CHECKED
import de.ilazlow.velosonic.data.lyrics.LYRICS_SYNC_PROGRESS_CURRENT_TITLE
import de.ilazlow.velosonic.data.lyrics.LYRICS_SYNC_PROGRESS_TOTAL
import de.ilazlow.velosonic.data.lyrics.LyricsSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsSyncUiState(
    val isOffline: Boolean = false,
    val isSyncing: Boolean = false,
    val currentTrackTitle: String = "",
    val checked: Int = 0,
    val total: Int = 0
) {
    val progress: Float get() = if (total > 0) checked.toFloat() / total else 0f
    val pending: Int get() = (total - checked).coerceAtLeast(0)
}

/** Backs the Lyrics Sync section of [LyricsSettingsScreen] — mirrors LyricsSyncManager.swift's
 *  observable state, sourced from [LyricsSyncScheduler]'s manual [WorkInfo] (checked/total/
 *  current-title come from the worker's own progress data while running) and a fresh DB count
 *  once it's not (mirrors `refreshCounts`). */
@HiltViewModel
class LyricsSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    offlineModeStore: OfflineModeStore
) : ViewModel() {
    private val _idleCounts = MutableStateFlow(0 to 0)

    init { refreshCounts() }

    private fun refreshCounts() {
        viewModelScope.launch {
            _idleCounts.value = trackDao.countAllLyricsChecked() to trackDao.countAll()
        }
    }

    private val workInfo: StateFlow<WorkInfo?> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(LYRICS_SYNC_MANUAL_WORK_NAME)
        .map { it.firstOrNull { info -> !info.state.isFinished } ?: it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<LyricsSyncUiState> = combine(workInfo, _idleCounts, offlineModeStore.isEnabled) { info, idle, offline ->
        if (info != null && info.state == WorkInfo.State.RUNNING) {
            LyricsSyncUiState(
                isOffline = offline,
                isSyncing = true,
                currentTrackTitle = info.progress.getString(LYRICS_SYNC_PROGRESS_CURRENT_TITLE).orEmpty(),
                checked = info.progress.getInt(LYRICS_SYNC_PROGRESS_CHECKED, idle.first),
                total = info.progress.getInt(LYRICS_SYNC_PROGRESS_TOTAL, idle.second)
            )
        } else {
            if (info?.state?.isFinished == true) refreshCounts()
            LyricsSyncUiState(isOffline = offline, isSyncing = false, checked = idle.first, total = idle.second)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsSyncUiState())

    fun startSync(resetFirst: Boolean = false) = LyricsSyncScheduler.startNow(context, resetFirst)

    fun stopSync() = LyricsSyncScheduler.cancel(context)
}
