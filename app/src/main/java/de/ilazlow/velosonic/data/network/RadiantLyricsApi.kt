package de.ilazlow.velosonic.data.network

import de.ilazlow.velosonic.data.network.dto.RadiantLyricsResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/** api.atomix.one's word-synced lyrics lookup — mirrors iOS's RadiantLyricsManager.buildURL.
 *  Auth headers are passed per-call (not a static `@Headers` annotation) since they come from
 *  [de.ilazlow.velosonic.BuildConfig] at runtime, not a compile-time literal. */
interface RadiantLyricsApi {
    @GET("https://api.atomix.one/rl-api")
    suspend fun get(
        @Header("P-Access-Token-Id") tokenId: String,
        @Header("P-Access-Token") token: String,
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("isrc") isrc: String? = null,
        @Query("romanize") romanize: Boolean = false,
        // Only sent when true — omitted (not "false") when off, matching iOS's buildURL, which
        // never appends the param at all unless the user opted in.
        @Query("synthesize") synthesize: Boolean? = null,
        @Query("platform") platform: String = "VeloSonic"
    ): RadiantLyricsResponseDto
}
