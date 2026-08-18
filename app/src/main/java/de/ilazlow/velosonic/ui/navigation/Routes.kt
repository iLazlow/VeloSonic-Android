package de.ilazlow.velosonic.ui.navigation

import kotlinx.serialization.Serializable

// Bottom navigation destinations
@Serializable object HomeRoute
@Serializable object LibraryRoute
@Serializable object PlaylistsRoute
@Serializable object SearchRoute
@Serializable object SettingsRoute

// Library sub-screens
@Serializable object LibraryArtistsRoute
@Serializable object LibraryAlbumsRoute
@Serializable object LibrarySongsRoute
@Serializable object LibraryGenresRoute
@Serializable object LibraryFavoritesRoute
@Serializable object LibraryRadioRoute
@Serializable object LibraryDownloadsRoute

@Serializable data class GenreTracksRoute(val genre: String)

// Shared detail destinations, reachable from Home and every Library sub-screen
@Serializable data class ArtistDetailRoute(val artistId: String, val artistName: String)
@Serializable data class AlbumDetailRoute(val albumId: String)
@Serializable data class PlaylistDetailRoute(val playlistId: String)
@Serializable object PlaylistImportRoute

// Settings sub-screens
@Serializable object SettingsPlaybackRoute
@Serializable object SettingsLyricsRoute
@Serializable object SettingsAppearanceRoute
@Serializable object SettingsLiquidCoverRoute
@Serializable object SettingsServersRoute
@Serializable object SettingsServerListRoute
@Serializable object SettingsEqRoute
@Serializable object SettingsManageEqPresetsRoute
@Serializable object SettingsAudioAnalysisRoute
@Serializable object SettingsSharesRoute
@Serializable object SettingsStorageRoute
@Serializable object SettingsBackupRoute

/** [target] is one of "WIFI"/"CELLULAR"/"DOWNLOAD" — one shared screen, three call sites,
 *  mirrors iOS's single `TranscodingDetailView` reused with different bindings. */
@Serializable data class SettingsTranscodingRoute(val target: String)
@Serializable object SettingsSharingRoute
@Serializable object SettingsApiRoute
@Serializable object SettingsDatabaseRoute
@Serializable object SettingsDatabaseViewerRoute
@Serializable data class SettingsDatabaseTableRoute(val tableName: String)
@Serializable data class SettingsDatabaseRowDetailRoute(val tableName: String, val rowId: Long)
@Serializable object SettingsDebugRoute
@Serializable object SettingsDebugFullLogRoute
@Serializable data class SettingsServerDetailRoute(val serverHost: String)
@Serializable object SettingsRadiantCacheListRoute
@Serializable data class SettingsRadiantCacheDetailRoute(val entryId: String)
