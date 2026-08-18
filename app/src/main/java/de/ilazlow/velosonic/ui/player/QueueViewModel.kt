package de.ilazlow.velosonic.ui.player

import androidx.lifecycle.ViewModel
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

/** Backs the "Up Next" queue screen — mirrors `UpNextView.swift`'s `UpNextViewModel`: live queue
 *  + now-playing index, tap-to-jump, swipe-to-remove, and a destructive clear-queue action. */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val coverArtUrlResolver: CoverArtUrlResolver
) : ViewModel() {
    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 120): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    fun jumpTo(index: Int) = playbackController.jumpTo(index)

    fun removeAt(index: Int) = playbackController.removeAt(index)

    fun clearQueue() = playbackController.clearQueue()
}
