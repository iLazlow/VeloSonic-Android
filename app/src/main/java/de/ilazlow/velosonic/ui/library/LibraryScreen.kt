package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.ui.navigation.LibraryAlbumsRoute
import de.ilazlow.velosonic.ui.navigation.LibraryArtistsRoute
import de.ilazlow.velosonic.ui.navigation.LibraryDownloadsRoute
import de.ilazlow.velosonic.ui.navigation.LibraryFavoritesRoute
import de.ilazlow.velosonic.ui.navigation.LibraryGenresRoute
import de.ilazlow.velosonic.ui.navigation.LibraryRadioRoute
import de.ilazlow.velosonic.ui.navigation.LibrarySongsRoute

private data class LibraryEntry(val icon: ImageVector, val labelRes: Int, val route: Any)

private val libraryEntries = listOf(
    LibraryEntry(Icons.Filled.Mic, R.string.library_artists, LibraryArtistsRoute),
    LibraryEntry(Icons.Filled.ViewModule, R.string.library_albums, LibraryAlbumsRoute),
    LibraryEntry(Icons.Filled.MusicNote, R.string.library_songs, LibrarySongsRoute),
    LibraryEntry(Icons.Filled.Style, R.string.library_genres, LibraryGenresRoute),
    LibraryEntry(Icons.Filled.Downloading, R.string.library_downloads, LibraryDownloadsRoute),
    LibraryEntry(Icons.Filled.Radio, R.string.library_radio, LibraryRadioRoute),
    LibraryEntry(Icons.Filled.Favorite, R.string.library_favorites, LibraryFavoritesRoute)
)

@Composable
fun LibraryScreen(onNavigate: (Any) -> Unit) {
    LazyColumn {
        items(libraryEntries) { entry ->
            LibraryRow(
                icon = entry.icon,
                label = stringResource(id = entry.labelRes),
                onClick = { onNavigate(entry.route) }
            )
        }
    }
}

@Composable
private fun LibraryRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
