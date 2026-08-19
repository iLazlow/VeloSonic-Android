package de.ilazlow.velosonic.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.artist.ArtistSubsonicClient
import de.ilazlow.velosonic.data.artwork.AnimatedArtworkRepository
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import de.ilazlow.velosonic.data.datastore.KawarpSettings
import de.ilazlow.velosonic.data.datastore.KawarpSettingsStore
import de.ilazlow.velosonic.data.datastore.LyricsSettingsStore
import de.ilazlow.velosonic.data.datastore.PlaybackSettingsStore
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.ArtistEntry
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.lyrics.LyricsRepository
import de.ilazlow.velosonic.data.lyrics.LyricsUiState
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.dto.SimilarArtistDto
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.sync.toStandaloneEntity
import de.ilazlow.velosonic.domain.artistRouteId
import de.ilazlow.velosonic.domain.primaryArtistRouteId
import de.ilazlow.velosonic.domain.supportsSonicSimilarity
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private fun String.stripHtmlTags(): String = replace(Regex("<[^>]+>"), "")

private data class AboutData(
    val similarSongs: List<TrackEntity> = emptyList(),
    val similarArtists: List<SimilarArtistDto> = emptyList(),
    val artistBiography: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val subsonicClient: PlaybackSubsonicClient,
    private val artistSubsonicClient: ArtistSubsonicClient,
    private val lyricsRepository: LyricsRepository,
    private val animatedArtworkRepository: AnimatedArtworkRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackSettingsStore: PlaybackSettingsStore,
    private val libraryRepository: LibraryRepository,
    lyricsSettingsStore: LyricsSettingsStore,
    appearanceSettingsStore: AppearanceSettingsStore,
    kawarpSettingsStore: KawarpSettingsStore
) : ViewModel() {
    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    /** Mirrors iOS's `moreRadioStationsSection`: every other station on the same server as the one
     *  currently playing, all shown (no cap) — an empty list hides the whole section. */
    val otherRadioStations: StateFlow<List<RadioStationEntity>> = nowPlaying
        .flatMapLatest { np ->
            val current = np.radioStation
            if (current == null) {
                flowOf(emptyList())
            } else {
                libraryRepository.observeRadioStations()
                    .map { stations -> stations.filter { it.serverHost == current.serverHost && it.id != current.id } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playRadioStation(station: RadioStationEntity) = playbackController.playRadio(station)

    val downloads: StateFlow<Map<String, Download>> = downloadRepository.downloads

    val lyricsSparklesEnabled: StateFlow<Boolean> = lyricsSettingsStore.settings
        .map { it.radiantLyricsSparklesEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val animateWebpArtwork: StateFlow<Boolean> = appearanceSettingsStore.settings
        .map { it.animatedArtworksEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val kawarpSettings: StateFlow<KawarpSettings> = kawarpSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KawarpSettings())

    /** Resolved animated-video artwork for the currently-playing track (see
     *  [AnimatedArtworkRepository.resolve]) — null while unavailable/loading/disabled, in which
     *  case the player screen just shows the (possibly animated-WebP) static artwork instead. */
    val animatedVideoUri: StateFlow<String?> = nowPlaying
        .map { it.track }
        .distinctUntilChangedBy { it?.id }
        .flatMapLatest { track ->
            Log.d("PlayerVM", "animatedVideoUri: track changed to id=${track?.id} title=${track?.title}")
            if (track == null) return@flatMapLatest flowOf(null)
            flow<String?> {
                emit(null)
                emit(resolveAnimatedVideo(track))
            }
        }
        .onEach { Log.d("PlayerVM", "animatedVideoUri emit: $it") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun resolveAnimatedVideo(track: TrackEntity): String? = animatedArtworkRepository.resolve(
        serverHost = track.serverHost,
        coverArtId = track.coverArt,
        trackId = track.id,
        artist = track.artistName,
        album = track.albumName.orEmpty(),
        title = track.title
    )

    /** Refetched fresh (network sources first, local cache last) whenever the current track
     *  changes — mirrors LyricsManager.load()'s per-track cache key, just keyed by track id
     *  since there's no Radiant romanization variant here to fold into the key. */
    val lyrics: StateFlow<LyricsUiState> = nowPlaying
        .map { it.track }
        .distinctUntilChangedBy { it?.id }
        .flatMapLatest { track ->
            if (track == null) return@flatMapLatest flowOf<LyricsUiState>(LyricsUiState.Empty)
            flow {
                emit(LyricsUiState.Loading)
                val content = lyricsRepository.resolve(track)
                emit(if (content == null) LyricsUiState.Empty else LyricsUiState.Loaded(content))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUiState.Loading)

    /** Mirrors iOS's `PlayerViewModel.loadAboutData()` — one fetch per track change that backs
     *  all three "About" sections (Similar Songs, Similar Artists, artist bio) together, since
     *  the similar-artists/bio call is keyed by the SAME artist id regardless of which similar-
     *  songs tier produced the song list. Similar Songs itself uses the same tiered fallback
     *  ContinuousMixResolver uses for autoplay (sonic similarity first, gated by both server
     *  support AND the user's Sonic Similar Songs setting — see PlaybackSettingsScreen — else the
     *  plain Subsonic getSimilarSongs2). Local-audio-analysis and fully-offline tiers (iOS's
     *  priorities 2-4) aren't replicated here — [de.ilazlow.velosonic.playback.ContinuousMixResolver]
     *  already covers the on-device-analysis fallback for autoplay; duplicating that scoring
     *  purely for this read-only info panel isn't worth it. */
    private val aboutData: StateFlow<AboutData> = nowPlaying
        .map { it.track }
        .distinctUntilChangedBy { it?.id }
        .flatMapLatest { track ->
            if (track == null) return@flatMapLatest flowOf(AboutData())
            val config = subsonicClient.configFor(track.serverHost) ?: return@flatMapLatest flowOf(AboutData())
            flow {
                emit(AboutData())
                val sonicEnabled = playbackSettingsStore.settings.first().sonicSimilarSongsEnabled
                val songDtos = if (sonicEnabled && config.supportsSonicSimilarity) {
                    subsonicClient.sonicSimilarTracks(config, track.subsonicId, 15)
                        .ifEmpty { subsonicClient.similarSongs(config, track.subsonicId, 15) }
                } else {
                    subsonicClient.similarSongs(config, track.subsonicId, 15)
                }
                // .distinctBy: confirmed live against a real server — similarSongs/sonicSimilarTracks
                // can return the same track twice, which crashed this screen's "Similar Songs" row
                // (`itemsIndexed(songs, key = { _, song -> song.id })` requires unique keys).
                val songs = songDtos.map { it.toStandaloneEntity(track.serverHost) }.distinctBy { it.id }

                val rawArtistId = track.artistId
                var similarArtists = emptyList<SimilarArtistDto>()
                var biography: String? = null
                if (!rawArtistId.isNullOrEmpty()) {
                    val info = artistSubsonicClient.fetchArtistInfo(config, rawArtistId)
                    // Same duplicate-id issue as similarSongs above, for the "Similar Artists" row.
                    similarArtists = info?.similarArtist.orEmpty().distinctBy { it.id }
                    biography = info?.biography?.stripHtmlTags()?.trim()?.takeIf(String::isNotEmpty)
                }
                emit(AboutData(songs, similarArtists, biography))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AboutData())

    val similarSongs: StateFlow<List<TrackEntity>> = aboutData
        .map { it.similarSongs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val similarArtists: StateFlow<List<SimilarArtistDto>> = aboutData
        .map { it.similarArtists }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artistBiography: StateFlow<String?> = aboutData
        .map { it.artistBiography }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun skipToNext() = playbackController.skipToNext()
    fun skipToPrevious() = playbackController.skipToPrevious()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()
    fun toggleFavorite() = playbackController.toggleFavoriteForCurrentTrack()
    fun jumpTo(index: Int) = playbackController.jumpTo(index)

    fun seekToLyricLine(startMs: Int) = playbackController.seekTo(startMs.toLong())

    fun playSimilar(index: Int) {
        val list = similarSongs.value
        if (list.isNotEmpty()) playbackController.playQueue(list, index)
    }

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 800): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    /** Composite artist id (matching [de.ilazlow.velosonic.ui.navigation.ArtistDetailRoute]'s
     *  expected id space) for one of [TrackEntity.artistEntries] — null when that entry has no
     *  raw id at all (a legacy row synced before per-artist ids were tracked), matching iOS's own
     *  "only show a Go to Artist action when an id actually resolved" behavior. Delegates to the
     *  shared [de.ilazlow.velosonic.domain.artistRouteId] extension so every track-row screen's
     *  menu resolves artist navigation identically, not just this one. */
    fun artistRouteId(track: TrackEntity, entry: ArtistEntry): String? = track.artistRouteId(entry)

    /** The "About" section's artist card links to the track's single primary artist (whichever
     *  id [aboutData]'s biography/similar-artists fetch was actually keyed by), not necessarily
     *  the first of [TrackEntity.artistEntries] — same distinction iOS's `artistCard` draws
     *  between `track.displayAlbumArtist`/`track.artistId` and the title row's per-entry list. */
    fun primaryArtistRouteId(track: TrackEntity): String? = track.primaryArtistRouteId()

    fun primaryArtistDisplayName(track: TrackEntity): String =
        track.displayAlbumArtist?.takeIf(String::isNotEmpty) ?: track.artistName

    fun primaryArtistAvatarUrl(track: TrackEntity, size: Int = 300): String? =
        track.artistId?.let { coverArtUrlResolver.urlFor(track.serverHost, it, size) }

    fun similarArtistAvatarUrl(track: TrackEntity, similarArtistId: String, size: Int = 200): String? =
        coverArtUrlResolver.urlFor(track.serverHost, similarArtistId, size)

    fun isDownloaded(track: TrackEntity): Boolean = downloadRepository.isDownloaded(track.id)

    fun downloadCurrentTrack() {
        val track = nowPlaying.value.track ?: return
        downloadRepository.downloadTrack(track, partOfBulkGroup = false)
    }
}
