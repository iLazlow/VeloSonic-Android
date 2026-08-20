package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubsonicArtistDto(
    val id: String,
    val name: String,
    val albumCount: Int? = null
)

@Serializable
data class ArtistDetailDto(
    val id: String,
    val name: String,
    val album: List<AlbumDto>? = null,
    val starred: String? = null
)

@Serializable
data class SimilarArtistDto(
    val id: String,
    val name: String,
    val coverArt: String? = null
)

@Serializable
data class ArtistInfoNodeDto(
    val biography: String? = null,
    val lastFmUrl: String? = null,
    val musicBrainzId: String? = null,
    val similarArtist: List<SimilarArtistDto>? = null
)

@Serializable
data class ArtistIndexDto(
    val name: String,
    val artist: List<SubsonicArtistDto>? = null
)

@Serializable
data class ArtistsNodeDto(
    val index: List<ArtistIndexDto>? = null
)

@Serializable
data class ResponseDataIndexesDto(
    val artists: ArtistsNodeDto? = null
)

@Serializable
data class SubsonicIndexesResponseDto(
    @kotlinx.serialization.SerialName("subsonic-response")
    val subsonicResponse: ResponseDataIndexesDto? = null
)
