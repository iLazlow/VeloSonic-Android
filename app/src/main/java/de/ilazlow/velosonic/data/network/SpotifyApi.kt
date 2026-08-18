package de.ilazlow.velosonic.data.network

import de.ilazlow.velosonic.data.network.dto.SpotifyAuthTokenDto
import de.ilazlow.velosonic.data.network.dto.SpotifyPlaylistDto
import de.ilazlow.velosonic.data.network.dto.SpotifyPlaylistTracksPageDto
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Spotify Web API — Client Credentials flow only (no user login), matching iOS's
 * `SpotifyManager`: enough to read a *public* playlist's metadata/tracks for playlist import,
 * nothing that needs a signed-in Spotify user (which is also why Spotify's own algorithmic
 * playlists — Daily Mix, Discover Weekly, editorial — 404 here and aren't importable). Every
 * endpoint here is fixed, not per-server, so each annotation carries its own full URL directly
 * rather than going through [SubsonicUrlBuilder] — this overrides the shared Retrofit instance's
 * otherwise-unused placeholder base URL, same as every other API interface in this app.
 */
interface SpotifyApi {
    @FormUrlEncoded
    @POST("https://accounts.spotify.com/api/token")
    suspend fun getAccessToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): SpotifyAuthTokenDto

    /** Deliberately no `fields` filter — fetches the full metadata object, matching iOS's
     *  comment that a narrower field list has caused encoding/compatibility issues before. */
    @GET("https://api.spotify.com/v1/playlists/{id}")
    suspend fun getPlaylist(
        @Path("id") playlistId: String,
        @Header("Authorization") authorization: String
    ): Response<SpotifyPlaylistDto>

    @GET("https://api.spotify.com/v1/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("id") playlistId: String,
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("fields") fields: String
    ): Response<SpotifyPlaylistTracksPageDto>
}
