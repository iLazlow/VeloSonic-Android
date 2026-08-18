package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.ArtistEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver
) : ViewModel() {
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    fun onSearchTextChange(value: String) {
        _searchText.value = value
    }

    val grouped: StateFlow<List<Pair<String, List<ArtistEntity>>>> =
        combine(libraryRepository.observeArtists(), _searchText) { artists, query ->
            val filtered = if (query.isBlank()) artists else artists.filter { it.name.contains(query, ignoreCase = true) }
            filtered.groupBy { artist ->
                val first = artist.name.firstOrNull()?.uppercaseChar()
                if (first != null && first.isLetter()) first.toString() else "#"
            }.toSortedMap(compareBy { if (it == "#") "" else it }).toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun avatarUrl(artist: ArtistEntity, size: Int = 200): String? =
        coverArtUrlResolver.urlFor(artist.serverHost, artist.subsonicId, size)
}
