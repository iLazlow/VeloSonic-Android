package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    appearanceSettingsStore: AppearanceSettingsStore
) : ViewModel() {
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    fun onSearchTextChange(value: String) {
        _searchText.value = value
    }

    val albums: StateFlow<List<AlbumEntity>> =
        combine(libraryRepository.observeAlbums(), _searchText) { albums, query ->
            if (query.isBlank()) albums else albums.filter {
                it.name.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val animatedAlbumGridEnabled: StateFlow<Boolean> = appearanceSettingsStore.settings
        .map { it.animatedAlbumGridEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun coverArtUrl(serverHost: String, coverArtId: String?): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId)
}
