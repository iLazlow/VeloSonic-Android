package de.ilazlow.velosonic.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.datastore.ServerOrderStore
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.NavidromeAuth
import de.ilazlow.velosonic.data.network.NavidromeLoginRequest
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.security.CredentialStore
import de.ilazlow.velosonic.data.sync.ServerMigrationManager
import de.ilazlow.velosonic.data.sync.SyncEngine
import de.ilazlow.velosonic.data.sync.SyncMode
import de.ilazlow.velosonic.data.sync.SyncNowWorker
import de.ilazlow.velosonic.domain.ServerType
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

enum class AddServerError {
    /** Host string couldn't be parsed as a URL (missing scheme, malformed, etc.). */
    INVALID_HOST,
    /** Reached the host but the Subsonic `ping` call didn't come back with status "ok". */
    PING_FAILED
}

sealed interface AddServerResult {
    data class Success(val config: ServerConfigEntity) : AddServerResult
    data class Failure(val error: AddServerError) : AddServerResult
}

/**
 * Mirrors LoginView.performLogin + NavidromeManager's ping/type-detection/JWT-prefetch flow.
 * Every call is parameterized by explicit host/credentials — no implicit "current server".
 */
@Singleton
class ServerRepository @Inject constructor(
    private val api: SubsonicApi,
    private val serverConfigDao: ServerConfigDao,
    private val credentialStore: CredentialStore,
    private val syncEngine: SyncEngine,
    private val serverMigrationManager: ServerMigrationManager,
    private val serverOrderStore: ServerOrderStore,
    @ApplicationContext private val context: Context
) {
    fun observeServers(): Flow<List<ServerConfigEntity>> = serverConfigDao.observeAll()

    suspend fun setServerVisible(config: ServerConfigEntity, visible: Boolean) {
        serverConfigDao.update(config.copy(isVisible = visible))
    }

    /** Deletes the server's credentials, its whole cached library (SyncEngine's job — see
     *  deleteAllDataForHost), and finally the `server_configs` row itself. Doesn't touch any
     *  audio already downloaded for this host's tracks — cleaning those up is a coarser
     *  "orphaned downloads" sweep that doesn't exist yet (same gap noted when Downloads was
     *  built), not something worth blocking server removal on. */
    suspend fun removeServer(config: ServerConfigEntity) {
        credentialStore.deletePassword(config.host, config.username)
        syncEngine.deleteAllDataForHost(config.host)
        serverConfigDao.delete(config)
    }

    suspend fun addServer(
        hostInput: String,
        username: String,
        password: String,
        name: String,
        onLog: (String) -> Unit = {}
    ): AddServerResult {
        val host = hostInput.trim().trimEnd('/')
        val auth = NavidromeAuth.generateAuth(password)
        onLog("Connecting to $host as $username")
        onLog("Generated auth token (${auth.token.length} chars), salt (${auth.salt.length} chars)")

        val pingData = try {
            val url = SubsonicUrlBuilder.build(host, "ping", username, auth.token, auth.salt)
            onLog("Ping ${redactUrl(url)}")
            val response = api.get(url).subsonicResponse
            onLog(
                "Ping response: status=${response?.status ?: "null"}" +
                    (response?.error?.let { " error=${it.code} \"${it.message}\"" } ?: "")
            )
            response
        } catch (e: IllegalArgumentException) {
            onLog("Invalid host: ${e.message}")
            return AddServerResult.Failure(AddServerError.INVALID_HOST)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()?.take(300)
            onLog("Ping failed: HTTP ${e.code()} ${e.message()}" + (body?.let { " body=$it" } ?: ""))
            null
        } catch (e: Exception) {
            onLog("Ping failed: ${e::class.simpleName}: ${e.message}")
            null
        }

        if (pingData?.status != "ok") {
            return AddServerResult.Failure(AddServerError.PING_FAILED)
        }

        val detectedType = pingData.type?.takeIf { it.isNotEmpty() }
            ?.let { ServerType.fromServerResponse(it) }
            ?: detectOpenSubsonicFallback(host, username, auth.token, auth.salt, onLog)
        val detectedVersion = pingData.serverVersion
        onLog("Detected server type=${detectedType.raw} version=${detectedVersion ?: "unknown"}")

        val navToken = if (detectedType == ServerType.NAVIDROME) {
            onLog("Fetching Navidrome JWT")
            fetchNavidromeJwt(host, username, password).also { token ->
                onLog(if (token != null) "JWT fetched (${token.length} chars)" else "JWT fetch failed")
            }
        } else {
            null
        }

        credentialStore.savePassword(host, username, password)

        val config = ServerConfigEntity(
            host = host,
            username = username,
            token = auth.token,
            salt = auth.salt,
            name = name.trim(),
            serverTypeRaw = detectedType.raw,
            serverVersion = detectedVersion,
            navToken = navToken,
            isVisible = true,
            isAdmin = false
        )
        serverConfigDao.upsert(config)
        serverOrderStore.recordIfNew(host)
        onLog("Server saved")
        // Runs as a foreground-service WorkManager job (SyncNowWorker), not a plain
        // appScope.launch — mirrors the iOS client's "background sync starts automatically once a
        // server is added" behavior, but a bare coroutine has no foreground priority: backgrounding
        // the app mid-sync let the OS freeze/kill the process before this had a WorkManager job
        // (and its progress notification) behind it.
        SyncNowWorker.enqueue(context, host, SyncMode.INITIAL)
        return AddServerResult.Success(config)
    }

    /** Masks the `t=`/`s=` (token/salt) query params before a URL is ever logged — mirrors iOS's
     *  login debug console, which never prints full credentials either. */
    private fun redactUrl(url: String): String =
        url.replace(Regex("([?&][ts]=)[^&]*"), "$1***")

    /** Edit path for an already-saved server. When [hostInput] resolves to the same host as
     *  [originalHost], this is just [addServer] again (its upsert-by-host naturally overwrites
     *  the existing row in place). When the host actually changed, every host-scoped local
     *  record is migrated first via [ServerMigrationManager] — including the old row's synced
     *  library, so the subsequent [addServer] call's `performInitialSync` sees
     *  `isInitialSyncComplete` already true (migrated along with the rest of sync metadata) and
     *  correctly no-ops instead of a redundant full re-fetch — then the old `server_configs` row
     *  is dropped once the new one is confirmed to have been created successfully. */
    suspend fun editServer(
        originalHost: String,
        hostInput: String,
        username: String,
        password: String,
        name: String,
        onMigrationProgress: (Float) -> Unit = {},
        onLog: (String) -> Unit = {}
    ): AddServerResult {
        val newHost = hostInput.trim().trimEnd('/')
        if (newHost != originalHost) {
            onLog("Host changed: $originalHost -> $newHost — migrating local data first")
            serverMigrationManager.migrate(originalHost, newHost, username, onMigrationProgress)
            serverOrderStore.replace(originalHost, newHost)
        }
        val result = addServer(newHost, username, password, name, onLog)
        if (result is AddServerResult.Success && newHost != originalHost) {
            serverConfigDao.getByHost(originalHost)?.let { serverConfigDao.delete(it) }
        }
        return result
    }

    private suspend fun detectOpenSubsonicFallback(
        host: String,
        username: String,
        token: String,
        salt: String,
        onLog: (String) -> Unit = {}
    ): ServerType {
        val extOk = try {
            val url = SubsonicUrlBuilder.build(host, "getOpenSubsonicExtensions", username, token, salt)
            api.get(url).subsonicResponse?.status == "ok"
        } catch (e: Exception) {
            onLog("OpenSubsonic extension check failed: ${e.message}")
            false
        }
        return if (extOk) ServerType.OPEN_SUBSONIC else ServerType.SUBSONIC
    }

    private suspend fun fetchNavidromeJwt(host: String, username: String, password: String): String? {
        return try {
            val response = api.login("$host/auth/login", NavidromeLoginRequest(username, password))
            response.token
        } catch (e: Exception) {
            null
        }
    }
}
