package de.ilazlow.velosonic.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.coverart.CoverArtContentProvider
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.ArtistEntity
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.playlist.PlaylistSubsonicClient
import de.ilazlow.velosonic.data.sync.compositeId
import de.ilazlow.velosonic.data.sync.toStandaloneEntity
import de.ilazlow.velosonic.domain.mightSupportRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

private const val ROOT_ID = "root"
private const val NODE_ARTISTS = "artists"
private const val NODE_ALBUMS = "albums"
private const val NODE_PLAYLISTS = "playlists"
private const val NODE_GENRES = "genres"
private const val NODE_RADIO = "radio"
private const val NODE_FAVORITES = "favorites"

/**
 * Android Auto's browse tree (also serves any other MediaBrowser client — Assistant, Wear, a
 * generic MediaController app). Every browsable node's children come from a one-shot
 * `Flow.first()` read of [LibraryRepository]'s already multi-server-scoped queries — the same
 * combined-across-visible-servers view the phone's own Library tab uses, so Auto's tree matches
 * the phone app with no separate per-server disambiguation logic needed.
 *
 * Playable leaves carry only a stable `mediaId` (no real stream URL) while browsing — the actual
 * playable [MediaItem] (stream URL, artwork, MIME type) is resolved lazily in [onAddMediaItems],
 * the standard Media3/UAMP pattern, via the exact same builders [PlaybackEngine]'s own queue uses
 * ([resolveTrackMediaItem], [resolveRadioMediaItem] — passed in rather than duplicated, so both
 * surfaces always agree on how a track/station becomes playable).
 */
class AutoLibrarySessionCallback(
    private val context: Context,
    private val scope: CoroutineScope,
    private val libraryRepository: LibraryRepository,
    private val serverRepository: ServerRepository,
    private val playlistSubsonicClient: PlaylistSubsonicClient,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val resolveTrackMediaItem: (TrackEntity) -> MediaItem,
    private val resolveRadioMediaItem: (RadioStationEntity) -> MediaItem
) : MediaLibrarySession.Callback {

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        LibraryResult.ofItem(
            browsableItem(ROOT_ID, context.getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            params
        )
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        resolveSingleItem(mediaId)?.let { LibraryResult.ofItem(it, null) }
            ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val children = childrenFor(parentId)
        val paged = children.drop(page * pageSize).take(pageSize)
        LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
    }

    /** Resolves the placeholder [MediaItem]s a browse tap hands back (each carrying only the
     *  `mediaId` [onGetChildren] gave out) into real playable items — the default
     *  `onSetMediaItems` (used for "play this whole list starting at index N") delegates straight
     *  here, so this one override covers both a single tap and a multi-item queue. */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<List<MediaItem>> = scope.future {
        mediaItems.mapNotNull { resolvePlayable(it.mediaId) }
    }

    private suspend fun resolvePlayable(mediaId: String): MediaItem? {
        val prefix = mediaId.substringBefore(':', "")
        val rest = mediaId.substringAfter(':', "")
        return when (prefix) {
            "track" -> libraryRepository.getTracksByCompositeIds(listOf(rest)).firstOrNull()?.let(resolveTrackMediaItem)
            "radiostation" -> libraryRepository.observeRadioStations().first().find { it.id == rest }?.let(resolveRadioMediaItem)
            else -> null
        }
    }

    private suspend fun resolveSingleItem(mediaId: String): MediaItem? {
        val prefix = mediaId.substringBefore(':', "")
        val rest = mediaId.substringAfter(':', "")
        return when (prefix) {
            "track" -> libraryRepository.getTracksByCompositeIds(listOf(rest)).firstOrNull()?.let(::trackPlaceholder)
            "radiostation" -> libraryRepository.observeRadioStations().first().find { it.id == rest }?.let(::radioPlaceholder)
            "artist" -> libraryRepository.getArtistById(rest)?.let(::artistItem)
            "album" -> libraryRepository.getAlbumById(rest)?.let(::albumItem)
            "playlist" -> libraryRepository.getPlaylistById(rest)?.let(::playlistItem)
            "genre" -> Uri.decode(rest).takeIf(String::isNotEmpty)?.let(::genreItem)
            else -> null
        }
    }

    private suspend fun childrenFor(parentId: String): List<MediaItem> = when {
        parentId == ROOT_ID -> rootChildren()
        parentId == NODE_ARTISTS -> libraryRepository.observeArtists().first()
            .sortedBy { it.name.lowercase() }.map(::artistItem)
        parentId == NODE_ALBUMS -> libraryRepository.observeAlbums().first()
            .sortedBy { it.name.lowercase() }.map(::albumItem)
        parentId == NODE_PLAYLISTS -> libraryRepository.observePlaylists().first()
            .sortedBy { it.name.lowercase() }.map(::playlistItem)
        parentId == NODE_GENRES -> libraryRepository.observeGenres().first()
            .sorted().map(::genreItem)
        parentId == NODE_RADIO -> libraryRepository.observeRadioStations().first()
            .sortedBy { it.name.lowercase() }.map(::radioPlaceholder)
        parentId == NODE_FAVORITES -> libraryRepository.observeFavoriteTracks().first()
            .map(::trackPlaceholder)
        parentId.startsWith("artist:") -> libraryRepository.observeAlbumsByArtist(parentId.removePrefix("artist:")).first()
            .map(::albumItem)
        parentId.startsWith("album:") -> libraryRepository.observeTracksByAlbum(parentId.removePrefix("album:")).first()
            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
            .map(::trackPlaceholder)
        parentId.startsWith("playlist:") -> playlistTracks(parentId.removePrefix("playlist:")).map(::trackPlaceholder)
        parentId.startsWith("genre:") -> {
            val genre = Uri.decode(parentId.removePrefix("genre:"))
            libraryRepository.observeTracksByGenre(genre).first().map(::trackPlaceholder)
        }
        else -> emptyList()
    }

    private suspend fun rootChildren(): List<MediaItem> {
        val showRadio = serverRepository.observeServers().first().any { it.mightSupportRadio }
        return buildList {
            add(browsableItem(NODE_ARTISTS, context.getString(R.string.library_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, contentStyleHint = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM))
            add(browsableItem(NODE_ALBUMS, context.getString(R.string.library_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, contentStyleHint = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM))
            add(browsableItem(NODE_PLAYLISTS, context.getString(R.string.tab_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, contentStyleHint = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM))
            add(browsableItem(NODE_GENRES, context.getString(R.string.library_genres), MediaMetadata.MEDIA_TYPE_FOLDER_GENRES, contentStyleHint = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM))
            if (showRadio) add(browsableItem(NODE_RADIO, context.getString(R.string.library_radio), MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS, contentStyleHint = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM))
            add(browsableItem(NODE_FAVORITES, context.getString(R.string.library_favorites), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, contentStyleHint = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM))
        }
    }

    /** Mirrors [de.ilazlow.velosonic.ui.playlists.PlaylistDetailViewModel]'s exact heal pattern —
     *  a playlist can list tracks the normal artist/album sync crawl never found (confirmed live
     *  against a Tidal-wrapper OpenSubsonic proxy), so anything missing locally is fetched once via
     *  a full `getPlaylist` round-trip and persisted, same as the phone UI's own playlist screen. */
    private suspend fun playlistTracks(playlistCompositeId: String): List<TrackEntity> {
        val playlist = libraryRepository.getPlaylistById(playlistCompositeId) ?: return emptyList()
        val compositeIds = playlist.trackIds.map { compositeId(playlist.serverHost, it) }
        val fetchedById = libraryRepository.getTracksByCompositeIds(compositeIds).associateBy { it.subsonicId }
        val missingIds = playlist.trackIds.filter { it !in fetchedById }
        if (missingIds.isEmpty()) return playlist.trackIds.mapNotNull { fetchedById[it] }
        val healed = healMissingTracks(playlist, missingIds)
        return playlist.trackIds.mapNotNull { fetchedById[it] ?: healed[it] }
    }

    private suspend fun healMissingTracks(playlist: PlaylistEntity, missingIds: List<String>): Map<String, TrackEntity> {
        val config = playlistSubsonicClient.configFor(playlist.serverHost) ?: return emptyMap()
        val detail = playlistSubsonicClient.getPlaylistDetail(config, playlist.subsonicId) ?: return emptyMap()
        val missingSet = missingIds.toSet()
        val healedEntities = detail.entry.orEmpty()
            .filter { it.id in missingSet }
            .map { it.toStandaloneEntity(playlist.serverHost) }
        if (healedEntities.isNotEmpty()) libraryRepository.upsertTracks(healedEntities)
        return healedEntities.associateBy { it.subsonicId }
    }

    // ── MediaItem builders ──────────────────────────────────────────────────────

    private fun browsableItem(
        id: String,
        title: String,
        mediaType: Int,
        subtitle: String? = null,
        artworkUri: Uri? = null,
        contentStyleHint: Int? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .apply { subtitle?.let { setSubtitle(it) } }
            .apply { artworkUri?.let { setArtworkUri(it) } }
            .apply {
                if (contentStyleHint != null) {
                    setExtras(Bundle().apply {
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, contentStyleHint)
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, contentStyleHint)
                    })
                }
            }
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    private fun artistItem(artist: ArtistEntity): MediaItem = browsableItem(
        id = "artist:${artist.id}",
        title = artist.name,
        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
        artworkUri = coverArtUri(artist.serverHost, artist.subsonicId, 300)
    )

    private fun albumItem(album: AlbumEntity): MediaItem = browsableItem(
        id = "album:${album.id}",
        title = album.name,
        subtitle = album.artistName,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
        artworkUri = coverArtUri(album.serverHost, album.coverArt, 300)
    )

    private fun playlistItem(playlist: PlaylistEntity): MediaItem = browsableItem(
        id = "playlist:${playlist.id}",
        title = playlist.name,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        artworkUri = coverArtUri(playlist.serverHost, playlist.coverArt, 300)
    )

    private fun genreItem(genre: String): MediaItem = browsableItem(
        id = "genre:${Uri.encode(genre)}",
        title = genre,
        mediaType = MediaMetadata.MEDIA_TYPE_GENRE
    )

    private fun trackPlaceholder(track: TrackEntity): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .apply { track.albumName?.let { setAlbumTitle(it) } }
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { coverArtUri(track.serverHost, track.coverArt, 300)?.let { setArtworkUri(it) } }
            .build()
        return MediaItem.Builder().setMediaId("track:${track.id}").setMediaMetadata(metadata).build()
    }

    private fun radioPlaceholder(station: RadioStationEntity): MediaItem {
        val artworkUri = station.coverArt?.let { coverArtUri(station.serverHost, "ra-${station.subsonicId}", 300) }
        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .apply { artworkUri?.let { setArtworkUri(it) } }
            .build()
        return MediaItem.Builder().setMediaId("radiostation:${station.id}").setMediaMetadata(metadata).build()
    }

    /** Android Auto rejects plain `https://` artwork URLs for browse-tree/queue items outright —
     *  only local `content://`/`android.resource://` URIs are accepted. [CoverArtContentProvider]
     *  bridges the gap: it resolves this same host/id/size triple to the real Navidrome URL and
     *  fetches+caches it on demand when Auto actually requests the bytes. Returns null (no artwork
     *  shown, not a broken image) when the id is absent or the host has no resolvable server
     *  config at all — building a content:// URI the provider could never fetch is worse than
     *  showing Auto's generic placeholder icon. */
    private fun coverArtUri(serverHost: String, coverArtId: String?, size: Int): Uri? {
        if (coverArtId == null) return null
        coverArtUrlResolver.configFor(serverHost) ?: return null
        return Uri.parse("content://${CoverArtContentProvider.AUTHORITY}/${Uri.encode(serverHost)}/${Uri.encode(coverArtId)}/$size")
    }
}
