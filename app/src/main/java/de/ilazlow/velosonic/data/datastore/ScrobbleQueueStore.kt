package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.scrobbleQueueDataStore by preferencesDataStore(name = "scrobble_queue")

/** Mirrors iOS's `ScrobbleQueue.PendingScrobble` — a scrobble that couldn't be sent live (offline
 *  mode, or a transient failure) kept durably so it isn't just lost. */
@Serializable
data class PendingScrobble(
    val trackId: String,
    val serverHost: String,
    val timestampMs: Long,
    val submission: Boolean
)

@Singleton
class ScrobbleQueueStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENTRIES = stringPreferencesKey("entries_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val entries: Flow<List<PendingScrobble>> = context.scrobbleQueueDataStore.data.map { prefs -> decode(prefs[Keys.ENTRIES]) }

    suspend fun snapshot(): List<PendingScrobble> = entries.first()

    suspend fun enqueue(entry: PendingScrobble) {
        context.scrobbleQueueDataStore.edit { prefs ->
            prefs[Keys.ENTRIES] = json.encodeToString(decode(prefs[Keys.ENTRIES]) + entry)
        }
    }

    suspend fun replaceAll(entries: List<PendingScrobble>) {
        context.scrobbleQueueDataStore.edit { prefs -> prefs[Keys.ENTRIES] = json.encodeToString(entries) }
    }

    private fun decode(raw: String?): List<PendingScrobble> =
        raw?.let { runCatching { json.decodeFromString<List<PendingScrobble>>(it) }.getOrNull() }.orEmpty()
}
