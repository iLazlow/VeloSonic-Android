package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Mirrors `LibrarySongsView.swift`: flat, alphabetical, tapping a row plays the full filtered
 *  list starting at that index (not a navigation push) — see `onTrackClick`. */
@HiltViewModel
class SongsViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val playbackController: PlaybackController
) : ViewModel() {
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    fun onSearchTextChange(value: String) {
        _searchText.value = value
    }

    private val allTracks: StateFlow<List<TrackEntity>> = libraryRepository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<TrackEntity>> =
        combine(allTracks, _searchText) { tracks, query ->
            if (query.isBlank()) tracks else tracks.filter {
                it.title.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    fun coverArtUrl(serverHost: String, coverArtId: String?): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId)

    fun onTrackClick(track: TrackEntity) {
        val list = tracks.value
        val index = list.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playQueue(list, index)
    }
}
