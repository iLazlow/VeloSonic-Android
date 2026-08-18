package de.ilazlow.velosonic.deeplink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.playback.PlaybackController
import de.ilazlow.velosonic.ui.navigation.AlbumDetailRoute
import de.ilazlow.velosonic.ui.navigation.ArtistDetailRoute
import de.ilazlow.velosonic.ui.navigation.PlaylistDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves a [DeepLinkTarget] into either a navigation [route] (album/artist/playlist — the
 * screen composes and navigates to it) or an immediate side effect (song — starts playback
 * directly, mirroring iOS's `songToPlayId`, no dedicated "track detail" screen exists to
 * navigate to). A song deep link to a track that isn't in the local library yet is a silent
 * no-op — resolving it would need an extra network fetch for just that one track's metadata,
 * which doesn't exist here; every other deep-linkable entity type is always locally synced
 * already since the library sync covers the whole catalog, so this gap is song-only.
 */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val holder: DeepLinkHolder,
    private val trackDao: TrackDao,
    private val playbackController: PlaybackController
) : ViewModel() {
    private val _route = MutableStateFlow<Any?>(null)
    val route: StateFlow<Any?> = _route.asStateFlow()

    init {
        viewModelScope.launch {
            holder.pending.collect { target ->
                if (target == null) return@collect
                when (target) {
                    is DeepLinkTarget.Album -> _route.value = AlbumDetailRoute(target.compositeId)
                    is DeepLinkTarget.Artist -> _route.value = ArtistDetailRoute(target.compositeId, "")
                    is DeepLinkTarget.Playlist -> _route.value = PlaylistDetailRoute(target.compositeId)
                    is DeepLinkTarget.Song -> trackDao.getById(target.compositeId)?.let { playbackController.playTrack(it) }
                }
                holder.consume()
            }
        }
    }

    fun onRouteConsumed() {
        _route.value = null
    }
}
