package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.ArtistEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    libraryRepository: LibraryRepository
) : ViewModel() {

    val grouped: StateFlow<List<Pair<String, List<ArtistEntity>>>> = libraryRepository.observeArtists()
        .map { artists ->
            artists.groupBy { artist ->
                val first = artist.name.firstOrNull()?.uppercaseChar()
                if (first != null && first.isLetter()) first.toString() else "#"
            }.toSortedMap(compareBy { if (it == "#") "" else it }).toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
