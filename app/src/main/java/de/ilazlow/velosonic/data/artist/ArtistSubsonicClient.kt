package de.ilazlow.velosonic.data.artist

import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.network.dto.ArtistInfoNodeDto
import de.ilazlow.velosonic.data.network.dto.TrackDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two Artist Detail calls that have no local cache to fall back on (unlike albums, which
 * Room already has via the synced library) — mirrors NavidromeManager's `fetchTopSongs`/
 * `fetchArtistInfo`. Star/unstar reuse [de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient]
 * (the plain Subsonic `star`/`unstar` endpoints take any entity id, artist included).
 */
@Singleton
class ArtistSubsonicClient @Inject constructor(
    private val api: SubsonicApi,
    private val coverArtUrlResolver: CoverArtUrlResolver
) {
    private fun url(config: ServerConfigEntity, endpoint: String, extra: Map<String, String> = emptyMap()) =
        SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra)

    fun configFor(serverHost: String): ServerConfigEntity? = coverArtUrlResolver.configFor(serverHost)

    /** Subsonic's `getTopSongs` is keyed by artist *name*, not id — matches iOS's
     *  `fetchTopSongs(artistName:)`. */
    suspend fun fetchTopSongs(config: ServerConfigEntity, artistName: String, count: Int = 15): List<TrackDto> = try {
        api.get(url(config, "getTopSongs", mapOf("artist" to artistName, "count" to count.toString())))
            .subsonicResponse?.topSongs?.song.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** OpenSubsonic/Navidrome `getArtistInfo2` — biography + similar artists, last.fm-backed. */
    suspend fun fetchArtistInfo(config: ServerConfigEntity, artistId: String, count: Int = 10): ArtistInfoNodeDto? = try {
        api.get(url(config, "getArtistInfo2", mapOf("id" to artistId, "count" to count.toString(), "includeNotPresent" to "true")))
            .subsonicResponse?.artistInfo2
    } catch (e: Exception) {
        null
    }
}
