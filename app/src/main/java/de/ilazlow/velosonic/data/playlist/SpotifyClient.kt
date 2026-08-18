package de.ilazlow.velosonic.data.playlist

import android.util.Base64
import de.ilazlow.velosonic.data.network.SpotifyApi
import de.ilazlow.velosonic.data.security.ApiKeyStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spotify Web API client for playlist import — mirrors iOS's `SpotifyManager` actor: OAuth
 * (Client Credentials, user-supplied ID/secret from Settings → API), playlist-URL parsing, and
 * paginated track fetching. The access token is cached in memory only (not persisted — a fresh
 * app launch just requests a new one), refreshed a little before it actually expires.
 */
@Singleton
class SpotifyClient @Inject constructor(
    private val api: SpotifyApi,
    private val apiKeyStore: ApiKeyStore
) {
    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0L

    private suspend fun accessToken(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { now < tokenExpiryMs }?.let { return@withLock it }

        val clientId = apiKeyStore.loadSpotifyClientId()?.takeIf { it.isNotBlank() }
        val clientSecret = apiKeyStore.loadSpotifyClientSecret()?.takeIf { it.isNotBlank() }
        if (clientId == null || clientSecret == null) throw PlaylistImportException.MissingCredentials()

        val credentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val response = try {
            api.getAccessToken("Basic $credentials")
        } catch (e: Exception) {
            throw PlaylistImportException.AuthFailed()
        }
        cachedToken = response.accessToken
        // 30s safety margin, mirrors iOS.
        tokenExpiryMs = now + (response.expiresIn - 30).coerceAtLeast(0) * 1000L
        response.accessToken
    }

    /** Playlist metadata — a 404 means this playlist isn't reachable without a user login
     *  (Spotify's own algorithmic playlists: Daily Mix, Discover Weekly, editorial). */
    suspend fun fetchPlaylistInfo(playlistId: String): ImportPlaylistInfo {
        val token = accessToken()
        val response = try {
            api.getPlaylist(playlistId, "Bearer $token")
        } catch (e: Exception) {
            throw PlaylistImportException.FetchFailed(e.message ?: "network error")
        }
        if (response.code() == 404) throw PlaylistImportException.PlaylistNotAccessible()
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw PlaylistImportException.FetchFailed("HTTP ${response.code()}")
        }
        return ImportPlaylistInfo(
            id = playlistId,
            name = body.name ?: "Unknown Playlist",
            description = body.description,
            artworkUrl = body.images?.firstOrNull()?.url,
            totalTracks = body.tracks?.total ?: 0,
            ownerName = body.owner?.displayName
        )
    }

    /** All tracks, paginated 100 at a time — skips entries with no track (local files, or a
     *  podcast episode/track removed from the catalog since the playlist was made). */
    suspend fun fetchAllTracks(playlistId: String): List<ImportSourceTrack> {
        val token = accessToken()
        val fields = "total,next,items(track(id,name,duration_ms,artists(name),album(name,images)))"
        val tracks = mutableListOf<ImportSourceTrack>()
        var offset = 0
        while (true) {
            val response = try {
                api.getPlaylistTracks(playlistId, "Bearer $token", limit = 100, offset = offset, fields = fields)
            } catch (e: Exception) {
                throw PlaylistImportException.FetchFailed(e.message ?: "network error")
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw PlaylistImportException.FetchFailed("HTTP ${response.code()}")
            }
            val items = body.items.orEmpty()
            for (item in items) {
                val track = item.track ?: continue
                val id = track.id ?: continue
                val title = track.name ?: continue
                tracks += ImportSourceTrack(
                    id = id,
                    title = title,
                    artist = track.artists.orEmpty().joinToString(", ") { it.name },
                    album = track.album?.name,
                    artworkUrl = track.album?.images?.firstOrNull()?.url,
                    durationMs = track.durationMs ?: 0
                )
            }
            val hasNext = body.next != null
            offset += items.size
            if (!hasNext || items.isEmpty()) break
        }
        return tracks
    }

    /** Extracts a playlist id from a pasted Spotify link or URI — handles
     *  `https://open.spotify.com/playlist/<id>` (with or without a query string, or a locale
     *  path prefix like `/intl-de/`) and `spotify:playlist:<id>`. */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("spotify:playlist:")) {
            return trimmed.split(":").getOrNull(2)
        }
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return null
        }
        val segments = uri.path?.split("/").orEmpty()
        val index = segments.indexOf("playlist")
        if (index == -1 || index + 1 >= segments.size) return null
        return segments[index + 1].takeIf { it.isNotBlank() }
    }

    /** Detects whether a pasted string looks like a Spotify link at all — CSV mode is driven by
     *  having picked a file, never by URL text. */
    fun detectSource(input: String): PlaylistImportSource? {
        val lower = input.trim().lowercase()
        return if (lower.contains("spotify.com") || lower.startsWith("spotify:")) PlaylistImportSource.SPOTIFY else null
    }
}
