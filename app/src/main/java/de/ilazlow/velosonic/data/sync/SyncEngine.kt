package de.ilazlow.velosonic.data.sync

import android.util.Log
import androidx.room.withTransaction
import de.ilazlow.velosonic.data.db.AlbumDao
import de.ilazlow.velosonic.data.db.ArtistDao
import de.ilazlow.velosonic.data.db.PlaylistDao
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.PlaylistSortField
import de.ilazlow.velosonic.data.db.RadioStationDao
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.db.SyncMetadataDao
import de.ilazlow.velosonic.data.db.SyncMetadataEntity
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.db.VeloSonicDatabase
import de.ilazlow.velosonic.data.debug.LogManager
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.network.dto.AlbumDetailDto
import de.ilazlow.velosonic.data.network.dto.AlbumDto
import de.ilazlow.velosonic.data.network.dto.SubsonicArtistDto
import de.ilazlow.velosonic.domain.ServerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/** Servers send `starred` as ISO-8601 with fractional seconds — mirrors the iOS engine's
 *  ISO8601DateFormatter(.withInternetDateTime, .withFractionalSeconds) parse. */
private fun parseIso8601ToEpochMillis(raw: String?): Long? {
    if (raw == null) return null
    return try {
        Instant.parse(raw).toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}

data class SyncState(
    val isSyncing: Boolean = false,
    val progress: Double = 0.0,
    val statusMessage: String = "",
    val serverProgressLabel: String = "",
    /** True after [SyncEngine.performInitialSync] throws (e.g. a transient network blip) —
     *  without this, a failed first sync silently reset to an idle-looking state with nothing
     *  to distinguish it from "about to start", stranding the user on the sync screen forever
     *  (initial sync only ever fires once, from ServerRepository.addServer). Cleared the moment
     *  a new attempt actually starts making progress. */
    val hasFailed: Boolean = false
)

private const val TAG = "SyncEngine"
private const val THREE_HOURS_MS = 3 * 60 * 60 * 1000L
private const val ALBUM_COVERAGE_THRESHOLD = 0.75
private const val CONCURRENCY = 10

/**
 * Ports SyncManager.swift's three-tier sync strategy: a first-time [performInitialSync], a
 * user-triggered [performFullResync] that wipes and re-fetches everything, and a periodic
 * [performPartialSync] that reconciles additions/deletions without wiping. Does not port the
 * iOS-specific ceremony around it (Live Activities, BGProcessingTask, the ModelContext-swap
 * memory workaround) — Room has no equivalent identity-map memory growth to work around, and
 * the background-scheduling side is WorkManager's job (see SyncWorker), not this class's.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: SubsonicApi,
    private val database: VeloSonicDatabase,
    private val serverConfigDao: ServerConfigDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val radioStationDao: RadioStationDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val logManager: LogManager
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val syncingHosts: MutableSet<String> = Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    private fun url(config: ServerConfigEntity, endpoint: String, extra: Map<String, String> = emptyMap()) =
        SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra)

    /** Every sync log line, in both Logcat (dev-machine debugging) and [LogManager] (in-app
     *  viewer, gated on the user's own logging toggle) — one call instead of two at every site. */
    private fun log(message: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, message, error) else Log.i(TAG, message)
        logManager.write("[Sync] $message")
    }

    private fun updateProgress(progress: Double, message: String) {
        _state.update { it.copy(isSyncing = true, progress = progress, statusMessage = message, hasFailed = false) }
    }

    private fun clearProgress() {
        _state.update { it.copy(isSyncing = false, progress = 0.0, statusMessage = "") }
    }

    // ── Initial sync (first-time) ──────────────────────────────────────────────

    suspend fun performInitialSync(host: String) {
        if (!syncingHosts.add(host)) {
            log("[$host] performInitialSync skipped — already syncing")
            return
        }
        log("[$host] performInitialSync started")
        try {
            val config = serverConfigDao.getByHost(host) ?: return
            val meta = syncMetadataDao.getForHost(host)
            if (meta?.isInitialSyncComplete == true) {
                log("[$host] performInitialSync skipped — already complete")
                return
            }
            _state.update { it.copy(serverProgressLabel = config.name.ifBlank { config.host }) }
            syncFullLibrary(config, wipeFirst = false)
        } catch (e: Exception) {
            log("[$host] performInitialSync failed", e)
            _state.update { it.copy(hasFailed = true) }
        } finally {
            syncingHosts.remove(host)
            clearProgress()
        }
    }

    // ── Full resync (user-triggered, wipes and re-fetches everything) ─────────

    suspend fun performFullResync(host: String) {
        if (!syncingHosts.add(host)) return
        try {
            val config = serverConfigDao.getByHost(host) ?: return
            _state.update { it.copy(serverProgressLabel = config.name.ifBlank { config.host }) }
            syncFullLibrary(config, wipeFirst = true)
        } catch (e: Exception) {
            log("[$host] performFullResync failed", e)
        } finally {
            syncingHosts.remove(host)
            clearProgress()
        }
    }

    /** Playlists-only refresh for one server — lets a just-imported/updated playlist show up
     *  immediately without waiting for the next periodic partial sync, and without the cost of a
     *  full partial sync (starred/artist/album/genre deltas it doesn't need). Runs quietly,
     *  without touching [state] — mirrors iOS's `SyncManager.syncPlaylistsOnly`, called from a
     *  detached background task right after an import completes, not something the user watches. */
    suspend fun syncPlaylistsOnly(host: String) {
        if (!syncingHosts.add(host)) return
        try {
            val config = serverConfigDao.getByHost(host) ?: return
            syncPlaylistsForHost(config, emptyMap())
        } catch (e: Exception) {
            log("[$host] syncPlaylistsOnly failed", e)
        } finally {
            syncingHosts.remove(host)
        }
    }

    // ── Shared fetch-everything core (used by initial sync, full resync, and the
    //    "brand new second server discovered mid partial-sync" case) ───────────

    private suspend fun syncFullLibrary(config: ServerConfigEntity, wipeFirst: Boolean) {
        val host = config.host
        val t0 = System.currentTimeMillis()
        fun mark(msg: String) = log("[$host] +${System.currentTimeMillis() - t0}ms $msg")

        // Fetch everything from the network FIRST, before touching any local data — a real,
        // previously-observed failure mode: the wipe used to run up front, before any of this,
        // so a dropped connection partway through a multi-minute fetch on a large library (which
        // fetchConcurrently surfaces as SyncAbortedException after enough per-item failures)
        // left the DB wiped with nothing to replace it. Confirmed live: an interrupted full
        // resync on a ~4700-album library emptied it out entirely. Deferring the wipe until
        // every fetch below has already succeeded — then writing the replacement in one
        // transaction — means any failure above leaves the existing library untouched.
        updateProgress(0.1, "Loading artists...")
        val allArtists = fetchAllArtists(config)
        mark("fetched ${allArtists.size} artists")
        updateProgress(0.3, "${allArtists.size} artists")

        val allAlbums = fetchAlbumsForArtists(config, allArtists) { completed, total ->
            updateProgress(0.3 + (completed.toDouble() / total) * 0.3, "Loading albums... $completed/$total artists")
        }
        mark("fetched ${allAlbums.size} albums")
        updateProgress(0.6, "${allAlbums.size} albums")

        val trackFetchResult = fetchAlbumDetails(config, allAlbums) { completed, total ->
            updateProgress(0.6 + (completed.toDouble() / total) * 0.3, "Loading songs... $completed/$total albums")
        }
        mark("fetched track/album details")
        updateProgress(0.9, "${trackFetchResult.tracks.size} songs")

        // Preserve download/pin flags across a full-resync wipe — mirrors the iOS engine's
        // preserved-IDs snapshot. The actual re-download of newly-appeared playlist tracks is a
        // Phase 7 (Downloads) concern; this only keeps the flags themselves from being lost.
        var preservedPlaylists: Map<String, PlaylistEntity> = emptyMap()
        database.withTransaction {
            if (wipeFirst) {
                preservedPlaylists = playlistDao.getAllForServer(host).associateBy { it.subsonicId }
                trackDao.deleteByServer(host)
                albumDao.deleteByServer(host)
                artistDao.deleteByServer(host)
                playlistDao.deleteByServer(host)
                radioStationDao.deleteByServer(host)
            }
            artistDao.upsertAll(allArtists.map { it.toEntity(host) })
            albumDao.upsertAll(allAlbums.map { it.toLightweightEntity(host) })
            applyTrackFetchResult(host, trackFetchResult)
        }
        mark("wiped + upserted artists/albums/tracks atomically")

        updateProgress(0.9, "Loading playlists...")
        syncPlaylistsForHost(config, preservedPlaylists)
        mark("synced playlists")

        syncStarred(config)
        mark("synced starred")
        syncRadioStations(config)
        mark("synced radio stations")

        syncMetadataDao.upsert(
            SyncMetadataEntity(key = host, lastSyncDate = System.currentTimeMillis(), isInitialSyncComplete = true)
        )
        mark("wrote sync metadata — DONE")
        updateProgress(1.0, "Library ready")
    }

    // ── Partial sync (periodic, incremental — no wipe) ─────────────────────────

    /** Wipes every cached-library row for [host] — used when a server is removed entirely
     *  (ServerRepository.removeServer), same DAO set as syncFullLibrary's wipe-first branch but
     *  as a standalone, reusable step rather than inlined into that one call site. Doesn't touch
     *  `server_configs`/credentials or downloaded-audio files — those are ServerRepository's and
     *  DownloadRepository's own responsibility respectively. */
    suspend fun deleteAllDataForHost(host: String) {
        trackDao.deleteByServer(host)
        albumDao.deleteByServer(host)
        artistDao.deleteByServer(host)
        playlistDao.deleteByServer(host)
        radioStationDao.deleteByServer(host)
        syncMetadataDao.deleteForHost(host)
    }

    suspend fun performPartialSyncAllServers(forced: Boolean = false) {
        val configs = serverConfigDao.getAll()
        for ((index, config) in configs.withIndex()) {
            if (configs.size > 1) {
                val name = config.name.ifBlank { config.host }
                _state.update { it.copy(serverProgressLabel = "Server ${index + 1}/${configs.size}: $name") }
            }
            // This loop already owns serverProgressLabel (the richer "Server X/Y: name" form) —
            // performPartialSync shouldn't overwrite it with its own plain-name version.
            performPartialSync(config.host, forced, setOwnLabel = false)
        }
        _state.update { it.copy(serverProgressLabel = "") }
    }

    /** [setOwnLabel] is false only when called from [performPartialSyncAllServers], which already
     *  set a richer "Server X/Y: name" label for this pass — every other caller (a direct forced
     *  Resync from Settings/[SyncNowWorker], the "new server discovered" fallback, ...) needs this
     *  to set its own label, since nothing else will. Confirmed live: without this, the Settings
     *  Sync Status section showed a generic "Syncing…" with no server name for any sync that
     *  didn't go through the all-servers loop. */
    suspend fun performPartialSync(host: String, forced: Boolean = false, setOwnLabel: Boolean = true) {
        if (!syncingHosts.add(host)) {
            log("[$host] performPartialSync skipped — already syncing")
            return
        }
        log("[$host] performPartialSync started (forced=$forced)")
        try {
            val config = serverConfigDao.getByHost(host) ?: return
            if (setOwnLabel) {
                _state.update { it.copy(serverProgressLabel = config.name.ifBlank { config.host }) }
            }
            val localAlbumsCount = albumDao.countForServer(host)
            val isNewServer = localAlbumsCount == 0

            syncStarred(config)

            if (!forced && !isNewServer) {
                val meta = syncMetadataDao.getForHost(host)
                if (meta?.isInitialSyncComplete == true) {
                    val age = System.currentTimeMillis() - meta.lastSyncDate
                    if (age < THREE_HOURS_MS) return
                }
            }

            val allArtists = fetchAllArtists(config)

            if (isNewServer && allArtists.isNotEmpty()) {
                syncFullLibrary(config, wipeFirst = false)
                return
            }

            val serverAlbumsCount = allArtists.sumOf { it.albumCount ?: 0 }
            if (localAlbumsCount > 0 && localAlbumsCount < serverAlbumsCount * ALBUM_COVERAGE_THRESHOLD) {
                // DB coverage dropped well below what the server reports — something's off
                // (interrupted sync, corrupted state). Flag for a full re-sync instead of
                // patching incrementally on top of a possibly-inconsistent base.
                syncMetadataDao.upsert(
                    SyncMetadataEntity(
                        key = host,
                        lastSyncDate = syncMetadataDao.getForHost(host)?.lastSyncDate ?: 0L,
                        isInitialSyncComplete = false
                    )
                )
                return
            }

            updateProgress(0.1, "Removing deleted artists...")
            reconcileDeletedArtists(host, allArtists)

            val localArtistIds = artistDao.getAllForServer(host).map { it.subsonicId }.toSet()
            val newArtists = allArtists.filter { it.id !in localArtistIds }
            if (newArtists.isNotEmpty()) artistDao.upsertAll(newArtists.map { it.toEntity(host) })

            updateProgress(0.2, "Scanning for album changes...")
            reconcileArtistAlbumDeltas(config, allArtists)

            updateProgress(0.8, "Syncing playlists...")
            syncPlaylistsForHost(config, emptyMap())

            if (forced) {
                updateProgress(0.85, "Refreshing genres...")
                backfillGenres(config)
            }

            updateProgress(0.9, "Syncing radio stations...")
            syncRadioStations(config)

            updateProgress(0.95, "Finalizing...")
            syncStarred(config)

            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = host,
                    lastSyncDate = System.currentTimeMillis(),
                    isInitialSyncComplete = true
                )
            )
            log("[$host] performPartialSync complete")
        } catch (e: Exception) {
            log("[$host] performPartialSync failed", e)
        } finally {
            syncingHosts.remove(host)
            clearProgress()
        }
    }

    private suspend fun reconcileDeletedArtists(host: String, serverArtists: List<SubsonicArtistDto>) {
        val serverArtistIds = serverArtists.map { it.id }.toSet()
        val localArtists = artistDao.getAllForServer(host)
        for (localArtist in localArtists) {
            if (localArtist.subsonicId in serverArtistIds) continue
            for (album in albumDao.getByArtistRawId(localArtist.subsonicId, host)) {
                trackDao.deleteByAlbumRawId(album.subsonicId, host)
            }
            albumDao.deleteByArtistRawId(localArtist.subsonicId, host)
            artistDao.deleteById(localArtist.id)
        }
    }

    private suspend fun reconcileArtistAlbumDeltas(config: ServerConfigEntity, artists: List<SubsonicArtistDto>) {
        val host = config.host
        for (artist in artists) {
            val expectedAlbums = artist.albumCount ?: 0
            val localCount = albumDao.countByArtistRawId(artist.id, host)
            if (localCount == expectedAlbums) continue

            val serverAlbums = try {
                api.get(url(config, "getArtist", mapOf("id" to artist.id))).subsonicResponse?.artist?.album.orEmpty()
            } catch (e: Exception) {
                continue
            }
            val serverAlbumCids = serverAlbums.map { compositeId(host, it.id) }.toSet()
            val localAlbumsForArtist = albumDao.getByArtistRawId(artist.id, host)
            val localAlbumCids = localAlbumsForArtist.map { it.id }.toSet()

            for (localAlbum in localAlbumsForArtist) {
                if (localAlbum.id !in serverAlbumCids) {
                    trackDao.deleteByAlbumRawId(localAlbum.subsonicId, host)
                    albumDao.deleteById(localAlbum.id)
                }
            }

            val newAlbums = serverAlbums.filter { compositeId(host, it.id) !in localAlbumCids }
            if (newAlbums.isEmpty()) continue

            albumDao.upsertAll(newAlbums.map { it.toLightweightEntity(host) })
            fetchAndSaveTracksForAlbums(config, newAlbums)
        }
    }

    private suspend fun backfillGenres(config: ServerConfigEntity) {
        val host = config.host
        var offset = 0
        val pageSize = 500
        while (true) {
            val page = try {
                api.get(url(config, "getAlbumList2", mapOf("type" to "alphabeticalByName", "size" to "$pageSize", "offset" to "$offset")))
                    .subsonicResponse?.albumList2?.album.orEmpty()
            } catch (e: Exception) {
                break
            }
            if (page.isEmpty()) break
            val withGenre = page.filter { !it.genre.isNullOrEmpty() }
            if (withGenre.isNotEmpty()) {
                val ids = withGenre.map { compositeId(host, it.id) }
                val existing = albumDao.getByIds(ids).associateBy { it.id }
                val updated = withGenre.mapNotNull { dto ->
                    existing[compositeId(host, dto.id)]?.copy(genre = dto.genre)
                }
                if (updated.isNotEmpty()) albumDao.upsertAll(updated)
            }
            if (page.size < pageSize) break
            offset += page.size
        }
    }

    /** `reconcileArtistAlbumDeltas` only fetches [AlbumDetailDto] (the source of `played`/
     *  `playCount`/`starred`) for albums that are brand-new since the last sync — an
     *  already-known album's play stats otherwise stay frozen at whatever they were on its very
     *  first sync forever, since nothing else ever re-fetches them. That's the actual cause of
     *  Home's "Recently Played"/"Frequently Played" sections never updating after the initial
     *  sync — confirmed live: an album played today still sorted by a `played` timestamp from
     *  weeks ago. `getAlbumList2?type=recent/frequent` carries these same fields on the cheap,
     *  lightweight [AlbumDto] shape (same AlbumID3 schema Subsonic uses everywhere), so a
     *  20-album refetch per type — not a full per-album [AlbumDetailDto] round-trip — is enough
     *  to keep both sections current.
     *
     *  Deliberately NOT folded into [performPartialSync] (unlike iOS's `syncHomeOrdering`, which
     *  rides along inside its own partial-sync pass) — that's gated to once every 3 hours, far
     *  too coarse for "did I just finish an album" freshness. Instead this runs as its own
     *  standalone, much more frequent periodic job — see
     *  [de.ilazlow.velosonic.data.sync.RecentPlayFreshnessScheduler]. */
    suspend fun refreshRecentPlayFreshnessAllServers() {
        for (config in serverConfigDao.getAll()) {
            refreshRecentPlayFreshness(config)
        }
    }

    private suspend fun refreshRecentPlayFreshness(config: ServerConfigEntity) {
        val host = config.host
        for (type in listOf("recent", "frequent")) {
            val albums = try {
                api.get(url(config, "getAlbumList2", mapOf("type" to type, "size" to "20")))
                    .subsonicResponse?.albumList2?.album.orEmpty()
            } catch (e: Exception) {
                continue
            }
            if (albums.isEmpty()) continue
            val ids = albums.map { compositeId(host, it.id) }
            val existing = albumDao.getByIds(ids).associateBy { it.id }
            val updated = albums.mapNotNull { dto ->
                existing[compositeId(host, dto.id)]?.let { entity ->
                    entity.copy(
                        played = normalizeCreatedDate(dto.played) ?: entity.played,
                        playCount = dto.playCount ?: entity.playCount,
                        starredAt = dto.starred ?: entity.starredAt
                    )
                }
            }
            if (updated.isNotEmpty()) albumDao.upsertAll(updated)
        }
    }

    // ── Shared fetch helpers ────────────────────────────────────────────────────

    private suspend fun fetchAllArtists(config: ServerConfigEntity): List<SubsonicArtistDto> {
        val response = api.getArtistIndexes(url(config, "getArtists"))
        return response.subsonicResponse?.artists?.index.orEmpty().flatMap { it.artist.orEmpty() }
    }

    private suspend fun fetchAlbumsForArtists(
        config: ServerConfigEntity,
        artists: List<SubsonicArtistDto>,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): List<AlbumDto> {
        val results = fetchConcurrently(artists, concurrency = CONCURRENCY, onProgress = onProgress) { artist ->
            api.get(url(config, "getArtist", mapOf("id" to artist.id))).subsonicResponse?.artist?.album.orEmpty()
        }
        val seen = HashSet<String>()
        val albums = mutableListOf<AlbumDto>()
        for (albumList in results.filterNotNull()) {
            for (album in albumList) {
                if (seen.add(compositeId(config.host, album.id))) albums += album
            }
        }
        return albums
    }

    private data class TrackFetchResult(
        val tracks: List<TrackEntity>,
        val albumUpdates: List<Pair<String, AlbumDetailDto>>
    )

    /** Fetches getAlbum for each album — pure network fetch, no DB writes, so a caller can wipe
     *  local data only after confirming this succeeded (see [syncFullLibrary]'s doc comment). */
    private suspend fun fetchAlbumDetails(
        config: ServerConfigEntity,
        albums: List<AlbumDto>,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): TrackFetchResult {
        val host = config.host
        val results = fetchConcurrently(albums, concurrency = CONCURRENCY, onProgress = onProgress) { album ->
            album to api.get(url(config, "getAlbum", mapOf("id" to album.id))).subsonicResponse?.album
        }

        val seenTracks = HashSet<String>()
        val tracks = mutableListOf<TrackEntity>()
        val albumUpdates = mutableListOf<Pair<String, AlbumDetailDto>>()

        for (pair in results.filterNotNull()) {
            val (album, detail) = pair
            if (detail == null) continue
            albumUpdates += compositeId(host, album.id) to detail
            tracks += detail.song.orEmpty()
                .filter { seenTracks.add(compositeId(host, it.id)) }
                .map { it.toEntity(host, album) }
        }
        return TrackFetchResult(tracks, albumUpdates)
    }

    /** Writes a [fetchAlbumDetails] result: the tracks themselves, plus merging the extended
     *  album metadata (sortName, playCount, genresList, ...) onto the already-inserted
     *  lightweight AlbumEntity rows. */
    private suspend fun applyTrackFetchResult(host: String, result: TrackFetchResult) {
        if (result.tracks.isNotEmpty()) trackDao.upsertAll(result.tracks)
        if (result.albumUpdates.isNotEmpty()) {
            val existing = albumDao.getByIds(result.albumUpdates.map { it.first }).associateBy { it.id }
            val merged = result.albumUpdates.mapNotNull { (cid, detail) -> existing[cid]?.mergedWithDetail(detail) }
            if (merged.isNotEmpty()) albumDao.upsertAll(merged)
        }
    }

    /** Fetch-then-apply in one call — used by [reconcileArtistAlbumDeltas]'s partial-sync path,
     *  which only ever adds genuinely new albums (never wipes anything first), so there's no
     *  wipe-ordering risk to guard against the way [syncFullLibrary] has to. */
    private suspend fun fetchAndSaveTracksForAlbums(config: ServerConfigEntity, albums: List<AlbumDto>) {
        applyTrackFetchResult(config.host, fetchAlbumDetails(config, albums))
    }

    // ── Starred / radio / playlists (shared across all three sync tiers) ──────

    /**
     * `getStarred2` is the ONLY source of truth for what's actually starred — before this fix,
     * the sync only ever toggled `isStarred` on tracks/albums/artists that some *other* path
     * (the normal artist→album→track crawl) had already inserted into Room, silently dropping
     * anything starred that crawl never reached. That's a real gap for some Subsonic-compatible
     * bridges (confirmed live against a Tidal-via-Subsonic bridge server: `getStarred2` returned
     * starred songs that never showed up locally at all, since that bridge's own artist/album
     * listing didn't happen to surface them) — a starred item can exist without ever having gone
     * through the crawl. `getStarred2`'s own artist/album/song entries carry full metadata (not
     * just ids), so a starred entity missing from Room is now inserted fresh from that same
     * response instead of being silently skipped — mirrors the same "server as fallback for an
     * entity Room doesn't have" fix already applied to Artist/Album Detail's live-search gap. */
    private suspend fun syncStarred(config: ServerConfigEntity) {
        val host = config.host
        val starred = try {
            api.get(url(config, "getStarred2")).subsonicResponse?.starred2
        } catch (e: Exception) {
            return
        } ?: return

        val starredArtistIds = starred.artist.orEmpty().map { it.id }.toSet()
        val starredAlbumIds = starred.album.orEmpty().map { it.id }.toSet()
        val starredSongsById = starred.song.orEmpty().associateBy { it.id }

        val existingArtists = artistDao.getAllForServer(host).associateBy { it.subsonicId }
        val artistWrites = starred.artist.orEmpty().mapNotNull { dto ->
            val existing = existingArtists[dto.id]
            when {
                existing == null -> dto.toEntity(host).copy(isStarred = true)
                !existing.isStarred -> existing.copy(isStarred = true)
                else -> null
            }
        }
        val newlyUnstarredArtists = existingArtists.values.filter { it.isStarred && it.subsonicId !in starredArtistIds }
            .map { it.copy(isStarred = false) }
        if (artistWrites.isNotEmpty() || newlyUnstarredArtists.isNotEmpty()) {
            artistDao.upsertAll(artistWrites + newlyUnstarredArtists)
        }

        val existingAlbums = albumDao.getAllForServer(host).associateBy { it.subsonicId }
        val albumWrites = starred.album.orEmpty().mapNotNull { dto ->
            val existing = existingAlbums[dto.id]
            when {
                existing == null -> dto.toLightweightEntity(host).copy(isStarred = true)
                !existing.isStarred -> existing.copy(isStarred = true)
                else -> null
            }
        }
        val newlyUnstarredAlbums = existingAlbums.values.filter { it.isStarred && it.subsonicId !in starredAlbumIds }
            .map { it.copy(isStarred = false) }
        if (albumWrites.isNotEmpty() || newlyUnstarredAlbums.isNotEmpty()) {
            albumDao.upsertAll(albumWrites + newlyUnstarredAlbums)
        }

        val existingTracks = trackDao.getAllForServer(host).associateBy { it.subsonicId }
        val trackWrites = starredSongsById.values.mapNotNull { dto ->
            val starredAt = parseIso8601ToEpochMillis(dto.starred)
            val existing = existingTracks[dto.id]
            when {
                existing == null -> dto.toStandaloneEntity(host).copy(isStarred = true, starredAt = starredAt)
                !existing.isStarred || existing.starredAt != starredAt -> existing.copy(isStarred = true, starredAt = starredAt)
                else -> null
            }
        }
        val newlyUnstarredTracks = existingTracks.values.filter { it.isStarred && it.subsonicId !in starredSongsById.keys }
            .map { it.copy(isStarred = false, starredAt = null) }
        if (trackWrites.isNotEmpty() || newlyUnstarredTracks.isNotEmpty()) {
            trackDao.upsertAll(trackWrites + newlyUnstarredTracks)
        }
    }

    private suspend fun syncRadioStations(config: ServerConfigEntity) {
        val host = config.host
        val stations = try {
            api.get(url(config, "getInternetRadioStations")).subsonicResponse?.internetRadioStations?.internetRadioStation.orEmpty()
        } catch (e: Exception) {
            return
        }
        radioStationDao.deleteByServer(host)
        radioStationDao.upsertAll(stations.map { it.toEntity(host) })
    }

    /**
     * Full delete + reinsert of every playlist for this host, preserving isFullyDownloaded /
     * isPinned / rulesJSON / lastPlayedAt from [preserved] (keyed by subsonicId) — mirrors the iOS engine's
     * syncPlaylistsForServer snapshot-then-replace pattern, reused by all three sync tiers.
     * Skips the getPlaylist round-trip when Navidrome's `changed` timestamp matches what's
     * already stored (nothing could have changed), same optimization as the iOS engine.
     */
    private suspend fun syncPlaylistsForHost(config: ServerConfigEntity, preserved: Map<String, PlaylistEntity>) {
        val host = config.host
        val snapshots = preserved.ifEmpty { playlistDao.getAllForServer(host).associateBy { it.subsonicId } }

        val playlists = try {
            api.get(url(config, "getPlaylists")).subsonicResponse?.playlists?.playlist.orEmpty()
        } catch (e: Exception) {
            return
        }

        playlistDao.deleteByServer(host)

        val entities = playlists.map { playlist ->
            val snap = snapshots[playlist.id]
            val unchanged = snap != null && playlist.changed != null && playlist.changed == snap.changedDate
            val trackIds = if (unchanged) {
                snap.trackIds
            } else {
                try {
                    api.get(url(config, "getPlaylist", mapOf("id" to playlist.id)))
                        .subsonicResponse?.playlist?.entry?.map { it.id }.orEmpty()
                } catch (e: Exception) {
                    snap?.trackIds.orEmpty()
                }
            }
            val isSmart = config.serverTypeRaw == ServerType.NAVIDROME.raw && playlist.readonly == true
            PlaylistEntity(
                id = compositeId(host, playlist.id),
                subsonicId = playlist.id,
                serverHost = host,
                name = playlist.name,
                songCount = playlist.songCount,
                duration = playlist.duration,
                coverArt = playlist.coverArt,
                owner = playlist.owner,
                publicStatus = playlist.public,
                changedDate = playlist.changed,
                trackIds = trackIds,
                isFullyDownloaded = snap?.isFullyDownloaded ?: false,
                isPinned = snap?.isPinned ?: false,
                isSmartPlaylist = isSmart,
                sortField = snap?.sortField ?: PlaylistSortField.CUSTOM,
                isSortReversed = snap?.isSortReversed ?: false,
                rulesJSON = snap?.rulesJSON,
                lastPlayedAt = snap?.lastPlayedAt
            )
        }
        playlistDao.upsertAll(entities)
    }
}
