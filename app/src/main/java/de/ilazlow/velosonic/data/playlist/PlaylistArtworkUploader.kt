package de.ilazlow.velosonic.data.playlist

import android.content.Context
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.NavidromeJwtClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads custom playlist artwork (Navidrome's native `/api/playlist/{id}/image`, gated behind
 * [de.ilazlow.velosonic.domain.supportsPlaylistArtworkUpload]) and evicts the now-stale Coil
 * cache entry for that playlist's cover URL, so the new image shows immediately everywhere it's
 * displayed instead of only after the process restarts or the disk cache naturally expires.
 * Shared between [de.ilazlow.velosonic.ui.playlists.PlaylistsViewModel] and
 * [de.ilazlow.velosonic.ui.playlists.PlaylistDetailViewModel] — both have their own "Edit
 * Playlist" entry point, and this is the one piece of upload+cache-invalidation logic behind them
 * that shouldn't drift into two independently-maintained copies. Deliberately has no involvement
 * when no new image is picked — callers only invoke [upload] once they already have a non-null
 * byte array, so a plain rename never touches artwork at all.
 */
@Singleton
class PlaylistArtworkUploader @Inject constructor(
    private val navidromeJwtClient: NavidromeJwtClient,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    @ApplicationContext private val context: Context
) {
    suspend fun upload(config: ServerConfigEntity, playlist: PlaylistEntity, imageBytes: ByteArray, mimeType: String): Boolean {
        val uploaded = navidromeJwtClient.uploadPlaylistArtwork(config, playlist.subsonicId, imageBytes, mimeType)
        if (uploaded) evictCache(playlist.serverHost, playlist.coverArt ?: playlist.subsonicId)
        return uploaded
    }

    private fun evictCache(serverHost: String, coverArtId: String) {
        val url = coverArtUrlResolver.remoteUrlFor(serverHost, coverArtId) ?: return
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.remove(MemoryCache.Key(url))
        loader.diskCache?.remove(url)
    }
}
