package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors SDTrack. `id` is composite: "{serverHost}_{subsonicId}". Does not port
 * `appleArtworkUrl` (animated-lock-screen-artwork is an explicit, permanent iOS-only gap —
 * see the port plan's won't-port list).
 */
@Entity(
    tableName = "tracks",
    indices = [Index("serverHost"), Index("albumCompositeId")]
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val subsonicId: String,
    val serverHost: String,
    val title: String,
    val artistName: String,
    val duration: Int,
    val trackNumber: Int? = null,
    /** Raw Navidrome album id (used for cover art fallback). */
    val albumId: String,
    /** Composite id matching AlbumEntity.id — indexed lookup, not a strict FK (see AlbumEntity). */
    val albumCompositeId: String? = null,
    val albumName: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val suffix: String? = null,
    val isrc: String? = null,
    val explicitStatus: String? = null,
    val isStarred: Boolean = false,
    val starredAt: Long? = null,

    // Extended metadata (synced for offline availability)
    val parent: String? = null,
    val genre: String? = null,
    val genresList: List<String>? = null,
    val year: Int? = null,
    val contentType: String? = null,
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val fileSize: Long? = null,
    val filePath: String? = null,
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
    /** Parallel to artistIdsList — from participants.artist (multi-artist display). */
    val artistsList: List<String>? = null,
    val artistIdsList: List<String>? = null,

    // Offline lyrics cache, populated by the lyrics sync job (Phase 9) — not set at creation.
    val plainLyrics: String? = null,
    /** Raw LRC text ("[mm:ss.xx]text" per line). */
    val syncedLyricsLRC: String? = null,
    /** Set once a lyrics lookup has been attempted, so re-syncs don't re-query tracks with no
     *  lyrics on the server. Cleared by a full resync. */
    val lyricsCheckedAt: Long? = null
) {
    /** Reconstructs per-artist (raw id, name) pairs for multi-artist display — mirrors iOS's
     *  `SDTrack.artistEntries` (there parsed from pipe-joined strings; here the lists are native).
     *  Falls back to a single [artistId]/[artistName] entry when the parallel lists are absent or
     *  mismatched in length. */
    fun artistEntries(): List<ArtistEntry> {
        val names = artistsList
        val ids = artistIdsList
        if (names != null && ids != null && names.isNotEmpty() && names.size == ids.size) {
            return names.zip(ids).map { (name, id) -> ArtistEntry(id, name) }
        }
        return listOf(ArtistEntry(artistId.orEmpty(), artistName))
    }
}

/** One artist credited on a track — [id] is the raw (non-composite) Navidrome artist id, empty
 *  if unknown (e.g. a legacy row synced before artist ids were tracked). */
data class ArtistEntry(val id: String, val name: String)
