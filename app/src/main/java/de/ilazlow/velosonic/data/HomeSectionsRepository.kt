package de.ilazlow.velosonic.data

import de.ilazlow.velosonic.data.datastore.ServerOrderStore
import de.ilazlow.velosonic.data.db.AlbumDao
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.sync.compositeId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors HomeViewModel.swift's `loadAllData`: Home's four sections (newest/recent/frequent/
 * random) are ordered by asking each server directly (`getAlbumList2?type=…`), not by re-
 * deriving an order client-side from locally synced `created`/`played`/`playCount` fields —
 * different Subsonic implementations don't agree on date format/precision for those fields (a
 * live cross-server case broke local sort ordering — see `SyncMappers.normalizeCreatedDate`'s
 * doc comment), and trusting the server's own answer sidesteps that entirely. Multiple servers'
 * independently-ordered lists are merged with iOS's exact round-robin `interleave`, then hydrated
 * against the already-synced local [AlbumEntity] rows (so cover art/etc. still come from Room,
 * only the *order* is server-authoritative).
 */
@Singleton
class HomeSectionsRepository @Inject constructor(
    private val api: SubsonicApi,
    private val albumDao: AlbumDao,
    private val serverConfigDao: ServerConfigDao,
    private val serverOrderStore: ServerOrderStore
) {
    private fun url(config: ServerConfigEntity, endpoint: String, extra: Map<String, String>) =
        SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra)

    suspend fun fetchHomeSections(visibleHosts: List<String>, size: Int = 20): HomeSections {
        // Insertion order (rowid), not ServerConfigDao.getAll()'s alphabetical — interleaving by
        // name put a later-added test server ahead of the user's actual primary one. ServerOrderStore
        // then overrides with its own recorded order for any host it already knows about, so this
        // rowid-based order only really matters as the one-time bootstrap for servers added before
        // that store existed.
        val byHost = serverConfigDao.getAllOrderedByInsertion().associateBy { it.host }
        val orderedHosts = serverOrderStore.sorted(byHost.keys.toList().filter { it in visibleHosts })
        val configs = orderedHosts.mapNotNull { byHost[it] }
        if (configs.isEmpty()) return HomeSections()

        val newestPerServer = mutableListOf<List<String>>()
        val recentPerServer = mutableListOf<List<String>>()
        val frequentPerServer = mutableListOf<List<String>>()
        val randomPerServer = mutableListOf<List<String>>()

        for (config in configs) {
            newestPerServer += fetchIds(config, "newest", size)
            recentPerServer += fetchIds(config, "recent", size)
            frequentPerServer += fetchIds(config, "frequent", size)
            randomPerServer += fetchIds(config, "random", size)
        }

        return HomeSections(
            recentlyPlayed = hydrate(interleave(recentPerServer).take(size)),
            recentlyAdded = hydrate(interleave(newestPerServer).take(size)),
            frequentlyPlayed = hydrate(interleave(frequentPerServer).take(size)),
            random = hydrate(interleave(randomPerServer).take(size))
        )
    }

    private suspend fun fetchIds(config: ServerConfigEntity, type: String, size: Int): List<String> = try {
        api.get(url(config, "getAlbumList2", mapOf("type" to type, "size" to size.toString())))
            .subsonicResponse?.albumList2?.album.orEmpty()
            .map { compositeId(config.host, it.id) }
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun hydrate(ids: List<String>): List<AlbumEntity> {
        if (ids.isEmpty()) return emptyList()
        val byId = albumDao.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** Verbatim port of HomeViewModel.swift's `interleave` — round-robin across each server's
     *  own (already server-ordered) list, not a re-sort. */
    private fun interleave(lists: List<List<String>>): List<String> {
        val result = mutableListOf<String>()
        val maxSize = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxSize) {
            for (list in lists) {
                if (i < list.size) result += list[i]
            }
        }
        return result
    }
}
