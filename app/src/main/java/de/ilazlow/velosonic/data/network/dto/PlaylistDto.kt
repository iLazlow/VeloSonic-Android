package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val owner: String? = null,
    val public: Boolean? = null,
    val changed: String? = null,
    val entry: List<TrackDto>? = null,
    /** OpenSubsonic extension — true for Navidrome smart playlists (always read-only). */
    val readonly: Boolean? = null
)

@Serializable
data class PlaylistsNodeDto(
    val playlist: List<PlaylistDto>? = null
)
