package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubsonicUserDto(
    val username: String,
    val adminRole: Boolean? = null
)

/**
 * Server-side library scan status (startScan/getScanStatus) — rescans files on the server's
 * disk, distinct from this app's own local sync. `folderCount`/`lastScan` are Navidrome
 * extensions beyond base OpenSubsonic, both optional so decoding still succeeds without them.
 */
@Serializable
data class ScanStatusDto(
    val scanning: Boolean = false,
    val count: Int? = null,
    val folderCount: Int? = null,
    val lastScan: String? = null
)

@Serializable
data class NowPlayingEntryDto(
    @kotlinx.serialization.SerialName("id")
    val songId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Int? = null,
    val username: String,
    val minutesAgo: Int = 0,
    val playerId: Int? = null,
    val playerName: String? = null,
    val state: String? = null,
    val positionMs: Long? = null,
    val playbackRate: Double? = null
)

@Serializable
data class NowPlayingNodeDto(
    val entry: List<NowPlayingEntryDto>? = null
)

@Serializable
data class StarredNodeDto(
    val artist: List<SubsonicArtistDto>? = null,
    val song: List<TrackDto>? = null,
    val album: List<AlbumDto>? = null
)

@Serializable
data class TopSongsNodeDto(
    val song: List<TrackDto>? = null
)

@Serializable
data class SimilarSongs2NodeDto(
    val song: List<TrackDto>? = null
)

@Serializable
data class SonicMatchDto(
    val entry: TrackDto,
    val similarity: Double = 0.0
)

@Serializable
data class SonicSimilarTracksNodeDto(
    val sonicMatch: List<SonicMatchDto>? = null
)

@Serializable
data class RandomSongsNodeDto(
    val song: List<TrackDto>? = null
)

@Serializable
data class SearchResult3NodeDto(
    val song: List<TrackDto>? = null,
    val album: List<AlbumDto>? = null,
    val artist: List<SubsonicArtistDto>? = null
)
