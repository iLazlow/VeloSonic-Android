package de.ilazlow.velosonic.data.search

import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.network.dto.SearchResult3NodeDto
import javax.inject.Inject
import javax.inject.Singleton

/** `search3` for the main Search tab — a live per-server query, distinct from
 *  [de.ilazlow.velosonic.data.playlist.PlaylistSubsonicClient.searchTracks]'s songs-only variant
 *  used by playlist-import matching. Returns null on any failure (offline, timeout, server error)
 *  so [de.ilazlow.velosonic.ui.search.SearchViewModel] can fall back to the local synced library
 *  for that host rather than showing an error. */
@Singleton
class SearchSubsonicClient @Inject constructor(
    private val api: SubsonicApi
) {
    private fun url(config: ServerConfigEntity, endpoint: String, extra: Map<String, String> = emptyMap()) =
        SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra)

    suspend fun search3(
        config: ServerConfigEntity,
        query: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 25
    ): SearchResult3NodeDto? = try {
        api.get(
            url(
                config,
                "search3",
                mapOf(
                    "query" to query,
                    "artistCount" to artistCount.toString(),
                    "albumCount" to albumCount.toString(),
                    "songCount" to songCount.toString()
                )
            )
        ).subsonicResponse?.searchResult3
    } catch (e: Exception) {
        null
    }
}
