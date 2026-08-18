package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Shared by the flat Songs list and the Favorites list — same shape, different source Flow. */
sealed class TrackListViewModel(
    libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver
) : ViewModel() {
    protected abstract val source: kotlinx.coroutines.flow.Flow<List<TrackEntity>>

    val tracks: StateFlow<List<TrackEntity>> by lazy {
        source.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    fun coverArtUrl(serverHost: String, coverArtId: String?): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId)
}

@HiltViewModel
class SongsViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    coverArtUrlResolver: CoverArtUrlResolver
) : TrackListViewModel(libraryRepository, coverArtUrlResolver) {
    override val source = libraryRepository.observeTracks()
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    coverArtUrlResolver: CoverArtUrlResolver
) : TrackListViewModel(libraryRepository, coverArtUrlResolver) {
    override val source = libraryRepository.observeFavoriteTracks()
}
