package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local-only display ordering for a playlist's track list — never synced; the server's
 *  sequential order (trackIds) always remains the actual playback order. */
enum class PlaylistSortField {
    CUSTOM, TITLE, DURATION
}

/** Mirrors SDPlaylist. `id` is composite: "{serverHost}_{subsonicId}". */
@Entity(
    tableName = "playlists",
    indices = [Index("serverHost")]
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val subsonicId: String,
    val serverHost: String,
    val name: String,
    val songCount: Int,
    val duration: Int,
    val coverArt: String? = null,
    val owner: String? = null,
    val publicStatus: Boolean? = null,
    val changedDate: String? = null,
    /** Raw Navidrome track ids, same server, in server-defined order. */
    val trackIds: List<String> = emptyList(),
    val isFullyDownloaded: Boolean = false,
    val isPinned: Boolean = false,
    val isSmartPlaylist: Boolean = false,
    val sortField: PlaylistSortField = PlaylistSortField.CUSTOM,
    val isSortReversed: Boolean = false,
    /** Navidrome smart-playlist criteria JSON (smart playlists only). */
    val rulesJSON: String? = null,
    /** Wall-clock epoch millis of the last time playback was started from this playlist — local
     *  only, preserved across sync like isPinned/sortField (see SyncEngine.syncPlaylistsForHost).
     *  Backs the Home screen's recently-played-playlists row (mirrors iOS's RecentlyPlayedPlaylists). */
    val lastPlayedAt: Long? = null
)
