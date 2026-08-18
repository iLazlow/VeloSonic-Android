package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSearchesDataStore by preferencesDataStore(name = "recent_searches")
private const val MAX_ENTRIES = 10

/** Mirrors SearchViewModel.swift's `UserDefaults`-backed recent-search history: plain local
 *  list of raw query strings, most-recent-first, capped at 10, case-insensitive de-duped (an
 *  already-present term moves to the front instead of duplicating). Not scoped per-server or
 *  synced anywhere — purely a local convenience, same as iOS. */
@Singleton
class RecentSearchesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TERMS = stringPreferencesKey("terms_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val terms: Flow<List<String>> = context.recentSearchesDataStore.data.map { prefs ->
        prefs[Keys.TERMS]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
    }

    suspend fun add(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        context.recentSearchesDataStore.edit { prefs ->
            val current = prefs[Keys.TERMS]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_ENTRIES)
            prefs[Keys.TERMS] = json.encodeToString(updated)
        }
    }

    suspend fun remove(term: String) {
        context.recentSearchesDataStore.edit { prefs ->
            val current = prefs[Keys.TERMS]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
            prefs[Keys.TERMS] = json.encodeToString(current.filterNot { it == term })
        }
    }

    suspend fun clear() {
        context.recentSearchesDataStore.edit { prefs -> prefs[Keys.TERMS] = json.encodeToString(emptyList<String>()) }
    }
}
