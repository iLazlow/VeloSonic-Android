package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.ui.common.TrackListRow

@Composable
private fun TrackList(tracks: List<TrackEntity>, coverArtUrl: (String, String?) -> String?) {
    LazyColumn {
        items(tracks, key = { it.id }) { track ->
            TrackListRow(
                title = track.title,
                subtitle = track.artistName,
                coverArtUrl = coverArtUrl(track.serverHost, track.coverArt ?: track.albumId),
                onClick = { }
            )
        }
    }
}

@Composable
fun SongsScreen(viewModel: SongsViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    TrackList(tracks, viewModel::coverArtUrl)
}

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    TrackList(tracks, viewModel::coverArtUrl)
}
