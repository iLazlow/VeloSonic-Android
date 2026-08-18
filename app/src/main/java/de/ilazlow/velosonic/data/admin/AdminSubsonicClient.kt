package de.ilazlow.velosonic.data.admin

import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.network.dto.NowPlayingEntryDto
import de.ilazlow.velosonic.data.network.dto.ScanStatusDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Admin-only Subsonic calls backing [de.ilazlow.velosonic.ui.settings.ServerDetailScreen]'s
 * Actions/Now Playing sections — mirrors NavidromeManager.swift's `fetchIsAdmin`/`startScan`/
 * `getScanStatus`/`getNowPlaying`. Every call here is best-effort: a non-admin account, an
 * unsupported server, or a network blip all just come back null/empty rather than throwing, since
 * none of this is essential to the app's core function.
 */
@Singleton
class AdminSubsonicClient @Inject constructor(
    private val api: SubsonicApi
) {
    private fun url(config: ServerConfigEntity, endpoint: String, extra: Map<String, String> = emptyMap()) =
        SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra)

    /** Null means "couldn't check" (offline, unsupported server, ...) — callers should treat that
     *  as "leave the last-known value alone", not "definitely not admin". */
    suspend fun fetchIsAdmin(config: ServerConfigEntity): Boolean? = try {
        api.get(url(config, "getUser", mapOf("username" to config.username))).subsonicResponse?.user?.adminRole
    } catch (e: Exception) {
        null
    }

    /** Triggers a server-side library rescan (files on disk) — distinct from this app's own local
     *  sync. `fullScan = false` is incremental (new/changed files only). */
    suspend fun startScan(config: ServerConfigEntity, fullScan: Boolean): ScanStatusDto? = try {
        api.get(url(config, "startScan", mapOf("fullScan" to fullScan.toString()))).subsonicResponse?.scanStatus
    } catch (e: Exception) {
        null
    }

    suspend fun getScanStatus(config: ServerConfigEntity): ScanStatusDto? = try {
        api.get(url(config, "getScanStatus")).subsonicResponse?.scanStatus
    } catch (e: Exception) {
        null
    }

    /** Every user's currently-playing track, server-wide. */
    suspend fun getNowPlaying(config: ServerConfigEntity): List<NowPlayingEntryDto> = try {
        api.get(url(config, "getNowPlaying")).subsonicResponse?.nowPlaying?.entry.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }
}
