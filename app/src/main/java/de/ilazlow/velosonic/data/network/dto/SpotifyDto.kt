package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyAuthTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
data class SpotifyImageDto(val url: String)

@Serializable
data class SpotifyPlaylistOwnerDto(@SerialName("display_name") val displayName: String? = null)

@Serializable
data class SpotifyPlaylistTracksSummaryDto(val total: Int? = null)

@Serializable
data class SpotifyPlaylistDto(
    val name: String? = null,
    val description: String? = null,
    val images: List<SpotifyImageDto>? = null,
    val tracks: SpotifyPlaylistTracksSummaryDto? = null,
    val owner: SpotifyPlaylistOwnerDto? = null
)

@Serializable
data class SpotifyArtistRefDto(val name: String)

@Serializable
data class SpotifyAlbumRefDto(val name: String? = null, val images: List<SpotifyImageDto>? = null)

@Serializable
data class SpotifyTrackRefDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    val artists: List<SpotifyArtistRefDto>? = null,
    val album: SpotifyAlbumRefDto? = null
)

@Serializable
data class SpotifyPlaylistTrackItemDto(val track: SpotifyTrackRefDto? = null)

@Serializable
data class SpotifyPlaylistTracksPageDto(
    val total: Int? = null,
    val next: String? = null,
    val items: List<SpotifyPlaylistTrackItemDto>? = null
)
