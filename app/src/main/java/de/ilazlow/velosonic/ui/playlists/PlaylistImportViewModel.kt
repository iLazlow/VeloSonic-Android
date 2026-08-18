package de.ilazlow.velosonic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.NavidromeJwtClient
import de.ilazlow.velosonic.data.network.dto.PlaylistDto
import de.ilazlow.velosonic.data.network.dto.TrackDto
import de.ilazlow.velosonic.data.playlist.ExportifyCsvParser
import de.ilazlow.velosonic.data.playlist.ImportPlaylistInfo
import de.ilazlow.velosonic.data.playlist.ImportSourceTrack
import de.ilazlow.velosonic.data.playlist.PlaylistImportException
import de.ilazlow.velosonic.data.playlist.PlaylistImportSource
import de.ilazlow.velosonic.data.playlist.PlaylistSubsonicClient
import de.ilazlow.velosonic.data.playlist.SpotifyClient
import de.ilazlow.velosonic.data.playlist.TrackMatcher
import de.ilazlow.velosonic.data.sync.SyncEngine
import de.ilazlow.velosonic.data.sync.compositeId
import de.ilazlow.velosonic.di.ApplicationScope
import de.ilazlow.velosonic.domain.supportsPlaylistArtworkUpload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

sealed class ImportPhase {
    data object Idle : ImportPhase()
    data object LoadingPlaylist : ImportPhase()
    data object MatchingTracks : ImportPhase()
    data object ReadyToImport : ImportPhase()
    data object Importing : ImportPhase()
    data object Done : ImportPhase()
    data class Failed(val message: String) : ImportPhase()
}

data class ImportTrackMatch(val sourceTrack: ImportSourceTrack, val matched: TrackDto? = null) {
    val isMatched: Boolean get() = matched != null
}

data class PendingCsv(val bytes: ByteArray, val filename: String, val derivedName: String)

/**
 * Orchestrates playlist import from Spotify (Client Credentials OAuth, public playlists only)
 * or an Exportify CSV export — mirrors iOS's `PlaylistImportViewModel`: load source → match every
 * track against a chosen server's library (see [TrackMatcher]) → create or update a playlist
 * there with whatever matched. Unmatched tracks are surfaced in [matchResults] but silently
 * excluded from the actual import — there's no manual-resolution UI, matching iOS.
 */
@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val spotifyClient: SpotifyClient,
    private val trackMatcher: TrackMatcher,
    private val playlistSubsonicClient: PlaylistSubsonicClient,
    private val navidromeJwtClient: NavidromeJwtClient,
    private val libraryRepository: LibraryRepository,
    private val syncEngine: SyncEngine,
    private val okHttpClient: OkHttpClient,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 120): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    private val _phase = MutableStateFlow<ImportPhase>(ImportPhase.Idle)
    val phase: StateFlow<ImportPhase> = _phase.asStateFlow()

    private val _urlText = MutableStateFlow("")
    val urlText: StateFlow<String> = _urlText.asStateFlow()

    private val _pendingCsv = MutableStateFlow<PendingCsv?>(null)
    val pendingCsv: StateFlow<PendingCsv?> = _pendingCsv.asStateFlow()

    private val _playlistInfo = MutableStateFlow<ImportPlaylistInfo?>(null)
    val playlistInfo: StateFlow<ImportPlaylistInfo?> = _playlistInfo.asStateFlow()

    private var sourceTracks: List<ImportSourceTrack> = emptyList()

    private val _matchResults = MutableStateFlow<List<ImportTrackMatch>>(emptyList())
    val matchResults: StateFlow<List<ImportTrackMatch>> = _matchResults.asStateFlow()

    private val _existingPlaylist = MutableStateFlow<PlaylistDto?>(null)
    val existingPlaylist: StateFlow<PlaylistDto?> = _existingPlaylist.asStateFlow()

    private val _selectedServerHost = MutableStateFlow<String?>(null)
    val selectedServerHost: StateFlow<String?> = _selectedServerHost.asStateFlow()

    val servers: List<ServerConfigEntity> = playlistSubsonicClient.allConfigs()

    /** Pure passthrough — lets the screen render the live "Spotify"/"CSV" badge without holding
     *  a reference to [SpotifyClient] itself. */
    fun detectSource(text: String): PlaylistImportSource? =
        if (_pendingCsv.value != null) PlaylistImportSource.CSV else spotifyClient.detectSource(text)

    fun onUrlTextChange(text: String) {
        _urlText.value = text
    }

    fun setCsvFile(bytes: ByteArray, filename: String) {
        _urlText.value = ""
        _pendingCsv.value = PendingCsv(bytes, filename, ExportifyCsvParser.playlistNameFromFilename(filename))
        resetLoadedState()
    }

    fun clearCsv() {
        _pendingCsv.value = null
        resetLoadedState()
    }

    private fun resetLoadedState() {
        _playlistInfo.value = null
        sourceTracks = emptyList()
        _matchResults.value = emptyList()
        _existingPlaylist.value = null
        _phase.value = ImportPhase.Idle
    }

    fun loadPlaylist() {
        if (_phase.value == ImportPhase.LoadingPlaylist || _phase.value == ImportPhase.MatchingTracks) return
        viewModelScope.launch {
            _phase.value = ImportPhase.LoadingPlaylist
            try {
                val csv = _pendingCsv.value
                val (info, tracks) = if (csv != null) {
                    val result = withContext(Dispatchers.Default) { ExportifyCsvParser.parse(csv.bytes, csv.derivedName) }
                    result.info to result.tracks
                } else {
                    val playlistId = spotifyClient.extractPlaylistId(_urlText.value) ?: throw PlaylistImportException.InvalidUrl()
                    spotifyClient.fetchPlaylistInfo(playlistId) to spotifyClient.fetchAllTracks(playlistId)
                }
                _playlistInfo.value = info
                sourceTracks = tracks
                _matchResults.value = tracks.map { ImportTrackMatch(it) }

                // Mirrors iOS: defaults to the first configured server, not necessarily whichever
                // server the Playlists screen happened to be showing when Import was opened.
                val defaultServer = servers.firstOrNull()
                _selectedServerHost.value = defaultServer?.host
                if (defaultServer != null) {
                    checkExistingPlaylist(defaultServer, info.name)
                    runMatching(defaultServer)
                } else {
                    _phase.value = ImportPhase.ReadyToImport
                }
            } catch (e: Exception) {
                _phase.value = ImportPhase.Failed(e.message ?: "Import failed.")
            }
        }
    }

    fun onServerChanged(host: String) {
        _selectedServerHost.value = host
        val config = playlistSubsonicClient.configFor(host) ?: return
        val info = _playlistInfo.value ?: return
        viewModelScope.launch {
            checkExistingPlaylist(config, info.name)
            runMatching(config)
        }
    }

    private suspend fun checkExistingPlaylist(config: ServerConfigEntity, name: String) {
        val existing = playlistSubsonicClient.fetchPlaylists(config).firstOrNull { it.name.equals(name, ignoreCase = true) }
        _existingPlaylist.value = existing?.let { playlistSubsonicClient.getPlaylistDetail(config, it.id) }
    }

    /** Batches of 10, matched concurrently within a batch (mirrors iOS) — applied to
     *  [matchResults] once at the end rather than per-batch, so the preview list doesn't
     *  re-render on every single match. */
    private suspend fun runMatching(config: ServerConfigEntity) {
        _phase.value = ImportPhase.MatchingTracks
        val tracks = sourceTracks
        if (tracks.isEmpty()) {
            _matchResults.value = emptyList()
            _phase.value = ImportPhase.ReadyToImport
            return
        }
        val resolved = mutableListOf<ImportTrackMatch>()
        for (batch in tracks.chunked(10)) {
            val matched = coroutineScope {
                batch.map { track -> async { trackMatcher.match(track, config) } }.awaitAll()
            }
            batch.forEachIndexed { i, track -> resolved += ImportTrackMatch(track, matched[i]) }
        }
        _matchResults.value = resolved
        _phase.value = ImportPhase.ReadyToImport
    }

    fun startImport() {
        val info = _playlistInfo.value ?: return
        val host = _selectedServerHost.value ?: return
        val config = playlistSubsonicClient.configFor(host) ?: return
        val orderedIds = _matchResults.value.mapNotNull { it.matched?.id }
        if (orderedIds.isEmpty()) return

        viewModelScope.launch {
            _phase.value = ImportPhase.Importing
            val existingId = _existingPlaylist.value?.id
            val resultId = playlistSubsonicClient.importTracks(config, info.name, orderedIds, existingId)
            if (resultId == null) {
                _phase.value = ImportPhase.Failed("Import failed. Please try again.")
                return@launch
            }

            // Optimistic local row so the playlist shows up immediately, ahead of the background
            // sync below reconciling it with the server's own metadata.
            if (existingId != null) {
                libraryRepository.getPlaylistById(compositeId(host, existingId))?.let {
                    libraryRepository.upsertPlaylist(it.copy(songCount = orderedIds.size, trackIds = orderedIds))
                }
            } else {
                libraryRepository.upsertPlaylist(
                    PlaylistEntity(
                        id = compositeId(host, resultId),
                        subsonicId = resultId,
                        serverHost = host,
                        name = info.name,
                        songCount = orderedIds.size,
                        duration = 0,
                        owner = config.username,
                        publicStatus = false,
                        trackIds = orderedIds
                    )
                )
            }

            if (info.artworkUrl != null && config.supportsPlaylistArtworkUpload) {
                uploadArtwork(config, resultId, info.artworkUrl)
            }

            _phase.value = ImportPhase.Done

            // Just this server's playlists, in the background — the new/updated one appears
            // without waiting for the next periodic partial sync.
            appScope.launch { syncEngine.syncPlaylistsOnly(host) }
        }
    }

    private suspend fun uploadArtwork(config: ServerConfigEntity, playlistId: String, artworkUrl: String) {
        try {
            val bytes = withContext(Dispatchers.IO) {
                okHttpClient.newCall(Request.Builder().url(artworkUrl).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            } ?: return
            val mimeType = if (artworkUrl.substringAfterLast('.', "").lowercase() == "png") "image/png" else "image/jpeg"
            navidromeJwtClient.uploadPlaylistArtwork(config, playlistId, bytes, mimeType)
        } catch (e: Exception) {
            // Best-effort — a missing playlist image doesn't make the import a failure.
        }
    }
}
