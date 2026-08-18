package de.ilazlow.velosonic.data.share

import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.network.dto.SubsonicShareDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subsonic share CRUD (`createShare`/`getShares`/`deleteShare`) — mirrors NavidromeManager.swift's
 * share calls. Only ever shares a single entity at a time (matching how the iOS share sheet
 * actually uses `createShare`'s multi-id capability — never more than one), so `id` is a single
 * query param rather than the repeated-key shape multi-id would need.
 * `downloadAllowed` is sent as a query param here too (per the Subsonic spec) but real Navidrome
 * servers ignore it — see [de.ilazlow.velosonic.data.network.NavidromeJwtClient] for the native
 * PATCH that actually enforces it there.
 */
@Singleton
class ShareSubsonicClient @Inject constructor(
    private val api: SubsonicApi,
    private val coverArtUrlResolver: CoverArtUrlResolver
) {
    private fun url(config: ServerConfigEntity, endpoint: String, extra: Map<String, String> = emptyMap()) =
        SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra)

    fun configFor(serverHost: String): ServerConfigEntity? = coverArtUrlResolver.configFor(serverHost)

    suspend fun createShare(
        config: ServerConfigEntity,
        entityId: String,
        description: String?,
        expiresAtEpochMs: Long?,
        downloadAllowed: Boolean
    ): SubsonicShareDto? = try {
        val extra = buildMap {
            put("id", entityId)
            put("downloadAllowed", downloadAllowed.toString())
            if (!description.isNullOrBlank()) put("description", description)
            if (expiresAtEpochMs != null) put("expires", expiresAtEpochMs.toString())
        }
        api.get(url(config, "createShare", extra)).subsonicResponse?.shares?.share?.firstOrNull()
    } catch (e: Exception) {
        null
    }

    suspend fun getShares(config: ServerConfigEntity): List<SubsonicShareDto> = try {
        api.get(url(config, "getShares")).subsonicResponse?.shares?.share.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun deleteShare(config: ServerConfigEntity, shareId: String): Boolean = try {
        api.get(url(config, "deleteShare", mapOf("id" to shareId))).subsonicResponse?.status == "ok"
    } catch (e: Exception) {
        false
    }
}
