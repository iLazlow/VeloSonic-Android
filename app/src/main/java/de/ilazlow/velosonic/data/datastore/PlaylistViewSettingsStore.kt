package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playlistViewDataStore by preferencesDataStore(name = "playlist_view_settings")

enum class PlaylistViewMode { LIST, GRID }

enum class PlaylistSortOrder { NAME, DATE_CREATED, LAST_MODIFIED, DURATION, SONG_COUNT }

enum class PlaylistFilterMode { ALL, DOWNLOADED }

data class PlaylistViewSettings(
    val viewMode: PlaylistViewMode = PlaylistViewMode.LIST,
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.NAME,
    val sortAscending: Boolean = true,
    val filterMode: PlaylistFilterMode = PlaylistFilterMode.ALL,
    val ownerFilter: String? = null
)

/**
 * Mirrors iOS's `PlaylistsViewModel` view-mode/sort persistence (there backed by UserDefaults) —
 * same 5 sort keys, same list/grid toggle, same tap-the-active-order-again-to-flip-direction
 * interaction (see [selectSortOrder]).
 */
@Singleton
class PlaylistViewSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val FILTER_MODE = stringPreferencesKey("filter_mode")
        val OWNER_FILTER = stringPreferencesKey("owner_filter")
    }

    val settings: Flow<PlaylistViewSettings> = context.playlistViewDataStore.data.map { prefs ->
        PlaylistViewSettings(
            viewMode = prefs[Keys.VIEW_MODE]?.let { runCatching { PlaylistViewMode.valueOf(it) }.getOrNull() }
                ?: PlaylistViewMode.LIST,
            sortOrder = prefs[Keys.SORT_ORDER]?.let { runCatching { PlaylistSortOrder.valueOf(it) }.getOrNull() }
                ?: PlaylistSortOrder.NAME,
            sortAscending = prefs[Keys.SORT_ASCENDING] ?: true,
            filterMode = prefs[Keys.FILTER_MODE]?.let { runCatching { PlaylistFilterMode.valueOf(it) }.getOrNull() }
                ?: PlaylistFilterMode.ALL,
            ownerFilter = prefs[Keys.OWNER_FILTER]
        )
    }

    suspend fun setFilterMode(mode: PlaylistFilterMode) {
        context.playlistViewDataStore.edit { it[Keys.FILTER_MODE] = mode.name }
    }

    suspend fun setOwnerFilter(owner: String?) {
        context.playlistViewDataStore.edit { prefs ->
            if (owner == null) prefs.remove(Keys.OWNER_FILTER) else prefs[Keys.OWNER_FILTER] = owner
        }
    }

    suspend fun setViewMode(mode: PlaylistViewMode) {
        context.playlistViewDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    /** Selecting the already-active sort order flips its direction; selecting a different one
     *  switches to it starting ascending — exactly PlaylistsView.swift's sort-menu tap behavior. */
    suspend fun selectSortOrder(order: PlaylistSortOrder) {
        context.playlistViewDataStore.edit { prefs ->
            val current = prefs[Keys.SORT_ORDER]?.let { runCatching { PlaylistSortOrder.valueOf(it) }.getOrNull() }
            if (current == order) {
                prefs[Keys.SORT_ASCENDING] = !(prefs[Keys.SORT_ASCENDING] ?: true)
            } else {
                prefs[Keys.SORT_ORDER] = order.name
                prefs[Keys.SORT_ASCENDING] = true
            }
        }
    }
}
