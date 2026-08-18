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

private val Context.appearanceSettingsDataStore by preferencesDataStore(name = "appearance_settings")

/** Mirrors AppearanceSettings.swift's `defaultApiUrl` verbatim (same template placeholders,
 *  same host) — see [de.ilazlow.velosonic.data.artwork.AnimatedArtworkApiClient] for how it's
 *  actually filled in and called. */
const val DEFAULT_ANIMATED_ARTWORK_API_URL =
    "https://ama.trainswift.net/api/v1/artwork/search?artist={artistName}&album={albumName}&title={songName}"

data class AppearanceSettings(
    /** Android 12+ dynamic (Material You) color scheme vs. the fixed brand-seed fallback — see
     *  VeloSonicTheme's own doc comment for why this is the primary path, not dark/light mode
     *  (which always just follows the system, same as iOS never offered an in-app override).
     *  "Dynamic", not "wallpaper" — dynamicLightColorScheme/dynamicDarkColorScheme reflect
     *  whatever the system's current Material You color is, regardless of whether the user got
     *  there via wallpaper extraction or a manually-picked accent in Settings → Wallpaper & style;
     *  the UI label used to say "wallpaper colors" specifically, which was actively misleading. */
    val dynamicColorEnabled: Boolean = true,
    /** Master switch mirroring AppearanceSettings.swift's `animatedArtworksEnabled` — gates both
     *  native animated-WebP playback in detail views and the whole Apple-animated-artwork (video)
     *  subsystem. Lock-screen animated artwork has no Android surface at all (no MediaStyle
     *  notification equivalent), so unlike iOS there's no separate lockscreen-disable toggle. */
    val animatedArtworksEnabled: Boolean = true,
    /** Mirrors `animatedAlbumGridEnabled` — extends animated-WebP playback to grid cells too,
     *  not just detail headers/the player. Never enables video in grid cells (iOS never does
     *  either — see ArtworkView.shouldShowVideo's `isDetail`-only gate). */
    val animatedAlbumGridEnabled: Boolean = false,
    /** Mirrors `applyMissingAnimatedArtworks` — enables the third-party trainswift.net HLS video
     *  lookup/export pipeline. This hits an unofficial, unowned external service (same risk class
     *  as the skipped Radiant Lyrics API, though notably keyless), so it's surfaced as an
     *  explicit opt-out rather than something silently always-on. */
    val applyMissingAnimatedArtworks: Boolean = true,
    val animatedArtworkApiUrl: String = DEFAULT_ANIMATED_ARTWORK_API_URL
)

@Singleton
class AppearanceSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val ANIMATED_ARTWORKS_ENABLED = booleanPreferencesKey("animated_artworks_enabled")
        val ANIMATED_ALBUM_GRID_ENABLED = booleanPreferencesKey("animated_album_grid_enabled")
        val APPLY_MISSING_ANIMATED_ARTWORKS = booleanPreferencesKey("apply_missing_animated_artworks")
        val ANIMATED_ARTWORK_API_URL = stringPreferencesKey("animated_artwork_api_url")
    }

    val settings: Flow<AppearanceSettings> = context.appearanceSettingsDataStore.data.map { prefs ->
        AppearanceSettings(
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true,
            animatedArtworksEnabled = prefs[Keys.ANIMATED_ARTWORKS_ENABLED] ?: true,
            animatedAlbumGridEnabled = prefs[Keys.ANIMATED_ALBUM_GRID_ENABLED] ?: false,
            applyMissingAnimatedArtworks = prefs[Keys.APPLY_MISSING_ANIMATED_ARTWORKS] ?: true,
            animatedArtworkApiUrl = prefs[Keys.ANIMATED_ARTWORK_API_URL] ?: DEFAULT_ANIMATED_ARTWORK_API_URL
        )
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.appearanceSettingsDataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    }

    suspend fun setAnimatedArtworksEnabled(enabled: Boolean) {
        context.appearanceSettingsDataStore.edit { it[Keys.ANIMATED_ARTWORKS_ENABLED] = enabled }
    }

    suspend fun setAnimatedAlbumGridEnabled(enabled: Boolean) {
        context.appearanceSettingsDataStore.edit { it[Keys.ANIMATED_ALBUM_GRID_ENABLED] = enabled }
    }

    suspend fun setApplyMissingAnimatedArtworks(enabled: Boolean) {
        context.appearanceSettingsDataStore.edit { it[Keys.APPLY_MISSING_ANIMATED_ARTWORKS] = enabled }
    }

    suspend fun setAnimatedArtworkApiUrl(url: String) {
        context.appearanceSettingsDataStore.edit { it[Keys.ANIMATED_ARTWORK_API_URL] = url }
    }
}
