package de.ilazlow.velosonic.data.share

import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.network.NavidromeJwtClient
import de.ilazlow.velosonic.data.network.dto.SubsonicShareDto
import de.ilazlow.velosonic.domain.isNavidrome
import javax.inject.Inject
import javax.inject.Singleton

/** [SubsonicShareDto] doesn't carry which server it came from (the Subsonic response has no such
 *  field) — mirrors iOS's client-side, non-decoded `SubsonicShare.serverHost` tag, needed once
 *  shares from multiple servers' [ShareRepository.listShares] get combined into one list. */
data class ShareItem(val share: SubsonicShareDto, val serverHost: String)

@Singleton
class ShareRepository @Inject constructor(
    private val shareClient: ShareSubsonicClient,
    private val jwtClient: NavidromeJwtClient,
    private val serverConfigDao: ServerConfigDao
) {
    /** Combined across every configured server (not just visible ones — a share isn't a
     *  combined-library-view concern), sorted newest-first by [SubsonicShareDto.created]'s
     *  ISO8601 string (lexicographic order matches chronological order for that format). */
    suspend fun listShares(): List<ShareItem> =
        serverConfigDao.getAll()
            .flatMap { config -> shareClient.getShares(config).map { ShareItem(it, config.host) } }
            .sortedByDescending { it.share.created.orEmpty() }

    suspend fun createShare(
        serverHost: String,
        entityId: String,
        description: String?,
        expiresAtEpochMs: Long?,
        downloadAllowed: Boolean
    ): SubsonicShareDto? {
        val config = shareClient.configFor(serverHost) ?: return null
        val share = shareClient.createShare(config, entityId, description, expiresAtEpochMs, downloadAllowed)
            ?: return null
        if (config.isNavidrome) {
            jwtClient.updateShareDownloadable(config, share.id, downloadAllowed)
        }
        return share
    }

    suspend fun deleteShare(serverHost: String, shareId: String): Boolean {
        val config = shareClient.configFor(serverHost) ?: return false
        return shareClient.deleteShare(config, shareId)
    }
}
