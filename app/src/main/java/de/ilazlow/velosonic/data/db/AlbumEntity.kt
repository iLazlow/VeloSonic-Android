package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mirrors SDAlbum. `id` is composite: "{serverHost}_{subsonicId}". */
@Entity(
    tableName = "albums",
    indices = [Index("serverHost"), Index("artistCompositeId")]
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val subsonicId: String,
    val serverHost: String,
    val name: String,
    val artistName: String,
    /** Raw Navidrome artist id (for API calls). */
    val artistId: String? = null,
    /** Composite id matching ArtistEntity.id — not a Room @ForeignKey (artist may sync after
     *  its albums in some code paths), just an indexed lookup column. */
    val artistCompositeId: String? = null,
    /** Raw Navidrome entity id for getCoverArtURL. */
    val coverArt: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val date: String? = null,
    val isStarred: Boolean = false,

    // Extended metadata
    val sortName: String? = null,
    val musicBrainzId: String? = null,
    val displayArtist: String? = null,
    val played: String? = null,
    val playCount: Int? = null,
    val created: String? = null,
    val userRating: Int? = null,
    val starredAt: String? = null,
    val songCount: Int? = null,
    val albumDuration: Int? = null,
    val compilation: Boolean? = null,
    val moods: List<String>? = null,
    val releaseTypes: List<String>? = null,
    val genresList: List<String>? = null,
    val artistsList: List<String>? = null
)
