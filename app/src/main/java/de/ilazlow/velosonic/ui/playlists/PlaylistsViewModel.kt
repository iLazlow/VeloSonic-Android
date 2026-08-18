package de.ilazlow.velosonic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.datastore.PlaylistFilterMode
import de.ilazlow.velosonic.data.datastore.PlaylistSortOrder
import de.ilazlow.velosonic.data.datastore.PlaylistViewMode
import de.ilazlow.velosonic.data.datastore.PlaylistViewSettings
import de.ilazlow.velosonic.data.datastore.PlaylistViewSettingsStore
import de.ilazlow.velosonic.data.datastore.ServerOrderStore
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.NavidromeJwtClient
import de.ilazlow.velosonic.data.playlist.PlaylistArtworkUploader
import de.ilazlow.velosonic.data.playlist.PlaylistSubsonicClient
import de.ilazlow.velosonic.data.sync.compositeId
import de.ilazlow.velosonic.domain.isNavidrome
import de.ilazlow.velosonic.domain.supportsPlaylistArtworkUpload
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mirrors iOS PlaylistsView's per-row artwork badge (`assessDownloadStatus`): every track
 *  downloaded vs. some vs. none. */
enum class PlaylistDownloadStatus { NONE, DOWNLOADING, DOWNLOADED }

/** Mirrors iOS's `PlaylistsViewModel.ServerGroup` — one visible server's own playlists split from
 *  ones shared with it, used to render a per-server section pair in the list. */
data class ServerPlaylistGroup(
    val server: ServerConfigEntity,
    val mine: List<PlaylistEntity>,
    val shared: List<PlaylistEntity>
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val serverRepository: ServerRepository,
    private val serverOrderStore: ServerOrderStore,
    private val playlistSubsonicClient: PlaylistSubsonicClient,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val downloadRepository: DownloadRepository,
    private val playlistViewSettingsStore: PlaylistViewSettingsStore,
    private val playlistArtworkUploader: PlaylistArtworkUploader,
    private val navidromeJwtClient: NavidromeJwtClient
) : ViewModel() {
    val viewSettings: StateFlow<PlaylistViewSettings> = playlistViewSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistViewSettings())

    val downloads: StateFlow<Map<String, Download>> = downloadRepository.downloads

    private val rawPlaylists = libraryRepository.observePlaylists()

    /** Every distinct owner across every visible playlist, for the filter menu's owner picker —
     *  mirrors `viewModel.uniqueOwners(from: visiblePlaylists)`: always computed from the
     *  unfiltered list, so the menu's own option set doesn't shrink once a filter is applied. */
    val uniqueOwners: StateFlow<List<String>> = rawPlaylists
        .map { list -> list.mapNotNull { it.owner }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pinned first, then by [PlaylistViewSettings.sortOrder]/[PlaylistViewSettings.sortAscending]
     *  — a direct port of `PlaylistsViewModel.process`'s filter+sort block, including its two more
     *  surprising sort fields: dateCreated has no real creation-timestamp field to sort by
     *  (Subsonic doesn't expose one), so like iOS it sorts by subsonicId as a proxy; lastModified
     *  compares [PlaylistEntity.changedDate] with the "ascending" direction already inverted
     *  (newest first when sortAscending is true), which is iOS's own behavior, kept verbatim for
     *  parity. Filtering (downloaded-only / by owner) is applied before sorting, same as iOS. */
    val playlists: StateFlow<List<PlaylistEntity>> = combine(
        rawPlaylists, viewSettings, downloads
    ) { list, settings, downloads -> filterAndSort(list, settings, downloads) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setViewMode(mode: PlaylistViewMode) {
        viewModelScope.launch { playlistViewSettingsStore.setViewMode(mode) }
    }

    fun selectSortOrder(order: PlaylistSortOrder) {
        viewModelScope.launch { playlistViewSettingsStore.selectSortOrder(order) }
    }

    fun setFilterMode(mode: PlaylistFilterMode) {
        viewModelScope.launch { playlistViewSettingsStore.setFilterMode(mode) }
    }

    /** Clears the owner filter outright — the menu's "All Owners" row, as opposed to
     *  [selectOwnerFilter] which toggles a specific owner on/off. */
    fun clearOwnerFilter() {
        viewModelScope.launch { playlistViewSettingsStore.setOwnerFilter(null) }
    }

    /** Tapping the already-selected owner clears the filter; tapping a different one switches to
     *  it — mirrors `ownerFilter = (ownerFilter == owner) ? nil : owner`. */
    fun selectOwnerFilter(owner: String) {
        viewModelScope.launch {
            val current = viewSettings.value.ownerFilter
            playlistViewSettingsStore.setOwnerFilter(if (current == owner) null else owner)
        }
    }

    private fun filterAndSort(
        list: List<PlaylistEntity>,
        settings: PlaylistViewSettings,
        downloads: Map<String, Download>
    ): List<PlaylistEntity> {
        var result = list
        if (settings.filterMode == PlaylistFilterMode.DOWNLOADED) {
            result = result.filter { downloadStatus(it, downloads) == PlaylistDownloadStatus.DOWNLOADED }
        }
        settings.ownerFilter?.let { owner -> result = result.filter { it.owner == owner } }
        return sortPlaylists(result, settings)
    }

    private fun sortPlaylists(list: List<PlaylistEntity>, settings: PlaylistViewSettings): List<PlaylistEntity> {
        fun lessThan(a: PlaylistEntity, b: PlaylistEntity): Boolean = when (settings.sortOrder) {
            PlaylistSortOrder.NAME -> a.name.compareTo(b.name, ignoreCase = true) < 0
            PlaylistSortOrder.DATE_CREATED -> a.subsonicId < b.subsonicId
            PlaylistSortOrder.LAST_MODIFIED -> (a.changedDate ?: "") > (b.changedDate ?: "")
            PlaylistSortOrder.DURATION -> a.duration < b.duration
            PlaylistSortOrder.SONG_COUNT -> a.songCount < b.songCount
        }
        val comparator = Comparator<PlaylistEntity> { a, b ->
            if (a.isPinned != b.isPinned) return@Comparator if (a.isPinned) -1 else 1
            val aFirst = if (settings.sortAscending) lessThan(a, b) else lessThan(b, a)
            val bFirst = if (settings.sortAscending) lessThan(b, a) else lessThan(a, b)
            when {
                aFirst -> -1
                bFirst -> 1
                else -> 0
            }
        }
        return list.sortedWith(comparator)
    }

    fun togglePin(playlist: PlaylistEntity) {
        viewModelScope.launch { libraryRepository.upsertPlaylist(playlist.copy(isPinned = !playlist.isPinned)) }
    }

    fun downloadStatus(playlist: PlaylistEntity, downloads: Map<String, Download>): PlaylistDownloadStatus {
        if (playlist.trackIds.isEmpty()) return PlaylistDownloadStatus.NONE
        val downloadedCount = playlist.trackIds.count { downloads[compositeId(playlist.serverHost, it)]?.state == Download.STATE_COMPLETED }
        return when {
            downloadedCount == playlist.trackIds.size -> PlaylistDownloadStatus.DOWNLOADED
            downloadedCount > 0 -> PlaylistDownloadStatus.DOWNLOADING
            else -> PlaylistDownloadStatus.NONE
        }
    }

    /** Servers included in combined views, in the user's chosen order — mirrors
     *  `[ServerConfig].visible`: falls back to every server if the user has hidden all of them,
     *  so this list is never permanently empty. */
    val visibleServers: StateFlow<List<ServerConfigEntity>> = combine(
        serverRepository.observeServers(), serverOrderStore.order
    ) { configs, order ->
        val visible = configs.filter { it.isVisible }.ifEmpty { configs }
        serverOrderStore.applyOrder(visible, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun coverArtUrl(serverHost: String, coverArt: String?, size: Int = 300): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArt, size)

    /** A playlist with no reported `owner` (some servers omit it) is treated as mine — mirrors
     *  iOS's fallback of showing "System" rather than mis-bucketing it as shared. */
    fun isOwnedByMe(playlist: PlaylistEntity): Boolean {
        val owner = playlist.owner ?: return true
        return playlistSubsonicClient.configFor(playlist.serverHost)?.username == owner
    }

    /** One section-pair per visible server that actually has playlists — mirrors
     *  `PlaylistsViewModel.groupedByServer`, only used once more than one server is visible. */
    fun groupedByServer(playlists: List<PlaylistEntity>, servers: List<ServerConfigEntity>): List<ServerPlaylistGroup> =
        servers.mapNotNull { server ->
            val serverPlaylists = playlists.filter { it.serverHost == server.host }
            if (serverPlaylists.isEmpty()) return@mapNotNull null
            ServerPlaylistGroup(
                server = server,
                mine = serverPlaylists.filter(::isOwnedByMe),
                shared = serverPlaylists.filterNot(::isOwnedByMe)
            )
        }

    fun configs(): List<ServerConfigEntity> = playlistSubsonicClient.allConfigs()

    /** [isSmartPlaylist]/[smartRulesJson] create the playlist via Navidrome's native API instead
     *  of classic Subsonic `createPlaylist` — the only way to set rules at creation time. Mirrors
     *  `CreatePlaylistSheet`'s onCreate branch. */
    fun createPlaylist(
        name: String,
        isPublic: Boolean,
        serverHost: String,
        isSmartPlaylist: Boolean = false,
        smartRulesJson: String? = null,
        onResult: (String?) -> Unit
    ) {
        val config = playlistSubsonicClient.configFor(serverHost)
        if (config == null) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            if (isSmartPlaylist && config.isNavidrome) {
                val newId = navidromeJwtClient.createNativeSmartPlaylist(config, name, isPublic, smartRulesJson)
                if (newId == null) {
                    onResult(null)
                    return@launch
                }
                val entity = PlaylistEntity(
                    id = compositeId(serverHost, newId),
                    subsonicId = newId,
                    serverHost = serverHost,
                    name = name,
                    songCount = 0,
                    duration = 0,
                    coverArt = null,
                    owner = config.username,
                    publicStatus = isPublic,
                    isSmartPlaylist = true,
                    rulesJSON = smartRulesJson
                )
                libraryRepository.upsertPlaylist(entity)
                onResult(entity.id)
                return@launch
            }
            val created = playlistSubsonicClient.createPlaylist(config, name)
            if (created == null) {
                onResult(null)
                return@launch
            }
            val entity = PlaylistEntity(
                id = compositeId(serverHost, created.id),
                subsonicId = created.id,
                serverHost = serverHost,
                name = created.name,
                songCount = created.songCount,
                duration = created.duration,
                coverArt = created.coverArt,
                owner = created.owner ?: config.username,
                publicStatus = created.public ?: isPublic,
                changedDate = created.changed
            )
            libraryRepository.upsertPlaylist(entity)
            onResult(entity.id)
        }
    }

    /** True whenever [playlist]'s server accepts a custom cover-art upload — gates the
     *  artwork-picker row in [de.ilazlow.velosonic.ui.playlists.CreateEditPlaylistSheet]. */
    fun supportsArtworkUpload(playlist: PlaylistEntity): Boolean =
        playlistSubsonicClient.configFor(playlist.serverHost)?.supportsPlaylistArtworkUpload ?: false

    /** True whenever [playlist] is a smart playlist on a Navidrome server — gates whether the
     *  edit sheet shows the rules editor at all (a non-Navidrome-hosted smart playlist, e.g. one
     *  synced before a server migration, has no rules endpoint to edit through). */
    fun isSmartPlaylistEditable(playlist: PlaylistEntity): Boolean =
        playlist.isSmartPlaylist && (playlistSubsonicClient.configFor(playlist.serverHost)?.isNavidrome ?: false)

    /** Lazily fetches [playlist]'s current rules when they weren't already synced locally — see
     *  [CreateEditPlaylistSheet]'s `fetchSmartRules` parameter. */
    suspend fun fetchSmartRules(playlist: PlaylistEntity): String? {
        val config = playlistSubsonicClient.configFor(playlist.serverHost) ?: return null
        return navidromeJwtClient.fetchNativePlaylistRules(config, playlist.subsonicId)
    }

    /** [newCoverBytes]/[newCoverMimeType] stay null on a plain rename — artwork is only ever
     *  touched when the user actually picked a new image in the edit sheet. A smart playlist's
     *  rename goes entirely through [NavidromeJwtClient.updateNativePlaylistRules] instead (one
     *  PUT sets name/public/rules together) — artwork upload doesn't apply there, matching iOS's
     *  mutually-exclusive `if isSmart {...} else {artwork}` edit-sheet branches. */
    fun renamePlaylist(
        playlist: PlaylistEntity,
        newName: String,
        isPublic: Boolean,
        newCoverBytes: ByteArray? = null,
        newCoverMimeType: String? = null,
        smartRulesJson: String? = null
    ) {
        val config = playlistSubsonicClient.configFor(playlist.serverHost) ?: return
        viewModelScope.launch {
            if (playlist.isSmartPlaylist && config.isNavidrome) {
                val ok = navidromeJwtClient.updateNativePlaylistRules(config, playlist.subsonicId, newName, isPublic, smartRulesJson)
                if (ok) libraryRepository.upsertPlaylist(playlist.copy(name = newName, publicStatus = isPublic, rulesJSON = smartRulesJson))
                return@launch
            }
            libraryRepository.upsertPlaylist(playlist.copy(name = newName, publicStatus = isPublic))
            playlistSubsonicClient.renamePlaylist(config, playlist.subsonicId, newName, isPublic)
            if (newCoverBytes != null && newCoverMimeType != null) {
                playlistArtworkUploader.upload(config, playlist, newCoverBytes, newCoverMimeType)
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        val config = playlistSubsonicClient.configFor(playlist.serverHost) ?: return
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlist.id)
            playlistSubsonicClient.deletePlaylist(config, playlist.subsonicId)
        }
    }
}
