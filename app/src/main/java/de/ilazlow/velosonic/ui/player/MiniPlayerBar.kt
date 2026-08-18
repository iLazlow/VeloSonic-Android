package de.ilazlow.velosonic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.ui.common.CoverArtTile

/** Shown above the bottom nav bar whenever something is loaded, regardless of which tab is
 *  active — mirrors iOS's MiniPlayerAccessory, including its radio-station branch: artwork uses
 *  the `"ra-{subsonicId}"` cover-art id convention (a track's own `coverArt` id doesn't apply to
 *  a station), title prefers the live ICY stream title over the station name, subtitle is always
 *  the station name, and skip-next is hidden since a radio "queue" isn't a real concept. */
@Composable
fun MiniPlayerBar(
    onClick: () -> Unit,
    needsNavigationBarPadding: Boolean = false,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val track = nowPlaying.track
    val station = nowPlaying.radioStation
    val title = track?.title ?: nowPlaying.radioStreamTitle ?: station?.name ?: return
    val subtitle = track?.artistName ?: station?.name.orEmpty()
    val coverArtUrl = track?.let { viewModel.coverArtUrl(it.serverHost, it.coverArt, 120) }
        ?: station?.takeIf { it.coverArt != null }?.let { viewModel.coverArtUrl(it.serverHost, "ra-${it.subsonicId}", 120) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (needsNavigationBarPadding) Modifier.navigationBarsPadding() else Modifier)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CoverArtTile(
                url = coverArtUrl,
                contentDescription = title,
                modifier = Modifier.size(44.dp)
            )
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(
                    imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
            }
            if (!nowPlaying.isPlayingRadio) {
                IconButton(onClick = viewModel::skipToNext) {
                    Icon(imageVector = Icons.Filled.SkipNext, contentDescription = null)
                }
            }
        }
    }
}
