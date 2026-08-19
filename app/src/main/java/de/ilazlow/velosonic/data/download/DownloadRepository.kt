package de.ilazlow.velosonic.data.download

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.artwork.AnimatedArtworkRepository
import de.ilazlow.velosonic.data.artwork.PermanentArtworkStore
import de.ilazlow.velosonic.data.datastore.StorageSettings
import de.ilazlow.velosonic.data.datastore.StorageSettingsStore
import de.ilazlow.velosonic.data.datastore.resolvedQueryValue
import de.ilazlow.velosonic.data.debug.LogManager
import de.ilazlow.velosonic.data.db.StandaloneDownloadDao
import de.ilazlow.velosonic.data.db.StandaloneDownloadEntity
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Encoded into [androidx.media3.exoplayer.offline.DownloadRequest.data] so the foreground
 *  notification ([VeloSonicDownloadService.getForegroundNotification]) can show what's actually
 *  downloading — Media3's `Download`/`DownloadRequest` carry no title of their own, only the
 *  opaque [TrackEntity.id] used as the request id. */
@Serializable
data class DownloadRequestMetadata(val title: String, val artist: String)

/**
 * App-facing download API — wraps Media3's [DownloadManager] (see [DownloadCacheProvider]) and
 * mirrors iOS's per-track download model exactly: "download an album/playlist" is just this
 * called once per track, and a completed download not part of a bulk group is recorded in
 * [StandaloneDownloadDao] (iOS's `SDStandaloneDownload`) so the Downloads library screen's
 * "Songs" category can find it.
 *
 * Unlike iOS's `SDPlaylist.isFullyDownloaded` — a stored flag that needed a whole self-healing
 * pass to recover from a bulk download partially failing — a playlist's/album's "fully
 * downloaded" state here is always computed live from this repository's [downloads] map by the
 * caller (see PlaylistDetailViewModel/AlbumDetailViewModel), so it can't go stale in the first
 * place.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadCacheProvider: DownloadCacheProvider,
    private val subsonicClient: PlaybackSubsonicClient,
    private val standaloneDownloadDao: StandaloneDownloadDao,
    private val trackDao: TrackDao,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val permanentArtworkStore: PermanentArtworkStore,
    private val animatedArtworkRepository: AnimatedArtworkRepository,
    private val logManager: LogManager,
    private val storageSettingsStore: StorageSettingsStore,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    val downloads: StateFlow<Map<String, Download>> = _downloads.asStateFlow()

    // [downloadTrack] is a plain (non-suspend) call from many UI sites — kept up to date via this
    // background collector instead of a `runBlocking` DataStore read on every download tap, same
    // pattern PlaybackEngine uses for its own live-settings field.
    private var storageSettings = StorageSettings()

    init {
        appScope.launch {
            storageSettingsStore.settings.collect { storageSettings = it }
        }
    }

    // Tracks which download ids belong in the "activity" view (the queue/progress screen) —
    // deliberately NOT every completed download ever made (the Downloads library screen already
    // covers that permanent record via `downloads` itself), just what's currently queued/
    // downloading/failed, plus anything recently finished that hasn't been cleared yet. Mirrors
    // iOS's DownloadManager.items, which is a separate transient list from the on-disk registry.
    private val _activityIds = MutableStateFlow<Set<String>>(emptySet())
    val activity: StateFlow<List<Download>> = combine(_downloads, _activityIds) { downloads, ids ->
        ids.mapNotNull { downloads[it] }
    }.stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            _downloads.value = _downloads.value + (download.request.id to download)
            when (download.state) {
                Download.STATE_COMPLETED -> {
                    logManager.write("[Download] Completed: ${download.request.id}")
                    appScope.launch { persistArtworkForCompletedDownload(download.request.id) }
                }
                Download.STATE_FAILED -> logManager.write("[Download] Failed: ${download.request.id} (${finalException?.message})")
                else -> Unit
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            _downloads.value = _downloads.value - download.request.id
            _activityIds.value = _activityIds.value - download.request.id
            logManager.write("[Download] Removed: ${download.request.id}")
            appScope.launch { cleanupOrphanArtwork(download.request.id) }
        }
    }

    /** Mirrors DownloadManager.swift's `didFinishDownloadingTo` artwork fetch — fires once per
     *  completed download, no-ops if that cover art is already persisted (e.g. a sibling track
     *  from the same album finished first). */
    private suspend fun persistArtworkForCompletedDownload(trackId: String) {
        val track = trackDao.getById(trackId) ?: return
        val coverArt = track.coverArt ?: return
        val remoteUrl = coverArtUrlResolver.remoteUrlFor(track.serverHost, coverArt, 600) ?: return
        permanentArtworkStore.persistIfNeeded(track.serverHost, coverArt, remoteUrl)
        animatedArtworkRepository.persistForDownload(
            serverHost = track.serverHost,
            coverArtId = coverArt,
            trackId = track.id,
            artist = track.artistName,
            album = track.albumName.orEmpty(),
            title = track.title
        )
    }

    /** Unlike iOS (which only runs this orphan check for the playlist-track-removal path,
     *  leaking permanent artwork everywhere else — see the port research), this fires for
     *  *every* removal: deletes the permanent artwork only if no other currently-downloaded
     *  track still shares the same (serverHost, coverArt) pair. */
    private suspend fun cleanupOrphanArtwork(removedTrackId: String) {
        val removedTrack = trackDao.getById(removedTrackId) ?: return
        val coverArt = removedTrack.coverArt ?: return
        val remainingTracks = trackDao.getByIds(_downloads.value.keys.toList())
        val stillNeeded = remainingTracks.any { it.serverHost == removedTrack.serverHost && it.coverArt == coverArt }
        if (!stillNeeded) {
            permanentArtworkStore.delete(removedTrack.serverHost, coverArt)
        }
    }

    init {
        downloadCacheProvider.downloadManager.addListener(listener)
        appScope.launch {
            val initial = withContext(Dispatchers.IO) {
                buildMap {
                    downloadCacheProvider.downloadManager.downloadIndex.getDownloads().use { cursor ->
                        while (cursor.moveToNext()) {
                            put(cursor.download.request.id, cursor.download)
                        }
                    }
                }
            }
            _downloads.value = initial
            // Anything not yet completed (e.g. the app was killed mid-download) is inherently
            // still "in progress" and belongs in the activity view; completed entries from past
            // sessions are the library's permanent record, not fresh activity — don't resurrect
            // them here.
            _activityIds.value = initial.filterValues { it.state != Download.STATE_COMPLETED }.keys
        }
    }

    fun statusFor(trackId: String): Int? = downloads.value[trackId]?.state

    fun isDownloaded(trackId: String): Boolean = statusFor(trackId) == Download.STATE_COMPLETED

    fun isActiveOrDownloaded(trackId: String): Boolean = when (statusFor(trackId)) {
        Download.STATE_COMPLETED, Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> true
        else -> false
    }

    fun downloadTrack(track: TrackEntity, partOfBulkGroup: Boolean = false) {
        if (isActiveOrDownloaded(track.id)) return
        val format = storageSettings.downloadFormat.resolvedQueryValue(storageSettings.customDownloadFormat)
        val maxBitRate = storageSettings.downloadBitrate.resolvedQueryValue(storageSettings.customDownloadBitrate)
        val streamUrl = subsonicClient.downloadStreamUrlFor(track.serverHost, track.subsonicId, format, maxBitRate) ?: return
        val metadata = Json.encodeToString(DownloadRequestMetadata(track.title, track.artistName))
        val request = DownloadRequest.Builder(track.id, Uri.parse(streamUrl))
            .setData(metadata.toByteArray(Charsets.UTF_8))
            .build()
        DownloadService.sendAddDownload(context, VeloSonicDownloadService::class.java, request, false)
        logManager.write("[Download] Queued: '${track.title}' by ${track.artistName}")
        _activityIds.value = _activityIds.value + track.id
        if (!partOfBulkGroup) {
            appScope.launch { standaloneDownloadDao.insert(StandaloneDownloadEntity(track.id)) }
        }
    }

    fun downloadTracks(tracks: List<TrackEntity>, partOfBulkGroup: Boolean = false) {
        tracks.forEach { downloadTrack(it, partOfBulkGroup) }
    }

    fun removeDownload(trackId: String) {
        DownloadService.sendRemoveDownload(context, VeloSonicDownloadService::class.java, trackId, false)
        appScope.launch { standaloneDownloadDao.delete(StandaloneDownloadEntity(trackId)) }
    }

    fun removeDownloads(trackIds: List<String>) {
        trackIds.forEach(::removeDownload)
    }

    /** Re-sends the same add-download request for a [Download.STATE_FAILED] entry — Media3's
     *  `DownloadManager` restarts a failed download from scratch on re-add, same as any other
     *  add, so this is really just [downloadTrack] again. Always treated as "part of a bulk
     *  group" so it never re-inserts into [standaloneDownloadDao] — whatever standalone-ness the
     *  original download had was already recorded then, and retrying shouldn't relabel it. */
    fun retry(trackId: String) {
        appScope.launch {
            val track = trackDao.getById(trackId) ?: return@launch
            downloadTrack(track, partOfBulkGroup = true)
        }
    }

    fun retryAllFailed() {
        activity.value.filter { it.state == Download.STATE_FAILED }.forEach { retry(it.request.id) }
    }

    /** Stops every currently queued/downloading item — a real cancel (removes it from Media3's
     *  index entirely), not just hiding it from the activity view. */
    fun cancelQueue() {
        activity.value
            .filter { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING }
            .forEach { removeDownload(it.request.id) }
    }

    /** Drops completed/failed entries from the activity view only — the underlying Media3 index
     *  (and any downloaded file) is untouched, since completed downloads still belong in the
     *  Downloads library screen. Mirrors iOS's `clearCompleted`. */
    fun clearFinishedActivity() {
        val finishedIds = activity.value
            .filter { it.state == Download.STATE_COMPLETED || it.state == Download.STATE_FAILED }
            .map { it.request.id }
            .toSet()
        _activityIds.value = _activityIds.value - finishedIds
    }

    /** Cancels anything mid-flight for [host]'s tracks before a server-address migration runs —
     *  mirrors iOS's `DownloadManager.cancelDownloads(forHost:)` — so nothing completes mid-
     *  migration and writes a file under a cache key [ServerMigrationManager][de.ilazlow.velosonic.data.sync.ServerMigrationManager]
     *  never sees. Completed downloads are untouched; those are migrated in place instead of
     *  cancelled. */
    fun cancelActiveDownloadsForHost(host: String) {
        val prefix = "${host}_"
        activity.value
            .filter { it.request.id.startsWith(prefix) && (it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING) }
            .forEach { removeDownload(it.request.id) }
    }

    /** Re-scans the Media3 download index from scratch — called after
     *  [de.ilazlow.velosonic.data.sync.ServerMigrationManager] rewrites index rows directly
     *  (bypassing [DownloadManager], so its change listeners never fire), to bring [downloads]
     *  back in sync with what's actually on disk. */
    suspend fun refreshFromIndex() {
        val refreshed = withContext(Dispatchers.IO) {
            buildMap {
                downloadCacheProvider.downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        put(cursor.download.request.id, cursor.download)
                    }
                }
            }
        }
        _downloads.value = refreshed
    }
}
