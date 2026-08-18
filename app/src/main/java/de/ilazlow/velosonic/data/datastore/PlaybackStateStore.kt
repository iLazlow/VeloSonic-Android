package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackStateDataStore by preferencesDataStore(name = "playback_state")

data class PlaybackState(
    val queueTrackIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val albumId: String? = null,
    val playlistId: String? = null
)

/**
 * Play queue and playback position, restored on next launch — mirrors the iOS client's
 * UserDefaults keys (`lastPlaybackQueue`/`lastPlaybackIndex`/`lastPlaybackPosition`/
 * `lastPlaybackAlbum`/`lastPlaybackPlaylistId`). This is not synced to the server; it's purely
 * a local "resume where I left off" cache (Phase 5 also wires up the optional server-side
 * savePlayQueue/getPlayQueue mirror separately).
 */
@Singleton
class PlaybackStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val QUEUE_TRACK_IDS = stringPreferencesKey("queue_track_ids")
        val CURRENT_INDEX = intPreferencesKey("current_index")
        val POSITION_MS = longPreferencesKey("position_ms")
        val ALBUM_ID = stringPreferencesKey("album_id")
        val PLAYLIST_ID = stringPreferencesKey("playlist_id")
    }

    val state: Flow<PlaybackState> = context.playbackStateDataStore.data.map { prefs ->
        prefs.toPlaybackState()
    }

    suspend fun current(): PlaybackState = context.playbackStateDataStore.data.first().toPlaybackState()

    suspend fun save(state: PlaybackState) {
        context.playbackStateDataStore.edit { prefs ->
            prefs[Keys.QUEUE_TRACK_IDS] = state.queueTrackIds.joinToString(DELIMITER)
            prefs[Keys.CURRENT_INDEX] = state.currentIndex
            prefs[Keys.POSITION_MS] = state.positionMs
            state.albumId?.let { prefs[Keys.ALBUM_ID] = it } ?: prefs.remove(Keys.ALBUM_ID)
            state.playlistId?.let { prefs[Keys.PLAYLIST_ID] = it } ?: prefs.remove(Keys.PLAYLIST_ID)
        }
    }

    suspend fun clear() {
        context.playbackStateDataStore.edit { it.clear() }
    }

    private fun Preferences.toPlaybackState(): PlaybackState = PlaybackState(
        queueTrackIds = this[Keys.QUEUE_TRACK_IDS]?.let { if (it.isEmpty()) emptyList() else it.split(DELIMITER) }
            ?: emptyList(),
        currentIndex = this[Keys.CURRENT_INDEX] ?: -1,
        positionMs = this[Keys.POSITION_MS] ?: 0L,
        albumId = this[Keys.ALBUM_ID],
        playlistId = this[Keys.PLAYLIST_ID]
    )

    private companion object {
        // Unit Separator (code point 31) — same convention as the Room List<String> converters.
        // Built from its code point rather than a literal escape so the exact character is
        // never ambiguous in source.
        val DELIMITER = 31.toChar().toString()
    }
}
