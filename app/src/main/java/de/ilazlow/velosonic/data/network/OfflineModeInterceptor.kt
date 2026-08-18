package de.ilazlow.velosonic.data.network

import de.ilazlow.velosonic.data.datastore.OfflineModeStore
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live, synchronously-readable snapshot of [OfflineModeStore.isEnabled] — [OfflineModeInterceptor]
 * runs on OkHttp's own dispatcher thread, not a coroutine context, so it can't suspend to read the
 * DataStore Flow directly on every request without adding a `runBlocking` per call. Same "hot-path
 * volatile snapshot kept live by a background collector" pattern as
 * [de.ilazlow.velosonic.data.debug.LogManager]'s own logging-enabled flag.
 */
@Singleton
class OfflineModeGate @Inject constructor(
    offlineModeStore: OfflineModeStore,
    @ApplicationScope appScope: CoroutineScope
) {
    @Volatile private var enabled = false

    init {
        offlineModeStore.isEnabled.onEach { enabled = it }.launchIn(appScope)
    }

    fun isOffline(): Boolean = enabled
}

/**
 * Wired into the single shared [okhttp3.OkHttpClient] (see [NetworkModule]) that every Subsonic/
 * lrclib/Radiant Lyrics API call — and every artwork download — goes through, so enabling Offline
 * Mode actually blocks the network instead of just being a UI toggle nothing reads. Throwing
 * [IOException] rather than silently returning an empty response is deliberate: every call site
 * across the app already wraps its network calls in a try/catch that treats *any* [Exception] as
 * "this failed, fall back gracefully" (return null/empty/false) — the exact same path a real
 * connectivity failure already takes — so this needs no per-call-site changes to take effect
 * everywhere at once.
 */
@Singleton
class OfflineModeInterceptor @Inject constructor(
    private val offlineModeGate: OfflineModeGate
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (offlineModeGate.isOffline()) {
            throw IOException("Offline Mode is enabled — request blocked: ${chain.request().url}")
        }
        return chain.proceed(chain.request())
    }
}
