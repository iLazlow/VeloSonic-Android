package de.ilazlow.velosonic.data.network

import de.ilazlow.velosonic.data.network.dto.SubsonicIndexesResponseDto
import de.ilazlow.velosonic.data.network.dto.SubsonicResponseDto
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Url

@Serializable
data class NavidromeLoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class NavidromeLoginResponseDto(
    val token: String
)

@Serializable
data class UpdateShareRequestDto(
    val downloadable: Boolean
)

/**
 * Every call takes a fully-built absolute URL (see SubsonicUrlBuilder) rather than a fixed
 * base URL — there is no implicit "current server" client, matching the multi-server design.
 */
interface SubsonicApi {
    @GET
    suspend fun get(@Url url: String): SubsonicResponseDto

    /** getArtists.view / getIndexes.view use a distinct "index of indexes" envelope shape. */
    @GET
    suspend fun getArtistIndexes(@Url url: String): SubsonicIndexesResponseDto

    @POST
    suspend fun login(@Url url: String, @Body body: NavidromeLoginRequest): NavidromeLoginResponseDto

    /** Navidrome-native API (not classic Subsonic REST) — JWT bearer auth via header instead of
     *  u/t/s query params. Used for the one thing plain `createShare`'s `downloadAllowed` param
     *  can't do on a real Navidrome server (it silently ignores that param). Raw [Response] so
     *  the caller can inspect the status code directly (401 means the cached JWT expired). */
    @PUT
    suspend fun updateShare(
        @Url url: String,
        @Header("x-nd-authorization") authorization: String,
        @Body body: UpdateShareRequestDto
    ): Response<Unit>

    /** Navidrome-native API — uploads/replaces a playlist's own cover image, used by playlist
     *  import to carry over a Spotify playlist's artwork. Same JWT-bearer auth as [updateShare]. */
    @Multipart
    @POST
    suspend fun uploadPlaylistArtwork(
        @Url url: String,
        @Header("x-nd-authorization") authorization: String,
        @Part image: MultipartBody.Part
    ): Response<Unit>

    /** Navidrome-native smart-playlist API (`/api/playlist/{id}`) — a raw [ResponseBody] rather
     *  than a fixed DTO since the surrounding playlist JSON has many fields we don't care about
     *  and `rules` itself is a genuinely dynamic/heterogeneous shape (see
     *  [de.ilazlow.velosonic.data.playlist.SPCriteria]); callers parse out just what they need
     *  with [kotlinx.serialization.json.Json]. */
    @GET
    suspend fun getNativePlaylist(
        @Url url: String,
        @Header("x-nd-authorization") authorization: String
    ): Response<ResponseBody>

    /** Creates a native (optionally smart) playlist — `POST /api/playlist`, body `{name, public,
     *  rules?}`. Same raw-body rationale as [getNativePlaylist]; the response is just `{id, ...}`. */
    @POST
    suspend fun createNativePlaylist(
        @Url url: String,
        @Header("x-nd-authorization") authorization: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    /** Updates a native playlist's name/public/rules in one call — `PUT /api/playlist/{id}`. */
    @PUT
    suspend fun updateNativePlaylist(
        @Url url: String,
        @Header("x-nd-authorization") authorization: String,
        @Body body: RequestBody
    ): Response<Unit>
}
