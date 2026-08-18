package de.ilazlow.velosonic.data.artwork

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Trackid-keyed persistent cache of resolved (or confirmed-absent) Apple-animated-artwork URLs
 *  — direct port of `Utils/AppleArtworkCache.swift`. Backed by a flat JSON file rather than
 *  SwiftData since it's a pure key-value cache, no querying needed. `"none"` is the on-disk
 *  sentinel for "API confirmed no result", distinct from an absent key ("never fetched yet") —
 *  callers must check [get] for null (fetch) vs `"none"` (skip, already confirmed absent) vs a
 *  real URL (use it). */
@Singleton
class AppleArtworkCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val file = File(context.filesDir, "apple_artwork_urls.json")
    private val mutex = Mutex()
    private var cached: MutableMap<String, String>? = null

    private suspend fun mapUnsafe(): MutableMap<String, String> {
        cached?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext mutableMapOf()
            try {
                json.decodeFromString<Map<String, String>>(file.readText()).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
        }
        cached = loaded
        return loaded
    }

    suspend fun get(trackId: String): String? = mutex.withLock { mapUnsafe()[trackId] }

    suspend fun getTall(trackId: String): String? = mutex.withLock { mapUnsafe()["${trackId}_tall"] }

    suspend fun set(trackId: String, url: String?, urlTall: String?) = mutex.withLock {
        val map = mapUnsafe()
        map[trackId] = url ?: "none"
        map["${trackId}_tall"] = urlTall ?: "none"
        withContext(Dispatchers.IO) {
            file.writeText(json.encodeToString(map))
        }
    }
}
