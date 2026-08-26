package de.ilazlow.velosonic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.deeplink.DeepLinkViewModel
import de.ilazlow.velosonic.ui.album.AlbumDetailScreen
import de.ilazlow.velosonic.ui.artist.ArtistDetailScreen
import de.ilazlow.velosonic.ui.common.ComingSoonScreen
import de.ilazlow.velosonic.ui.common.ConnectivityStatusBanner
import de.ilazlow.velosonic.ui.common.LibrarySyncStatusBanner
import de.ilazlow.velosonic.ui.home.HomeScreen
import de.ilazlow.velosonic.ui.library.AlbumsScreen
import de.ilazlow.velosonic.ui.library.ArtistsScreen
import de.ilazlow.velosonic.ui.library.DownloadsScreen
import de.ilazlow.velosonic.ui.library.FavoritesScreen
import de.ilazlow.velosonic.ui.library.GenreAlbumsScreen
import de.ilazlow.velosonic.ui.library.GenresScreen
import de.ilazlow.velosonic.ui.library.LibraryScreen
import de.ilazlow.velosonic.ui.library.RadioScreen
import de.ilazlow.velosonic.ui.library.SongsScreen
import de.ilazlow.velosonic.ui.navigation.AlbumDetailRoute
import de.ilazlow.velosonic.ui.navigation.ArtistDetailRoute
import de.ilazlow.velosonic.ui.navigation.GenreAlbumsRoute
import de.ilazlow.velosonic.ui.navigation.HomeRoute
import de.ilazlow.velosonic.ui.navigation.LibraryAlbumsRoute
import de.ilazlow.velosonic.ui.navigation.LibraryArtistsRoute
import de.ilazlow.velosonic.ui.navigation.LibraryDownloadsRoute
import de.ilazlow.velosonic.ui.navigation.LibraryFavoritesRoute
import de.ilazlow.velosonic.ui.navigation.LibraryGenresRoute
import de.ilazlow.velosonic.ui.navigation.LibraryRadioRoute
import de.ilazlow.velosonic.ui.navigation.LibraryRoute
import de.ilazlow.velosonic.ui.navigation.LibrarySongsRoute
import de.ilazlow.velosonic.ui.navigation.LikedSongsRoute
import de.ilazlow.velosonic.ui.navigation.PlaylistDetailRoute
import de.ilazlow.velosonic.ui.navigation.PlaylistImportRoute
import de.ilazlow.velosonic.ui.navigation.PlaylistsRoute
import de.ilazlow.velosonic.ui.navigation.SearchRoute
import de.ilazlow.velosonic.ui.navigation.SettingsApiRoute
import de.ilazlow.velosonic.ui.navigation.SettingsLanguageRoute
import de.ilazlow.velosonic.ui.navigation.SettingsAppearanceRoute
import de.ilazlow.velosonic.ui.navigation.SettingsAudioAnalysisRoute
import de.ilazlow.velosonic.ui.navigation.SettingsBackupRoute
import de.ilazlow.velosonic.ui.navigation.SettingsDatabaseRoute
import de.ilazlow.velosonic.ui.navigation.SettingsDatabaseRowDetailRoute
import de.ilazlow.velosonic.ui.navigation.SettingsDatabaseTableRoute
import de.ilazlow.velosonic.ui.navigation.SettingsDatabaseViewerRoute
import de.ilazlow.velosonic.ui.navigation.SettingsDebugFullLogRoute
import de.ilazlow.velosonic.ui.navigation.SettingsDebugRoute
import de.ilazlow.velosonic.ui.navigation.SettingsEqRoute
import de.ilazlow.velosonic.ui.navigation.SettingsLiquidCoverRoute
import de.ilazlow.velosonic.ui.navigation.SettingsLyricsRoute
import de.ilazlow.velosonic.ui.navigation.SettingsRadiantCacheListRoute
import de.ilazlow.velosonic.ui.navigation.SettingsRadiantCacheDetailRoute
import de.ilazlow.velosonic.ui.navigation.SettingsManageEqPresetsRoute
import de.ilazlow.velosonic.ui.navigation.SettingsPlaybackRoute
import de.ilazlow.velosonic.ui.navigation.SettingsRoute
import de.ilazlow.velosonic.ui.navigation.SettingsServersRoute
import de.ilazlow.velosonic.ui.navigation.SettingsServerListRoute
import de.ilazlow.velosonic.ui.navigation.SettingsServerDetailRoute
import de.ilazlow.velosonic.ui.navigation.SettingsSharesRoute
import de.ilazlow.velosonic.ui.navigation.SettingsSharingRoute
import de.ilazlow.velosonic.ui.navigation.SettingsStorageRoute
import de.ilazlow.velosonic.ui.navigation.SettingsTranscodingRoute
import de.ilazlow.velosonic.ui.player.MiniPlayerBar
import de.ilazlow.velosonic.ui.player.PlayerScreen
import de.ilazlow.velosonic.ui.playlists.LikedSongsScreen
import de.ilazlow.velosonic.ui.playlists.PlaylistDetailScreen
import de.ilazlow.velosonic.ui.playlists.PlaylistImportScreen
import de.ilazlow.velosonic.ui.playlists.PlaylistsScreen
import de.ilazlow.velosonic.ui.search.SearchScreen
import de.ilazlow.velosonic.ui.settings.ApiSettingsScreen
import de.ilazlow.velosonic.ui.settings.AppearanceSettingsScreen
import de.ilazlow.velosonic.ui.settings.AudioAnalysisSettingsScreen
import de.ilazlow.velosonic.ui.settings.BackupSettingsScreen
import de.ilazlow.velosonic.ui.settings.DatabaseRowDetailScreen
import de.ilazlow.velosonic.ui.settings.DatabaseSettingsScreen
import de.ilazlow.velosonic.ui.settings.DatabaseTableScreen
import de.ilazlow.velosonic.ui.settings.DatabaseViewerScreen
import de.ilazlow.velosonic.ui.settings.DebugFullLogScreen
import de.ilazlow.velosonic.ui.settings.DebugSettingsScreen
import de.ilazlow.velosonic.ui.settings.EqSettingsScreen
import de.ilazlow.velosonic.ui.settings.KawarpSettingsScreen
import de.ilazlow.velosonic.ui.settings.LanguageSettingsScreen
import de.ilazlow.velosonic.ui.settings.LyricsSettingsScreen
import de.ilazlow.velosonic.ui.settings.RadiantLyricsCacheListScreen
import de.ilazlow.velosonic.ui.settings.RadiantLyricsCacheDetailScreen
import de.ilazlow.velosonic.ui.settings.ManageEqPresetsScreen
import de.ilazlow.velosonic.ui.settings.ManageServersScreen
import de.ilazlow.velosonic.ui.settings.PlaybackSettingsScreen
import de.ilazlow.velosonic.ui.settings.ServerDetailScreen
import de.ilazlow.velosonic.ui.settings.ServerListScreen
import de.ilazlow.velosonic.ui.settings.SettingsScreen
import de.ilazlow.velosonic.ui.settings.SharingSettingsScreen
import de.ilazlow.velosonic.ui.settings.StorageSettingsScreen
import de.ilazlow.velosonic.ui.settings.TranscodingDetailScreen
import de.ilazlow.velosonic.ui.share.ManageSharesScreen

private data class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    val labelRes: Int,
    val routeClass: kotlin.reflect.KClass<*>
)

private val topLevelDestinations = listOf(
    TopLevelDestination(HomeRoute, Icons.Filled.Home, R.string.tab_home, HomeRoute::class),
    TopLevelDestination(LibraryRoute, Icons.Filled.LibraryMusic, R.string.tab_library, LibraryRoute::class),
    TopLevelDestination(PlaylistsRoute, Icons.AutoMirrored.Filled.PlaylistPlay, R.string.tab_playlists, PlaylistsRoute::class),
    TopLevelDestination(SearchRoute, Icons.Filled.Search, R.string.tab_search, SearchRoute::class),
    TopLevelDestination(SettingsRoute, Icons.Filled.Settings, R.string.tab_settings, SettingsRoute::class)
)

/**
 * The player is a full-screen overlay sheet controlled by plain state, layered above the whole
 * NavigationSuiteScaffold (bottom nav included) — mirrors iOS's `.fullScreenSheet` presentation.
 * It's deliberately NOT a pushed nav destination: a regular composable<PlayerRoute> would leave
 * the bottom nav bar visible underneath, since that bar is a sibling of the NavHost rather than
 * part of it.
 */
@Composable
fun AppShell(deepLinkViewModel: DeepLinkViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    var showPlayerSheet by rememberSaveable { mutableStateOf(false) }

    val pendingRoute by deepLinkViewModel.route.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let {
            navController.navigate(it)
            deepLinkViewModel.onRouteConsumed()
        }
    }

    // Album Detail paints its own colorful background behind the status bar (like the Player
    // sheet already does) — applying the shell's own statusBarsPadding on top of that would
    // clip its content into the inset area before it ever gets a chance to draw there, leaving
    // a plain, un-themed status bar strip. Every other tab keeps the normal inset.
    val isImmersiveDetail = currentDestination?.hasRoute(AlbumDetailRoute::class) == true

    // NavigationSuiteScaffold swaps its own bottom NavigationBar — which already applies its own
    // navigationBarsPadding internally, per Material3's NavigationBar contract — for a side
    // NavigationRail once the window is wide enough (an unfolded foldable's tablet-sized inner
    // screen). Once that swap happens, nothing else in the content column reserves the system
    // navigation bar's own bottom inset anymore, so MiniPlayerBar — the last item in that column —
    // rendered directly under it instead of above it (confirmed live on a Fold in its unfolded
    // state). Computing the same layout type NavigationSuiteScaffold picks internally lets
    // MiniPlayerBar apply that padding itself only when it's actually the one responsible for it.
    val navSuiteLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val miniPlayerNeedsNavigationBarPadding = navSuiteLayoutType != NavigationSuiteType.NavigationBar

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            layoutType = navSuiteLayoutType,
            navigationSuiteItems = {
                topLevelDestinations.forEach { destination ->
                    item(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(id = destination.labelRes)) },
                        selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(destination.routeClass)
                        } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = if (isImmersiveDetail) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding()) {
                    // Non-immersive screens have a plain themed background all the way to the top,
                    // so pushing content down to make room for the banner (Column order) looks
                    // identical to overlaying it — this is the simpler of the two and matches every
                    // other screen. Immersive screens paint their own gradient/hero art behind the
                    // status bar, so a push-down banner here would sit in a separate, plain white
                    // strip above that gradient instead of blending into it — handled below instead.
                    if (!isImmersiveDetail) {
                        ConnectivityStatusBanner()
                        LibrarySyncStatusBanner()
                    }
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier.weight(1f)
                    ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                            onPlaylistClick = { navController.navigate(PlaylistDetailRoute(it)) },
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) }
                        )
                    }
                    composable<LibraryRoute> {
                        LibraryScreen(onNavigate = { navController.navigate(it) })
                    }
                    composable<PlaylistsRoute> {
                        PlaylistsScreen(
                            onPlaylistClick = { navController.navigate(PlaylistDetailRoute(it)) },
                            onLikedSongsClick = { navController.navigate(LikedSongsRoute) },
                            onImportClick = { navController.navigate(PlaylistImportRoute) }
                        )
                    }
                    composable<PlaylistImportRoute> {
                        PlaylistImportScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SearchRoute> {
                        SearchScreen(
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) }
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(onNavigate = { navController.navigate(it) })
                    }
                    composable<SettingsPlaybackRoute> {
                        PlaybackSettingsScreen(
                            onBack = { navController.navigateUp() },
                            onNavigateToEq = { navController.navigate(SettingsEqRoute) },
                            onNavigateToWifiTranscoding = { navController.navigate(SettingsTranscodingRoute("WIFI")) },
                            onNavigateToCellularTranscoding = { navController.navigate(SettingsTranscodingRoute("CELLULAR")) }
                        )
                    }
                    composable<SettingsLyricsRoute> {
                        LyricsSettingsScreen(
                            onBack = { navController.navigateUp() },
                            onNavigateToRadiantCache = { navController.navigate(SettingsRadiantCacheListRoute) }
                        )
                    }
                    composable<SettingsRadiantCacheListRoute> {
                        RadiantLyricsCacheListScreen(
                            onBack = { navController.navigateUp() },
                            onEntryClick = { id -> navController.navigate(SettingsRadiantCacheDetailRoute(id)) }
                        )
                    }
                    composable<SettingsRadiantCacheDetailRoute> { backStackEntry ->
                        val route: SettingsRadiantCacheDetailRoute = backStackEntry.toRoute()
                        RadiantLyricsCacheDetailScreen(entryId = route.entryId, onBack = { navController.navigateUp() })
                    }
                    composable<SettingsAppearanceRoute> {
                        AppearanceSettingsScreen(
                            onBack = { navController.navigateUp() },
                            onNavigateToLiquidCover = { navController.navigate(SettingsLiquidCoverRoute) }
                        )
                    }
                    composable<SettingsLiquidCoverRoute> {
                        KawarpSettingsScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsServersRoute> {
                        ManageServersScreen(
                            onBack = { navController.navigateUp() },
                            onNavigateToShares = { navController.navigate(SettingsSharesRoute) },
                            onNavigateToServerList = { navController.navigate(SettingsServerListRoute) }
                        )
                    }
                    composable<SettingsServerListRoute> {
                        ServerListScreen(
                            onBack = { navController.navigateUp() },
                            onServerClick = { host -> navController.navigate(SettingsServerDetailRoute(host)) }
                        )
                    }
                    composable<SettingsServerDetailRoute> { backStackEntry ->
                        val route: SettingsServerDetailRoute = backStackEntry.toRoute()
                        ServerDetailScreen(host = route.serverHost, onBack = { navController.navigateUp() })
                    }
                    composable<SettingsEqRoute> {
                        EqSettingsScreen(
                            onBack = { navController.navigateUp() },
                            onManagePresets = { navController.navigate(SettingsManageEqPresetsRoute) }
                        )
                    }
                    composable<SettingsManageEqPresetsRoute> {
                        ManageEqPresetsScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsAudioAnalysisRoute> {
                        AudioAnalysisSettingsScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsSharesRoute> {
                        ManageSharesScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsStorageRoute> {
                        StorageSettingsScreen(
                            onBack = { navController.navigateUp() },
                            onNavigateToDownloadTranscoding = { navController.navigate(SettingsTranscodingRoute("DOWNLOAD")) }
                        )
                    }
                    composable<SettingsBackupRoute> {
                        BackupSettingsScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsSharingRoute> {
                        SharingSettingsScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsApiRoute> {
                        ApiSettingsScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsLanguageRoute> {
                        LanguageSettingsScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsDatabaseRoute> {
                        DatabaseSettingsScreen(
                            onBack = { navController.navigateUp() },
                            onNavigateToViewer = { navController.navigate(SettingsDatabaseViewerRoute) },
                            onNavigateToBackup = { navController.navigate(SettingsBackupRoute) }
                        )
                    }
                    composable<SettingsDatabaseViewerRoute> {
                        DatabaseViewerScreen(
                            onBack = { navController.navigateUp() },
                            onTableClick = { table -> navController.navigate(SettingsDatabaseTableRoute(table)) }
                        )
                    }
                    composable<SettingsDatabaseTableRoute> { backStackEntry ->
                        val route: SettingsDatabaseTableRoute = backStackEntry.toRoute()
                        DatabaseTableScreen(
                            tableName = route.tableName,
                            onBack = { navController.navigateUp() },
                            onRowClick = { rowId -> navController.navigate(SettingsDatabaseRowDetailRoute(route.tableName, rowId)) }
                        )
                    }
                    composable<SettingsDatabaseRowDetailRoute> { backStackEntry ->
                        val route: SettingsDatabaseRowDetailRoute = backStackEntry.toRoute()
                        DatabaseRowDetailScreen(tableName = route.tableName, rowId = route.rowId, onBack = { navController.navigateUp() })
                    }
                    composable<SettingsDebugRoute> {
                        DebugSettingsScreen(
                            onBack = { navController.navigateUp() },
                            onViewFullLog = { navController.navigate(SettingsDebugFullLogRoute) }
                        )
                    }
                    composable<SettingsDebugFullLogRoute> {
                        DebugFullLogScreen(onBack = { navController.navigateUp() })
                    }
                    composable<SettingsTranscodingRoute> { backStackEntry ->
                        val route: SettingsTranscodingRoute = backStackEntry.toRoute()
                        val settingsViewModel: de.ilazlow.velosonic.ui.settings.SettingsViewModel = hiltViewModel()
                        val storageViewModel: de.ilazlow.velosonic.ui.settings.StorageSettingsViewModel = hiltViewModel()
                        val playback by settingsViewModel.playbackSettings.collectAsStateWithLifecycle()
                        val storage by storageViewModel.settings.collectAsStateWithLifecycle()
                        when (route.target) {
                            "WIFI" -> TranscodingDetailScreen(
                                title = "WiFi Settings",
                                format = playback.wifiFormat,
                                bitrate = playback.wifiBitrate,
                                onlyLossless = playback.onlyTranscodeLosslessWifi,
                                customFormat = playback.customWifiFormat,
                                customBitrate = playback.customWifiBitrate,
                                onBack = { navController.navigateUp() },
                                onFormatChange = settingsViewModel::setWifiFormat,
                                onBitrateChange = settingsViewModel::setWifiBitrate,
                                onOnlyLosslessChange = settingsViewModel::setOnlyTranscodeLosslessWifi,
                                onCustomFormatChange = settingsViewModel::setCustomWifiFormat,
                                onCustomBitrateChange = settingsViewModel::setCustomWifiBitrate
                            )
                            "CELLULAR" -> TranscodingDetailScreen(
                                title = "Cellular Settings",
                                format = playback.cellularFormat,
                                bitrate = playback.cellularBitrate,
                                onlyLossless = playback.onlyTranscodeLosslessCellular,
                                customFormat = playback.customCellularFormat,
                                customBitrate = playback.customCellularBitrate,
                                onBack = { navController.navigateUp() },
                                onFormatChange = settingsViewModel::setCellularFormat,
                                onBitrateChange = settingsViewModel::setCellularBitrate,
                                onOnlyLosslessChange = settingsViewModel::setOnlyTranscodeLosslessCellular,
                                onCustomFormatChange = settingsViewModel::setCustomCellularFormat,
                                onCustomBitrateChange = settingsViewModel::setCustomCellularBitrate
                            )
                            else -> TranscodingDetailScreen(
                                title = "Download Settings",
                                format = storage.downloadFormat,
                                bitrate = storage.downloadBitrate,
                                onlyLossless = null,
                                customFormat = storage.customDownloadFormat,
                                customBitrate = storage.customDownloadBitrate,
                                onBack = { navController.navigateUp() },
                                onFormatChange = storageViewModel::setDownloadFormat,
                                onBitrateChange = storageViewModel::setDownloadBitrate,
                                onOnlyLosslessChange = {},
                                onCustomFormatChange = storageViewModel::setCustomDownloadFormat,
                                onCustomBitrateChange = storageViewModel::setCustomDownloadBitrate
                            )
                        }
                    }

                    composable<LibraryArtistsRoute> {
                        ArtistsScreen(
                            onBack = { navController.navigateUp() },
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) }
                        )
                    }
                    composable<LibraryAlbumsRoute> {
                        AlbumsScreen(
                            onBack = { navController.navigateUp() },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) }
                        )
                    }
                    composable<LibrarySongsRoute> { SongsScreen(onBack = { navController.navigateUp() }) }
                    composable<LibraryGenresRoute> {
                        GenresScreen(
                            onBack = { navController.navigateUp() },
                            onGenreClick = { navController.navigate(GenreAlbumsRoute(it)) }
                        )
                    }
                    composable<GenreAlbumsRoute> {
                        GenreAlbumsScreen(
                            onBack = { navController.navigateUp() },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) }
                        )
                    }
                    composable<LibraryFavoritesRoute> {
                        FavoritesScreen(
                            onBack = { navController.navigateUp() },
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) }
                        )
                    }
                    composable<LibraryRadioRoute> { RadioScreen(onBack = { navController.navigateUp() }) }
                    composable<LibraryDownloadsRoute> {
                        DownloadsScreen(
                            onBack = { navController.navigateUp() },
                            onPlaylistClick = { navController.navigate(PlaylistDetailRoute(it)) },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) }
                        )
                    }
                    composable<ArtistDetailRoute> {
                        ArtistDetailScreen(
                            onBack = { navController.navigateUp() },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) }
                        )
                    }
                    composable<AlbumDetailRoute> {
                        AlbumDetailScreen(
                            onBack = { navController.navigateUp() },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) }
                        )
                    }
                    composable<PlaylistDetailRoute> {
                        PlaylistDetailScreen(
                            onBack = { navController.navigateUp() },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) }
                        )
                    }
                    composable<LikedSongsRoute> {
                        LikedSongsScreen(
                            onBack = { navController.navigateUp() },
                            onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                            onArtistClick = { id, name -> navController.navigate(ArtistDetailRoute(id, name)) }
                        )
                    }
                }
                    MiniPlayerBar(
                        onClick = { showPlayerSheet = true },
                        needsNavigationBarPadding = miniPlayerNeedsNavigationBarPadding
                    )
                }
                if (isImmersiveDetail) {
                    // Offset below the screen's own back/title/menu row (a fixed-height custom bar,
                    // not a standard TopAppBar this could measure against) — at the bare TopCenter
                    // position the badge would render directly on top of that row's centered title
                    // text instead of below it. Stacked in a Column (not two independently-aligned
                    // banners) so both can be visible at once — offline *and* still syncing — without
                    // rendering on top of each other.
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 60.dp)
                    ) {
                        ConnectivityStatusBanner()
                        LibrarySyncStatusBanner()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayerSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            PlayerScreen(
                onDismiss = { showPlayerSheet = false },
                onAlbumClick = {
                    showPlayerSheet = false
                    navController.navigate(AlbumDetailRoute(it))
                },
                onArtistClick = { id, name ->
                    showPlayerSheet = false
                    navController.navigate(ArtistDetailRoute(id, name))
                }
            )
        }
    }
}
