package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.ui.navigation.GenreAlbumsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val UNKNOWN_GENRE = "Unknown"

/** Mirrors `LibraryGenresView.swift`: genres are derived client-side from the already-loaded
 *  albums list (grouped by `album.genre`, "Unknown" fallback), not a separate track-genre query —
 *  each row's subtitle is the album count for that genre, and tapping drills into an albums grid
 *  (see [GenreAlbumsViewModel]), not a flat track list. */
@HiltViewModel
class GenresViewModel @Inject constructor(
    libraryRepository: LibraryRepository
) : ViewModel() {
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    fun onSearchTextChange(value: String) {
        _searchText.value = value
    }

    val genres: StateFlow<List<Pair<String, Int>>> =
        combine(libraryRepository.observeAlbums(), _searchText) { albums, query ->
            albums.groupBy { it.genre?.takeIf(String::isNotBlank) ?: UNKNOWN_GENRE }
                .map { (genre, group) -> genre to group.size }
                .filter { (genre, _) -> query.isBlank() || genre.contains(query, ignoreCase = true) }
                .sortedBy { it.first.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class GenreAlbumsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver
) : ViewModel() {
    private val route: GenreAlbumsRoute = savedStateHandle.toRoute()

    val genre: String get() = route.genre

    val albums: StateFlow<List<AlbumEntity>> = libraryRepository.observeAlbums()
        .map { albums -> albums.filter { (it.genre?.takeIf(String::isNotBlank) ?: UNKNOWN_GENRE) == route.genre } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun coverArtUrl(serverHost: String, coverArtId: String?): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId)
}
