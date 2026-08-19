package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.ui.common.AlbumGridItem
import de.ilazlow.velosonic.ui.common.LibrarySearchField
import de.ilazlow.velosonic.ui.settings.SettingsTopBar

@Composable
fun AlbumsScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val animatedGrid by viewModel.animatedAlbumGridEnabled.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "Albums", onBack = onBack)
        LibrarySearchField(value = searchText, onValueChange = viewModel::onSearchTextChange)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumGridItem(
                    title = album.name,
                    artist = album.artistName,
                    coverArtUrl = viewModel.coverArtUrl(album.serverHost, album.coverArt),
                    onClick = { onAlbumClick(album.id) },
                    animate = animatedGrid
                )
            }
        }
    }
}
