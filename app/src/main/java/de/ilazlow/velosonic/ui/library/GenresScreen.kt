package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.ui.common.AlbumGridItem
import de.ilazlow.velosonic.ui.common.LibrarySearchField
import de.ilazlow.velosonic.ui.common.SectionIndexScrubber
import de.ilazlow.velosonic.ui.settings.SettingsTopBar
import kotlinx.coroutines.launch

/** Mirrors `LibraryGenresView.swift`: flat A–Z list of genres with an "N albums" subtitle,
 *  drilling into an albums grid per genre (see [GenreAlbumsScreen]) — genres are NOT tracks here. */
@Composable
fun GenresScreen(
    onBack: () -> Unit,
    onGenreClick: (String) -> Unit,
    viewModel: GenresViewModel = hiltViewModel()
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val sectionKeys = genres.map { (name, _) ->
        val first = name.firstOrNull()?.uppercaseChar()
        if (first != null && first.isLetter()) first.toString() else "#"
    }.distinct()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "Genres", onBack = onBack)
        LibrarySearchField(value = searchText, onValueChange = viewModel::onSearchTextChange)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(genres, key = { it.first }) { (genre, albumCount) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onGenreClick(genre) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(text = genre, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "$albumCount albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (searchText.isBlank()) {
                SectionIndexScrubber(
                    keys = sectionKeys,
                    onSelect = { letter ->
                        val targetIndex = genres.indexOfFirst { (name, _) ->
                            val first = name.firstOrNull()?.uppercaseChar()
                            (if (first != null && first.isLetter()) first.toString() else "#") == letter
                        }
                        if (targetIndex >= 0) scope.launch { listState.scrollToItem(targetIndex) }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
fun GenreAlbumsScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    viewModel: GenreAlbumsViewModel = hiltViewModel()
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = viewModel.genre, onBack = onBack)
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
                    onClick = { onAlbumClick(album.id) }
                )
            }
        }
    }
}
