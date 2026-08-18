package de.ilazlow.velosonic.data.playlist

import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.network.dto.PlaylistDto
import de.ilazlow.velosonic.data.network.dto.TrackDto
import javax.inject.Inject
import javax.inject.Singleton

/** A single `updatePlaylist` call with too many repeated `songIdToAdd` params risks hitting a
 *  server/proxy URL-length limit — mirrors iOS's `NavidromeManager.importPlaylist`'s batch size. */
private const val BATCH_SIZE = 50

/**
 * Playlist CRUD + track add/remove against Subsonic's `createPlaylist`/`updatePlaylist`/
 * `deletePlaylist` — mirrors PlaybackSubsonicClient's shape (try/catch-Boolean, one `url()`
 * helper per config) since these are the same kind of one-shot, fire-and-forget server calls.
 */
@Singleton
class PlaylistSubsonicClient @Inject constructor(
    private val api: SubsonicApi,
    private val coverArtUrlResolver: CoverArtUrlResolver
) {
    private fun url(
        config: ServerConfigEntity,
        endpoint: String,
        extra: Map<String, String> = emptyMap(),
        repeated: List<Pair<String, String>> = emptyList()
    ) = SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra, repeatedParams = repeated)

    fun configFor(serverHost: String): ServerConfigEntity? = coverArtUrlResolver.configFor(serverHost)

    fun allConfigs(): List<ServerConfigEntity> = coverArtUrlResolver.allConfigs()

    suspend fun createPlaylist(config: ServerConfigEntity, name: String): PlaylistDto? = try {
        api.get(url(config, "createPlaylist", mapOf("name" to name))).subsonicResponse?.playlist
    } catch (e: Exception) {
        null
    }

    /** Full playlist detail including every track (`entry`) — used to heal tracks a playlist
     *  references that the normal artist/album sync crawl never found (see
     *  [de.ilazlow.velosonic.ui.playlists.PlaylistDetailViewModel]'s doc comment on why that
     *  happens for some servers, e.g. Tidal-wrapper OpenSubsonic proxies whose playlists can
     *  reference catalog tracks outside the user's indexed "artists" library). */
    suspend fun getPlaylistDetail(config: ServerConfigEntity, playlistId: String): PlaylistDto? = try {
        api.get(url(config, "getPlaylist", mapOf("id" to playlistId))).subsonicResponse?.playlist
    } catch (e: Exception) {
        null
    }

    suspend fun deletePlaylist(config: ServerConfigEntity, playlistId: String): Boolean = try {
        api.get(url(config, "deletePlaylist", mapOf("id" to playlistId))).subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    suspend fun renamePlaylist(config: ServerConfigEntity, playlistId: String, name: String, isPublic: Boolean?): Boolean = try {
        val extra = buildMap {
            put("playlistId", playlistId)
            put("name", name)
            if (isPublic != null) put("public", isPublic.toString())
        }
        api.get(url(config, "updatePlaylist", extra)).subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    suspend fun addTrack(config: ServerConfigEntity, playlistId: String, trackId: String): Boolean = try {
        api.get(url(config, "updatePlaylist", mapOf("playlistId" to playlistId, "songIdToAdd" to trackId)))
            .subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    suspend fun removeTrackAt(config: ServerConfigEntity, playlistId: String, trackIndex: Int): Boolean = try {
        api.get(url(config, "updatePlaylist", mapOf("playlistId" to playlistId, "songIndexToRemove" to trackIndex.toString())))
            .subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    /** `search3` scoped to songs only (album/artist counts pinned to 0) — used by playlist
     *  import's track-matching pass, `songCount` bounding how many candidates come back per
     *  query (import requests 8, mirroring iOS). */
    suspend fun searchTracks(config: ServerConfigEntity, query: String, songCount: Int = 8): List<TrackDto> = try {
        api.get(
            url(
                config,
                "search3",
                mapOf("query" to query, "songCount" to songCount.toString(), "artistCount" to "0", "albumCount" to "0")
            )
        ).subsonicResponse?.searchResult3?.song.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** Every playlist on this server — used by playlist import to detect a same-named playlist
     *  to update in place instead of creating a duplicate. */
    suspend fun fetchPlaylists(config: ServerConfigEntity): List<PlaylistDto> = try {
        api.get(url(config, "getPlaylists")).subsonicResponse?.playlists?.playlist.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Creates a brand-new playlist, or — when [existingPlaylistId] is set — replaces an existing
     * one's contents in place (`createPlaylist` with both `playlistId` and `songId` set has
     * *replace* semantics on Subsonic servers, keeping the playlist's own id intact rather than
     * deleting and recreating it, so anything referencing that id keeps working). [trackIds] is
     * sent in [BATCH_SIZE]-sized chunks to keep any one request's URL length sane. Mirrors iOS's
     * `NavidromeManager.importPlaylist`. Every batch after the first is fire-and-forget, same as
     * iOS — a partially-imported playlist is still more useful than failing the whole import over
     * one bad batch. Returns the resulting playlist's subsonic id, or null if creation (or the
     * first replace batch) failed outright.
     */
    suspend fun importTracks(
        config: ServerConfigEntity,
        name: String,
        trackIds: List<String>,
        existingPlaylistId: String?
    ): String? {
        val playlistId: String
        val remaining: List<String>
        if (existingPlaylistId != null) {
            val firstBatch = trackIds.take(BATCH_SIZE)
            val ok = try {
                api.get(
                    url(
                        config,
                        "createPlaylist",
                        mapOf("playlistId" to existingPlaylistId, "name" to name),
                        firstBatch.map { "songId" to it }
                    )
                ).subsonicResponse?.status == "ok"
            } catch (e: Exception) {
                false
            }
            if (!ok) return null
            playlistId = existingPlaylistId
            remaining = trackIds.drop(BATCH_SIZE)
        } else {
            playlistId = createPlaylist(config, name)?.id ?: return null
            remaining = trackIds
        }

        remaining.chunked(BATCH_SIZE).forEach { batch ->
            try {
                api.get(url(config, "updatePlaylist", mapOf("playlistId" to playlistId), batch.map { "songIdToAdd" to it }))
            } catch (e: Exception) {
                // Best-effort — see doc comment above.
            }
        }
        return playlistId
    }
}
