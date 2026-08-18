package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One flat envelope carrying every possible endpoint payload as an optional field — mirrors the
 * iOS client's ResponseData 1:1 rather than splitting per-endpoint DTOs, since Subsonic wraps
 * every response the same way regardless of which fields are actually populated.
 */
@Serializable
data class ResponseDataDto(
    val status: String = "",
    /** OpenSubsonic extension: server type (e.g. "navidrome"). */
    val type: String? = null,
    /** OpenSubsonic extension: server software version. */
    val serverVersion: String? = null,
    val album: AlbumDetailDto? = null,
    val albumList2: AlbumListDto? = null,
    val searchResult3: SearchResult3NodeDto? = null,
    val artist: ArtistDetailDto? = null,
    val playlists: PlaylistsNodeDto? = null,
    val playlist: PlaylistDto? = null,
    @SerialName("artistInfo2")
    val artistInfo2: ArtistInfoNodeDto? = null,
    val topSongs: TopSongsNodeDto? = null,
    val similarSongs2: SimilarSongs2NodeDto? = null,
    val sonicSimilarTracks: SonicSimilarTracksNodeDto? = null,
    val randomSongs: RandomSongsNodeDto? = null,
    val playQueue: PlayQueueNodeDto? = null,
    val starred2: StarredNodeDto? = null,
    val shares: SharesNodeDto? = null,
    val song: TrackDto? = null,
    val lyricsList: LyricsListNodeDto? = null,
    val internetRadioStations: InternetRadioStationsNodeDto? = null,
    val user: SubsonicUserDto? = null,
    val scanStatus: ScanStatusDto? = null,
    val nowPlaying: NowPlayingNodeDto? = null
)

@Serializable
data class SubsonicResponseDto(
    @SerialName("subsonic-response")
    val subsonicResponse: ResponseDataDto? = null
)
