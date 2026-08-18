package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import de.ilazlow.velosonic.ui.common.ArtistListRow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsScreen(
    onArtistClick: (id: String, name: String) -> Unit,
    viewModel: ArtistsViewModel = hiltViewModel()
) {
    val grouped by viewModel.grouped.collectAsStateWithLifecycle()

    LazyColumn {
        grouped.forEach { (letter, artists) ->
            stickyHeader {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(artists, key = { it.id }) { artist ->
                ArtistListRow(
                    name = artist.name,
                    onClick = { onArtistClick(artist.id, artist.name) }
                )
            }
        }
    }
}
