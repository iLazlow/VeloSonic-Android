package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

/** Lightweight album entry, e.g. from getAlbumList2/search3/starred2. Same AlbumID3 schema as
 *  [AlbumDetailDto] carries `played`/`playCount`/`starred` inline too — kept here so a cheap
 *  `getAlbumList2?type=recent/frequent` refetch can update those fields on already-known albums
 *  without needing a full per-album [AlbumDetailDto] fetch (see SyncEngine's
 *  `refreshRecentPlayFreshness`). */
@Serializable
data class AlbumDto(
    val id: String,
    val artistId: String? = null,
    val title: String? = null,
    val name: String? = null,
    val artist: String = "",
    val coverArt: String? = null,
    val explicitStatus: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val date: String? = null,
    val played: String? = null,
    val playCount: Int? = null,
    val starred: String? = null
) {
    /** Server inconsistency workaround: some servers send `title`, others `name`. */
    val albumTitle: String get() = title ?: name ?: "Unknown Album"

    val isExplicit: Boolean get() = explicitStatus == "explicit"
    val isClean: Boolean get() = explicitStatus == "clean"
}

@Serializable
data class AlbumArtistEntryDto(
    val id: String,
    val name: String
)

/** Full album detail, e.g. from getAlbum — includes the track listing and extended metadata. */
@Serializable
data class AlbumDetailDto(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val song: List<TrackDto>? = null,
    val year: Int? = null,
    val date: String? = null,
    val genre: String? = null,
    val sortName: String? = null,
    val musicBrainzId: String? = null,
    val displayArtist: String? = null,
    val played: String? = null,
    val playCount: Int? = null,
    val created: String? = null,
    val userRating: Int? = null,
    val starred: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val compilation: Boolean? = null,
    val moods: List<String>? = null,
    val releaseTypes: List<String>? = null,
    val genres: List<ItemGenreDto>? = null,
    val artists: List<AlbumArtistEntryDto>? = null
)

@Serializable
data class AlbumListDto(
    val album: List<AlbumDto>? = null
)
