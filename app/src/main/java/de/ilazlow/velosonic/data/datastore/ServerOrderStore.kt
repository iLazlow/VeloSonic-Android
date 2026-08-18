package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverOrderDataStore by preferencesDataStore(name = "server_order")

/**
 * Records the order servers were first successfully added in — independent of Room entirely, so
 * it never needs a schema migration (this app's `fallbackToDestructiveMigration(true)` would wipe
 * every synced library just to add an "added at" column, which isn't worth it for something this
 * small). [de.ilazlow.velosonic.data.db.ServerConfigDao.getAll]'s `ORDER BY name` is right for
 * settings lists but wrong for anything meant to reflect "your servers, in the order you set
 * them up" (Home's multi-server interleave — confirmed live: alphabetical put a later-added
 * test server ahead of the user's actual primary one).
 */
@Singleton
class ServerOrderStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val HOSTS = stringPreferencesKey("hosts_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val order: Flow<List<String>> = context.serverOrderDataStore.data.map { prefs ->
        prefs[Keys.HOSTS]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
    }

    /** No-ops if [host] is already recorded — an edit/re-add of an existing server must not
     *  reshuffle its position. */
    suspend fun recordIfNew(host: String) {
        val current = order.first()
        if (host in current) return
        context.serverOrderDataStore.edit { prefs ->
            prefs[Keys.HOSTS] = json.encodeToString(current + host)
        }
    }

    /** Sorts [hosts] by recorded add order, with any host never recorded (e.g. added before this
     *  store existed) kept in its original relative position, appended after every recorded one. */
    suspend fun sorted(hosts: List<String>): List<String> {
        val recordedOrder = order.first()
        val (known, unknown) = hosts.partition { it in recordedOrder }
        return known.sortedBy { recordedOrder.indexOf(it) } + unknown
    }

    /** A server-address edit (see [de.ilazlow.velosonic.data.ServerRepository.editServer]) should
     *  keep its original position rather than moving to the end like a freshly-added server would. */
    suspend fun replace(oldHost: String, newHost: String) {
        val current = order.first()
        val updated = if (oldHost in current) current.map { if (it == oldHost) newHost else it } else current + newHost
        context.serverOrderDataStore.edit { prefs -> prefs[Keys.HOSTS] = json.encodeToString(updated) }
    }

    /** Overwrites the recorded order wholesale — the write side of manual drag-to-reorder in
     *  Settings. Anything not present in [hosts] (there shouldn't be anything, callers always pass
     *  every known host) simply drops out of the recorded order and would fall back to
     *  "unrecorded" (end-of-list) treatment in [sorted]/[applyOrder] if it reappeared later. */
    suspend fun setOrder(hosts: List<String>) {
        context.serverOrderDataStore.edit { prefs -> prefs[Keys.HOSTS] = json.encodeToString(hosts) }
    }

    /** Sync counterpart of [sorted] for callers that already hold a live [order] emission (e.g.
     *  inside a `combine` with a servers [Flow]) and can't suspend — same known-then-unknown
     *  ordering rule, just taking the recorded order as a plain parameter instead of reading it. */
    fun applyOrder(configs: List<ServerConfigEntity>, recordedOrder: List<String>): List<ServerConfigEntity> {
        val (known, unknown) = configs.partition { it.host in recordedOrder }
        return known.sortedBy { recordedOrder.indexOf(it.host) } + unknown
    }
}
