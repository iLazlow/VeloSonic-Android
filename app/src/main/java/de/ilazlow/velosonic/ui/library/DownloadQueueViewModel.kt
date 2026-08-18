package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DownloadActivityItem(val download: Download, val track: TrackEntity?)

data class DownloadQueueUiState(
    val inProgress: List<DownloadActivityItem> = emptyList(),
    val failed: List<DownloadActivityItem> = emptyList(),
    val completed: List<DownloadActivityItem> = emptyList()
) {
    val isEmpty: Boolean get() = inProgress.isEmpty() && failed.isEmpty() && completed.isEmpty()
    val hasActive: Boolean get() = inProgress.isNotEmpty()
    val hasFailed: Boolean get() = failed.isNotEmpty()
    val hasFinished: Boolean get() = failed.isNotEmpty() || completed.isNotEmpty()
}

/**
 * Backs the download queue/activity screen — mirrors `DownloadQueueView.swift`'s three sections
 * (in-progress/failed/completed) computed from [DownloadRepository.activity], the transient
 * "what's happening right now" view distinct from the permanent Downloads library.
 */
@HiltViewModel
class DownloadQueueViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver
) : ViewModel() {

    val uiState: StateFlow<DownloadQueueUiState> = downloadRepository.activity
        .map { downloads -> buildState(downloads) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQueueUiState())

    private suspend fun buildState(downloads: List<Download>): DownloadQueueUiState {
        if (downloads.isEmpty()) return DownloadQueueUiState()
        val tracksById = libraryRepository.getTracksByCompositeIds(downloads.map { it.request.id }).associateBy { it.id }
        val items = downloads.map { DownloadActivityItem(it, tracksById[it.request.id]) }
        return DownloadQueueUiState(
            inProgress = items.filter { it.download.state == Download.STATE_QUEUED || it.download.state == Download.STATE_DOWNLOADING },
            failed = items.filter { it.download.state == Download.STATE_FAILED },
            completed = items.filter { it.download.state == Download.STATE_COMPLETED }
        )
    }

    fun coverArtUrl(serverHost: String, coverArt: String?, size: Int = 100): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArt, size)

    fun retry(trackId: String) = downloadRepository.retry(trackId)

    fun retryAllFailed() = downloadRepository.retryAllFailed()

    fun cancelQueue() = downloadRepository.cancelQueue()

    fun clearFinished() = downloadRepository.clearFinishedActivity()
}
