package de.ilazlow.velosonic.data.network

import de.ilazlow.velosonic.data.network.dto.LrcLibResponseDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/** lrclib.net's free/keyless lyrics lookup — the shared [Retrofit] instance's base URL is a
 *  placeholder (every call here already needs an absolute URL, same as [SubsonicApi]), so a
 *  fully-qualified URL in the `@GET` path works and just overrides it. The explicit User-Agent
 *  is load-bearing, not cosmetic: lrclib's Cloudflare front end returns a bare HTTP 520 for
 *  OkHttp's default "okhttp/x.y.z" UA (a generic-HTTP-client bot signature), silently failing
 *  every request — confirmed live by curling the same query with/without that UA. */
interface LrcLibApi {
    @Headers("User-Agent: VeloSonic (https://github.com/ilazlow/VeloSonic)")
    @GET("https://lrclib.net/api/get")
    suspend fun get(
        @Query("artist_name") artistName: String,
        @Query("track_name") trackName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSeconds: Int? = null
    ): LrcLibResponseDto
}
