package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemGenreDto(
    val name: String
)

@Serializable
data class ParticipantEntryDto(
    val id: String,
    val name: String,
    val missing: Boolean? = null
)

@Serializable
data class TrackParticipantsDto(
    val artist: List<ParticipantEntryDto>? = null,
    val albumartist: List<ParticipantEntryDto>? = null
)

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val explicitStatus: String? = null,
    val suffix: String? = null,
    @Serializable(with = StringOrFirstOfArraySerializer::class)
    val isrc: String? = null,
    val starred: String? = null,
    val coverArt: String? = null,
    val parent: String? = null,
    val genre: String? = null,
    val genres: List<ItemGenreDto>? = null,
    val year: Int? = null,
    val contentType: String? = null,
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val size: Long? = null,
    val path: String? = null,
    val created: String? = null,
    val played: String? = null,
    val playCount: Int? = null,
    val userRating: Int? = null,
    val samplingRate: Int? = null,
    val channelCount: Int? = null,
    val comment: String? = null,
    val sortName: String? = null,
    val musicBrainzId: String? = null,
    val bpm: Int? = null,
    val discNumber: Int? = null,
    val displayArtist: String? = null,
    val displayAlbumArtist: String? = null,
    val displayComposer: String? = null,
    val participants: TrackParticipantsDto? = null,
    val artists: List<ParticipantEntryDto>? = null
) {
    val isExplicit: Boolean get() = explicitStatus == "explicit"

    /** Artist entries: participants.artist -> artists -> single-artist fallback. */
    val artistEntries: List<ParticipantEntryDto>
        get() {
            participants?.artist?.takeIf { it.isNotEmpty() }?.let { return it }
            artists?.takeIf { it.isNotEmpty() }?.let { return it }
            return listOf(ParticipantEntryDto(id = artistId ?: "", name = artist))
        }
}
