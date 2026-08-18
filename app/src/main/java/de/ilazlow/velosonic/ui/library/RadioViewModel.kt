package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    serverRepository: ServerRepository,
    private val playbackController: PlaybackController,
    private val coverArtUrlResolver: CoverArtUrlResolver
) : ViewModel() {
    val stations: StateFlow<List<RadioStationEntity>> = libraryRepository.observeRadioStations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    val serverNames: StateFlow<Map<String, String>> = serverRepository.observeServers()
        .map { configs -> configs.associate { it.host to it.name.ifBlank { it.host } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onStationClick(station: RadioStationEntity) {
        val current = nowPlaying.value
        if (current.radioStation?.id == station.id) playbackController.togglePlayPause()
        else playbackController.playRadio(station)
    }

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 150): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)
}
