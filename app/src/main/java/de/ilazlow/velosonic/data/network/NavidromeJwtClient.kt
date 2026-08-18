package de.ilazlow.velosonic.data.network

import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.security.CredentialStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navidrome-native JWT client — fetches/refreshes a bearer token for the handful of endpoints
 * that only exist on Navidrome's own `/api/...` surface, not classic Subsonic REST (currently:
 * PATCHing a share's downloadable flag). Mirrors NavidromeManager.swift's
 * `validNavidromeJWT`/`fetchNavidromeJWT`/401-refresh-and-retry-once pattern — cached
 * `ServerConfigEntity.navToken` is tried first, and only refreshed (via the stored password,
 * fetched from [CredentialStore]) on a 401.
 */
@Singleton
class NavidromeJwtClient @Inject constructor(
    private val api: SubsonicApi,
    private val serverConfigDao: ServerConfigDao,
    private val credentialStore: CredentialStore
) {
    suspend fun updateShareDownloadable(config: ServerConfigEntity, shareId: String, allowed: Boolean): Boolean {
        var jwt = config.navToken ?: refreshJwt(config) ?: return false
        var response = putShare(config.host, jwt, shareId, allowed)
        if (response?.code() == 401) {
            jwt = refreshJwt(config) ?: return false
            response = putShare(config.host, jwt, shareId, allowed)
        }
        return response?.isSuccessful == true
    }

    private suspend fun putShare(host: String, jwt: String, shareId: String, allowed: Boolean): Response<Unit>? =
        try {
            api.updateShare("$host/api/share/$shareId", "Bearer $jwt", UpdateShareRequestDto(allowed))
        } catch (e: Exception) {
            null
        }

    /** Uploads/replaces [playlistId]'s cover image — used by playlist import to carry over a
     *  Spotify playlist's artwork. [mimeType] should be `image/png` or `image/jpeg`. */
    suspend fun uploadPlaylistArtwork(config: ServerConfigEntity, playlistId: String, imageBytes: ByteArray, mimeType: String): Boolean {
        var jwt = config.navToken ?: refreshJwt(config) ?: return false
        var response = postPlaylistArtwork(config.host, jwt, playlistId, imageBytes, mimeType)
        if (response?.code() == 401) {
            jwt = refreshJwt(config) ?: return false
            response = postPlaylistArtwork(config.host, jwt, playlistId, imageBytes, mimeType)
        }
        return response?.isSuccessful == true
    }

    private suspend fun postPlaylistArtwork(
        host: String,
        jwt: String,
        playlistId: String,
        imageBytes: ByteArray,
        mimeType: String
    ): Response<Unit>? = try {
        val extension = if (mimeType == "image/png") "png" else "jpg"
        val part = MultipartBody.Part.createFormData(
            "image",
            "artwork.$extension",
            imageBytes.toRequestBody(mimeType.toMediaType())
        )
        api.uploadPlaylistArtwork("$host/api/playlist/$playlistId/image", "Bearer $jwt", part)
    } catch (e: Exception) {
        null
    }

    /** Fetches a Navidrome smart playlist's current rules DSL as a raw JSON string (or null if
     *  the playlist has none / isn't smart / the request fails) — used to lazily load rules for
     *  editing when they aren't already cached locally (`PlaylistEntity.rulesJSON`). */
    suspend fun fetchNativePlaylistRules(config: ServerConfigEntity, playlistId: String): String? {
        var jwt = config.navToken ?: refreshJwt(config) ?: return null
        var response = getNativePlaylist(config.host, jwt, playlistId)
        if (response?.code() == 401) {
            jwt = refreshJwt(config) ?: return null
            response = getNativePlaylist(config.host, jwt, playlistId)
        }
        val body = response?.takeIf { it.isSuccessful }?.body()?.string() ?: return null
        val rules = runCatching { Json.parseToJsonElement(body).jsonObject["rules"] }.getOrNull()
        if (rules == null || rules is JsonNull) return null
        return rules.toString()
    }

    private suspend fun getNativePlaylist(host: String, jwt: String, playlistId: String): Response<ResponseBody>? = try {
        api.getNativePlaylist("$host/api/playlist/$playlistId", "Bearer $jwt")
    } catch (e: Exception) {
        null
    }

    /** Creates a playlist via Navidrome's native API — used for smart playlists, whose rules the
     *  classic Subsonic `createPlaylist` has no way to set. Returns the new playlist's Subsonic
     *  ID, or null on failure. [rulesJSON] may be null for a native (non-smart) create. */
    suspend fun createNativeSmartPlaylist(config: ServerConfigEntity, name: String, isPublic: Boolean, rulesJSON: String?): String? {
        var jwt = config.navToken ?: refreshJwt(config) ?: return null
        var response = postNativePlaylist(config.host, jwt, name, isPublic, rulesJSON)
        if (response?.code() == 401) {
            jwt = refreshJwt(config) ?: return null
            response = postNativePlaylist(config.host, jwt, name, isPublic, rulesJSON)
        }
        val body = response?.takeIf { it.isSuccessful }?.body()?.string() ?: return null
        return runCatching { Json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
    }

    private suspend fun postNativePlaylist(
        host: String,
        jwt: String,
        name: String,
        isPublic: Boolean,
        rulesJSON: String?
    ): Response<ResponseBody>? = try {
        api.createNativePlaylist("$host/api/playlist", "Bearer $jwt", buildNativePlaylistBody(name, isPublic, rulesJSON, includeNullRules = false))
    } catch (e: Exception) {
        null
    }

    /** Updates a smart playlist's rules (and name/public) via Navidrome's native API — one PUT
     *  call, replacing what would otherwise be a separate classic `updatePlaylist` call. */
    suspend fun updateNativePlaylistRules(config: ServerConfigEntity, playlistId: String, name: String, isPublic: Boolean, rulesJSON: String?): Boolean {
        var jwt = config.navToken ?: refreshJwt(config) ?: return false
        var response = putNativePlaylist(config.host, jwt, playlistId, name, isPublic, rulesJSON)
        if (response?.code() == 401) {
            jwt = refreshJwt(config) ?: return false
            response = putNativePlaylist(config.host, jwt, playlistId, name, isPublic, rulesJSON)
        }
        return response?.isSuccessful == true
    }

    private suspend fun putNativePlaylist(
        host: String,
        jwt: String,
        playlistId: String,
        name: String,
        isPublic: Boolean,
        rulesJSON: String?
    ): Response<Unit>? = try {
        api.updateNativePlaylist(
            "$host/api/playlist/$playlistId",
            "Bearer $jwt",
            buildNativePlaylistBody(name, isPublic, rulesJSON, includeNullRules = true)
        )
    } catch (e: Exception) {
        null
    }

    /** [includeNullRules] mirrors iOS's own `body["rules"] = NSNull()` on update-without-rules —
     *  create has no analogous case since a brand-new native playlist request never needs to
     *  explicitly clear rules that don't exist yet. */
    private fun buildNativePlaylistBody(name: String, isPublic: Boolean, rulesJSON: String?, includeNullRules: Boolean): RequestBody {
        val rulesElement = rulesJSON?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        val obj = buildJsonObject {
            put("name", JsonPrimitive(name))
            put("public", JsonPrimitive(isPublic))
            when {
                rulesElement != null -> put("rules", rulesElement)
                includeNullRules -> put("rules", JsonNull)
            }
        }
        return obj.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    }

    private suspend fun refreshJwt(config: ServerConfigEntity): String? {
        val password = credentialStore.loadPassword(config.host, config.username) ?: return null
        val jwt = try {
            api.login("${config.host}/auth/login", NavidromeLoginRequest(config.username, password)).token
        } catch (e: Exception) {
            null
        } ?: return null
        serverConfigDao.update(config.copy(navToken = jwt))
        return jwt
    }
}
