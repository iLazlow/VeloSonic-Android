package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.likedSongsDataStore by preferencesDataStore(name = "liked_songs_settings")

enum class LikedSongsSortOrder { DATE_LIKED, TITLE, ARTIST, DURATION }

data class LikedSongsSettings(
    val sortOrder: LikedSongsSortOrder = LikedSongsSortOrder.DATE_LIKED,
    val sortAscending: Boolean = false,
    val excludedHosts: Set<String> = emptySet()
)

/** Backs the "Liked Songs" pseudo-playlist's own sort menu and per-server include/exclude filter —
 *  both are deliberate Android-only additions beyond iOS parity: iOS hard-codes newest-liked-first
 *  with no user-facing sort control, and has no per-server filter specific to Liked Songs (only
 *  the app-wide visible-servers toggle every combined screen already respects). */
@Singleton
class LikedSongsSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val EXCLUDED_HOSTS = stringSetPreferencesKey("excluded_hosts")
    }

    val settings: Flow<LikedSongsSettings> = context.likedSongsDataStore.data.map { prefs ->
        LikedSongsSettings(
            sortOrder = prefs[Keys.SORT_ORDER]?.let { runCatching { LikedSongsSortOrder.valueOf(it) }.getOrNull() }
                ?: LikedSongsSortOrder.DATE_LIKED,
            sortAscending = prefs[Keys.SORT_ASCENDING] ?: false,
            excludedHosts = prefs[Keys.EXCLUDED_HOSTS] ?: emptySet()
        )
    }

    /** Selecting the already-active sort order flips its direction; selecting a different one
     *  switches to it — same interaction as [PlaylistViewSettingsStore.selectSortOrder]. Defaults
     *  to descending (newest-liked-first for [LikedSongsSortOrder.DATE_LIKED]) on a fresh
     *  selection, matching iOS's fixed default for this screen. */
    suspend fun selectSortOrder(order: LikedSongsSortOrder) {
        context.likedSongsDataStore.edit { prefs ->
            val current = prefs[Keys.SORT_ORDER]?.let { runCatching { LikedSongsSortOrder.valueOf(it) }.getOrNull() }
            if (current == order) {
                prefs[Keys.SORT_ASCENDING] = !(prefs[Keys.SORT_ASCENDING] ?: false)
            } else {
                prefs[Keys.SORT_ORDER] = order.name
                prefs[Keys.SORT_ASCENDING] = false
            }
        }
    }

    suspend fun toggleExcludedHost(host: String) {
        context.likedSongsDataStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_HOSTS] ?: emptySet()
            prefs[Keys.EXCLUDED_HOSTS] = if (host in current) current - host else current + host
        }
    }
}
