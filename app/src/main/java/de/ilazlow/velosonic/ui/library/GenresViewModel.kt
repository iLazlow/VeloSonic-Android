package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.ui.navigation.GenreTracksRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GenresViewModel @Inject constructor(
    libraryRepository: LibraryRepository
) : ViewModel() {
    val genres: StateFlow<List<String>> = libraryRepository.observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class GenreTracksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver
) : ViewModel() {
    private val route: GenreTracksRoute = savedStateHandle.toRoute()

    val genre: String get() = route.genre

    val tracks: StateFlow<List<TrackEntity>> = libraryRepository.observeTracksByGenre(route.genre)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun coverArtUrl(serverHost: String, coverArtId: String?): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId)
}
