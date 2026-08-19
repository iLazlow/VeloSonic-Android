package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.ui.common.NowPlayingIndicator
import de.ilazlow.velosonic.ui.common.TrackListRow
import de.ilazlow.velosonic.ui.common.LibrarySearchField
import de.ilazlow.velosonic.ui.common.formatTrackDuration
import de.ilazlow.velosonic.ui.settings.SettingsTopBar

@Composable
fun SongsScreen(onBack: () -> Unit, viewModel: SongsViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "Songs", onBack = onBack)
        LibrarySearchField(value = searchText, onValueChange = viewModel::onSearchTextChange)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks, key = { it.id }) { track ->
                val isCurrentTrack = nowPlaying.track?.id == track.id
                TrackListRow(
                    title = track.title,
                    subtitle = track.artistName,
                    coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt ?: track.albumId),
                    onClick = { viewModel.onTrackClick(track) },
                    isCurrentTrack = isCurrentTrack,
                    trailingContent = {
                        if (isCurrentTrack) {
                            NowPlayingIndicator(isPlaying = nowPlaying.isPlaying)
                        } else {
                            Text(formatTrackDuration(track.duration))
                        }
                    }
                )
            }
        }
    }
}
