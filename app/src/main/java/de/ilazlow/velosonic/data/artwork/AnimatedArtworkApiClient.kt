package de.ilazlow.velosonic.data.artwork

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AnimatedArtworkApi"

/** Mirrors iOS's `_AppleArtworkAPIResponseProxy`/`AppleArtworkAPIResponse` — `url` is the 1x1
 *  (square) HLS master playlist, `urlTall` the 3x4 variant (unused here since the tall variant
 *  only exists to feed iOS's lock-screen surface, out of scope for this port). */
@Serializable
data class AnimatedArtworkApiResponse(
    val url: String? = null,
    @SerialName("url_tall") val urlTall: String? = null
)

/**
 * Calls the user-configurable third-party "Apple animated artwork" endpoint (default
 * `ama.trainswift.net`, see [de.ilazlow.velosonic.data.datastore.DEFAULT_ANIMATED_ARTWORK_API_URL])
 * — mirrors `AppearanceSettings.artworkApiURL(artist:album:title:)` + the fetch half of
 * `AudioPlayerManager.fetchAppleAnimatedURLs`. This is an unofficial, unowned external service
 * with no availability guarantee; every caller must treat a null/failed result as a normal,
 * silent "no animated artwork available" outcome, never a hard error.
 */
@Singleton
class AnimatedArtworkApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun fetch(apiUrlTemplate: String, artist: String, album: String, title: String): AnimatedArtworkApiResponse? =
        withContext(Dispatchers.IO) {
            val url = buildUrl(apiUrlTemplate, artist, album, title) ?: run {
                Log.w(TAG, "fetch: could not build a URL from template='$apiUrlTemplate'")
                return@withContext null
            }
            try {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "fetch: HTTP ${response.code} for $url")
                        return@withContext null
                    }
                    val bodyText = response.body?.string() ?: run {
                        Log.w(TAG, "fetch: empty body for $url")
                        return@withContext null
                    }
                    Log.d(TAG, "fetch: $url -> $bodyText")
                    json.decodeFromString<AnimatedArtworkApiResponse>(bodyText)
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetch: exception for $url", e)
                null
            }
        }

    private fun buildUrl(template: String, artist: String, album: String, title: String): okhttp3.HttpUrl? {
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        val filled = template
            .replace("{artistName}", encode(artist))
            .replace("{albumName}", encode(album))
            .replace("{songName}", encode(title))
        return filled.toHttpUrlOrNull()
    }
}
