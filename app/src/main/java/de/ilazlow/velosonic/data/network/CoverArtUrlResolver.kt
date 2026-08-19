package de.ilazlow.velosonic.data.network

import de.ilazlow.velosonic.data.artwork.PermanentArtworkStore
import de.ilazlow.velosonic.data.datastore.ServerOrderStore
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves per-server URLs (`getCoverArt`, `stream`) synchronously from an in-memory
 * host->config cache, so grid/list item composables and the playback engine can build them
 * without a per-item suspend DB lookup. Mirrors NavidromeManager.getCoverArtURL/getStreamURL,
 * but keyed per-row by that row's own `serverHost` rather than a single "current server" — same
 * multi-server-aware design as the rest of the network layer. Despite the name (kept for
 * history), this is really "the server-config cache" — [configFor] is the general-purpose escape
 * hatch for anything (like the playback service) that needs the raw config, e.g. to make its own
 * scrobble/star/similar-songs calls.
 */
@Singleton
class CoverArtUrlResolver @Inject constructor(
    serverConfigDao: ServerConfigDao,
    private val serverOrderStore: ServerOrderStore,
    @ApplicationScope appScope: CoroutineScope,
    private val permanentArtworkStore: PermanentArtworkStore
) {
    private val configsByHost = MutableStateFlow<Map<String, ServerConfigEntity>>(emptyMap())
    private val orderedConfigs = MutableStateFlow<List<ServerConfigEntity>>(emptyList())

    init {
        appScope.launch {
            combine(serverConfigDao.observeAll(), serverOrderStore.order) { list, order -> list to order }
                .collect { (list, order) ->
                    configsByHost.value = list.associateBy { it.host }
                    orderedConfigs.value = serverOrderStore.applyOrder(list, order)
                }
        }
    }

    fun configFor(serverHost: String): ServerConfigEntity? = configsByHost.value[serverHost]

    /** In the user's chosen order (see [de.ilazlow.velosonic.data.datastore.ServerOrderStore]) —
     *  feeds every visible multi-server picker list (e.g. the create-playlist server picker). */
    fun allConfigs(): List<ServerConfigEntity> = orderedConfigs.value

    /** Prefers a permanently-persisted local copy (see [PermanentArtworkStore] — populated once
     *  a track/album/playlist finishes downloading) over the network URL, so downloaded
     *  content's artwork renders instantly and offline everywhere it's shown, not just in
     *  Downloads-specific UI. Falls back to [remoteUrlFor] for anything not downloaded. */
    fun urlFor(serverHost: String, coverArtId: String?, size: Int = 300): String? {
        val id = coverArtId ?: return null
        permanentArtworkStore.localUriFor(serverHost, id)?.let { return it }
        return remoteUrlFor(serverHost, id, size)
    }

    fun remoteUrlFor(serverHost: String, coverArtId: String, size: Int = 300): String? {
        val config = configFor(serverHost) ?: return null
        return SubsonicUrlBuilder.build(
            host = config.host,
            endpoint = "getCoverArt",
            username = config.username,
            token = config.token,
            salt = config.salt,
            useJson = false,
            extraParams = mapOf("id" to coverArtId, "size" to size.toString())
        )
    }

    fun streamUrlFor(serverHost: String, trackSubsonicId: String): String? {
        val config = configFor(serverHost) ?: return null
        return SubsonicUrlBuilder.build(
            host = config.host,
            endpoint = "stream",
            username = config.username,
            token = config.token,
            salt = config.salt,
            useJson = false,
            extraParams = mapOf("id" to trackSubsonicId)
        )
    }

    /** Same as [streamUrlFor] but with the Storage → Download Quality setting's `format`/
     *  `maxBitRate` params applied — [format]/[maxBitRate] are the already-resolved values (null
     *  means "omit the param", matching Subsonic's own "no transcoding requested" contract), not
     *  the raw [de.ilazlow.velosonic.data.datastore.StreamFormat]/[de.ilazlow.velosonic.data.datastore.StreamBitrate]
     *  enums — see [de.ilazlow.velosonic.data.datastore.resolvedQueryValue]. */
    fun downloadStreamUrlFor(serverHost: String, trackSubsonicId: String, format: String?, maxBitRate: Int?): String? {
        val config = configFor(serverHost) ?: return null
        val params = buildMap {
            put("id", trackSubsonicId)
            if (format != null) put("format", format)
            if (maxBitRate != null) put("maxBitRate", maxBitRate.toString())
        }
        return SubsonicUrlBuilder.build(
            host = config.host,
            endpoint = "stream",
            username = config.username,
            token = config.token,
            salt = config.salt,
            useJson = false,
            extraParams = params
        )
    }
}
