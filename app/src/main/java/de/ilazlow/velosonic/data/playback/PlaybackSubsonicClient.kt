package de.ilazlow.velosonic.data.playback

import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.network.dto.PlayQueueNodeDto
import de.ilazlow.velosonic.data.network.dto.TrackDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playback-adjacent Subsonic calls that don't belong to sync (scrobbling, similar-song
 * discovery, star/rating, server-side play-queue mirror) — mirrors the handful of
 * NavidromeManager methods AudioPlayerManager calls directly, kept separate from SyncEngine's
 * concerns since these are triggered by playback events, not the sync scheduler.
 */
@Singleton
class PlaybackSubsonicClient @Inject constructor(
    private val api: SubsonicApi,
    private val coverArtUrlResolver: CoverArtUrlResolver
) {
    private fun url(config: ServerConfigEntity, endpoint: String, extra: Map<String, String> = emptyMap()) =
        SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra)

    fun configFor(serverHost: String): ServerConfigEntity? = coverArtUrlResolver.configFor(serverHost)

    fun streamUrlFor(serverHost: String, trackSubsonicId: String): String? =
        coverArtUrlResolver.streamUrlFor(serverHost, trackSubsonicId)

    /** Traditional Subsonic scrobble — updates Navidrome's play count/history and, if
     *  configured server-side, submits to Last.fm. `submission=false` is the "now playing"
     *  ping variant; `true` records the play. `time` (epoch ms) lets a scrobble that was queued
     *  offline and only just flushed (see [de.ilazlow.velosonic.data.playback.ScrobbleQueue])
     *  still register at the moment it was actually played rather than whenever it happened to
     *  finally send — mirrors iOS's `NavidromeManager.scrobble(time:)`. */
    suspend fun scrobble(config: ServerConfigEntity, trackId: String, submission: Boolean, time: Long? = null): Boolean = try {
        val params = buildMap {
            put("id", trackId)
            put("submission", submission.toString())
            if (time != null) put("time", time.toString())
        }
        api.get(url(config, "scrobble", params)).subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    /** OpenSubsonic/Navidrome extension — richer than scrobble: carries live position and
     *  playback state ("starting"/"playing"/"paused"/"stopped"), letting admin now-playing
     *  views extrapolate a real position instead of a static one. Falls back to plain
     *  scrobble(submission=false) for servers that don't support it.
     *
     *  Param names verified against a live Navidrome 0.62 server — confirmed live: sending the
     *  generic Subsonic `id`/`playerState` names (matching every other endpoint's convention)
     *  gets HTTP 200 but a Subsonic-level `status: "failed"` / error code 10 "missing parameter:
     *  'mediaId'", since this specific extension actually expects `mediaId`/`mediaType`/`state`/
     *  `playbackRate` — every reportPlayback call was silently failing end-to-end until this was
     *  corrected, which is why nothing ever showed up in Navidrome's Now Playing. */
    suspend fun reportPlayback(config: ServerConfigEntity, trackId: String, positionMs: Int, state: String): Boolean = try {
        api.get(
            url(
                config,
                "reportPlayback",
                mapOf(
                    "mediaId" to trackId,
                    "mediaType" to "song",
                    "positionMs" to positionMs.toString(),
                    "state" to state,
                    "playbackRate" to "1.0"
                )
            )
        ).subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    suspend fun star(config: ServerConfigEntity, trackId: String): Boolean = try {
        api.get(url(config, "star", mapOf("id" to trackId))).subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    suspend fun unstar(config: ServerConfigEntity, trackId: String): Boolean = try {
        api.get(url(config, "unstar", mapOf("id" to trackId))).subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    suspend fun setRating(config: ServerConfigEntity, trackId: String, rating: Int): Boolean = try {
        api.get(url(config, "setRating", mapOf("id" to trackId, "rating" to rating.toString())))
            .subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }

    suspend fun similarSongs(config: ServerConfigEntity, trackId: String, count: Int): List<TrackDto> = try {
        api.get(url(config, "getSimilarSongs2", mapOf("id" to trackId, "count" to count.toString())))
            .subsonicResponse?.similarSongs2?.song.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** Navidrome >= 0.62 with the AudioMuse-AI WASM plugin — see ServerConfigEntity's
     *  supportsSonicSimilarity-equivalent check at the call site before relying on this. */
    suspend fun sonicSimilarTracks(config: ServerConfigEntity, trackId: String, count: Int): List<TrackDto> = try {
        api.get(url(config, "getSonicSimilarTracks", mapOf("id" to trackId, "count" to count.toString())))
            .subsonicResponse?.sonicSimilarTracks?.sonicMatch.orEmpty().map { it.entry }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun randomSongs(config: ServerConfigEntity, count: Int, genre: String? = null): List<TrackDto> = try {
        val extra = buildMap {
            put("size", count.toString())
            if (!genre.isNullOrEmpty()) put("genre", genre)
        }
        api.get(url(config, "getRandomSongs", extra)).subsonicResponse?.randomSongs?.song.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** Mirrors iOS's `loadQueueFromServer` — the OpenSubsonic-side counterpart of [savePlayQueue]:
     *  fetches whatever queue/position was last saved (by this device or another client), so a
     *  user can pick up mid-song on a different device. Home's manual "load queue" toolbar action
     *  is the only caller — there's no auto-load on launch, matching iOS. */
    suspend fun getPlayQueue(config: ServerConfigEntity): PlayQueueNodeDto? = try {
        api.get(url(config, "getPlayQueue")).subsonicResponse?.playQueue
    } catch (e: Exception) {
        null
    }

    suspend fun savePlayQueue(config: ServerConfigEntity, trackIds: List<String>, currentId: String, positionMs: Int) {
        try {
            // Repeated "id" params need real multi-value query support — SubsonicUrlBuilder's
            // Map<String,String> can't express that, so build this one URL by hand.
            val base = SubsonicUrlBuilder.build(config.host, "savePlayQueue", config.username, config.token, config.salt)
            val withIds = trackIds.fold(base) { acc, id -> "$acc&id=${java.net.URLEncoder.encode(id, "UTF-8")}" }
            val full = "$withIds&current=${java.net.URLEncoder.encode(currentId, "UTF-8")}&position=$positionMs"
            api.get(full)
        } catch (e: Exception) {
            // Best-effort — losing the server-side queue mirror is not user-visible.
        }
    }
}
