package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import de.ilazlow.velosonic.ui.common.ArtistListRow
import de.ilazlow.velosonic.ui.common.LibrarySearchField
import de.ilazlow.velosonic.ui.common.SectionIndexScrubber
import de.ilazlow.velosonic.ui.settings.SettingsTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsScreen(
    onBack: () -> Unit,
    onArtistClick: (id: String, name: String) -> Unit,
    viewModel: ArtistsViewModel = hiltViewModel()
) {
    val grouped by viewModel.grouped.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "Artists", onBack = onBack)
        LibrarySearchField(value = searchText, onValueChange = viewModel::onSearchTextChange)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                            avatarUrl = viewModel.avatarUrl(artist),
                            onClick = { onArtistClick(artist.id, artist.name) }
                        )
                    }
                }
            }

            if (searchText.isBlank()) {
                val sectionKeys = grouped.map { it.first }
                SectionIndexScrubber(
                    keys = sectionKeys,
                    onSelect = { letter ->
                        val targetIndex = flattenedIndexOf(grouped, letter)
                        if (targetIndex >= 0) scope.launch { listState.scrollToItem(targetIndex) }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

/** Sticky headers and their items all count as flat `LazyColumn` item positions — this finds the
 *  header item's index for [letter] to scroll to, mirroring what `LazyListScope.stickyHeader`
 *  actually emits under the hood (one item per header, one per row, in declaration order). */
private fun flattenedIndexOf(grouped: List<Pair<String, List<*>>>, letter: String): Int {
    var index = 0
    for ((key, items) in grouped) {
        if (key == letter) return index
        index += 1 + items.size
    }
    return -1
}
