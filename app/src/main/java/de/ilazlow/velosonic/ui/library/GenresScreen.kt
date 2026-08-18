package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.ui.common.TrackListRow

@Composable
fun GenresScreen(
    onGenreClick: (String) -> Unit,
    viewModel: GenresViewModel = hiltViewModel()
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()

    LazyColumn {
        items(genres, key = { it }) { genre ->
            Text(
                text = genre,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGenreClick(genre) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun GenreTracksScreen(viewModel: GenreTracksViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()

    LazyColumn {
        items(tracks, key = { it.id }) { track ->
            TrackListRow(
                title = track.title,
                subtitle = track.artistName,
                coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt ?: track.albumId),
                onClick = { }
            )
        }
    }
}
