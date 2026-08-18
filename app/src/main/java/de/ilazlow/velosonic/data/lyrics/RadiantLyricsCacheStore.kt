package de.ilazlow.velosonic.data.lyrics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CachedWordDto(
    val text: String,
    val startMs: Int,
    val durationMs: Int,
    val isBackground: Boolean,
    val romanizedText: String? = null
)

@Serializable
data class CachedLineDto(
    val startMs: Int,
    val text: String,
    val romanizedText: String? = null,
    val words: List<CachedWordDto>? = null
)

@Serializable
data class CachedEntryDto(
    val trackId: String,
    val serverHost: String,
    val title: String,
    val artist: String,
    val romanized: Boolean,
    val aiSynthesized: Boolean,
    val cachedAt: Long,
    val lines: List<CachedLineDto>
)

data class RadiantLyricsCacheSummary(
    val id: String,
    val trackId: String,
    val serverHost: String,
    val title: String,
    val artist: String,
    val romanized: Boolean,
    val aiSynthesized: Boolean,
    val cachedAt: Long,
    val lineCount: Int,
    val hasWordTiming: Boolean
)

/**
 * On-disk cache for word-synced Radiant Lyrics results — mirrors iOS's `RadiantLyricsManager`
 * disk cache (one JSON file per cache key, under app-internal storage) and its "Cached Lyrics"
 * Settings browser. Only ever written for results that actually carry word timing (see
 * [de.ilazlow.velosonic.data.lyrics.LyricsRepository]'s doc comment on why line-only Radiant
 * results aren't cached — there'd be nothing this cache offers over a fresh Navidrome/lrclib
 * fetch next time).
 */
@Singleton
class RadiantLyricsCacheStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir: File by lazy { File(context.filesDir, "radiant_lyrics_cache").apply { mkdirs() } }

    // cacheKey is derived from track.id, which embeds the server host including its "https://"
    // scheme (see compositeId) — raw slashes/colons in there would resolve as path separators
    // against a nonexistent subdirectory, so every non-filename-safe character is replaced.
    private fun sanitize(cacheKey: String): String = cacheKey.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun fileFor(cacheKey: String) = File(dir, "${sanitize(cacheKey)}.json")

    fun read(cacheKey: String): List<LyricLine>? {
        val file = fileFor(cacheKey)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<CachedEntryDto>(file.readText()).lines.map { it.toLyricLine() } }.getOrNull()
    }

    fun write(
        cacheKey: String,
        trackId: String,
        serverHost: String,
        title: String,
        artist: String,
        romanized: Boolean,
        aiSynthesized: Boolean,
        lines: List<LyricLine>
    ) {
        val entry = CachedEntryDto(
            trackId = trackId, serverHost = serverHost, title = title, artist = artist,
            romanized = romanized, aiSynthesized = aiSynthesized, cachedAt = System.currentTimeMillis(),
            lines = lines.map { it.toDto() }
        )
        runCatching { fileFor(cacheKey).writeText(json.encodeToString(entry)) }
    }

    fun listEntries(): List<RadiantLyricsCacheSummary> =
        dir.listFiles { f -> f.extension == "json" }?.mapNotNull { file ->
            runCatching {
                val entry = json.decodeFromString<CachedEntryDto>(file.readText())
                RadiantLyricsCacheSummary(
                    id = file.nameWithoutExtension,
                    trackId = entry.trackId,
                    serverHost = entry.serverHost,
                    title = entry.title,
                    artist = entry.artist,
                    romanized = entry.romanized,
                    aiSynthesized = entry.aiSynthesized,
                    cachedAt = entry.cachedAt,
                    lineCount = entry.lines.size,
                    hasWordTiming = entry.lines.any { !it.words.isNullOrEmpty() }
                )
            }.getOrNull()
        }?.sortedByDescending { it.cachedAt } ?: emptyList()

    fun cachedLines(id: String): List<LyricLine>? = read(id)

    fun rawJson(id: String): String? = runCatching { fileFor(id).readText() }.getOrNull()

    fun deleteEntry(id: String) {
        fileFor(id).delete()
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Rewrites every cached entry belonging to [oldHost] onto [newHost]'s trackId/serverHost and
     *  re-keys its filename accordingly — not part of iOS's migration (this cache is Android-
     *  only, see [LyricsRepository][de.ilazlow.velosonic.data.lyrics.LyricsRepository]'s doc
     *  comment), but skipping it would silently orphan every cached lyric on a server rename. */
    fun migrateHost(oldHost: String, newHost: String) {
        val prefix = "${oldHost}_"
        dir.listFiles { f -> f.extension == "json" }?.forEach { file ->
            runCatching {
                val entry = json.decodeFromString<CachedEntryDto>(file.readText())
                if (entry.serverHost != oldHost || !entry.trackId.startsWith(prefix)) return@runCatching
                val subsonicId = entry.trackId.removePrefix(prefix)
                val newTrackId = "${newHost}_$subsonicId"
                val variant = file.nameWithoutExtension.removePrefix(sanitize(entry.trackId))
                val migrated = entry.copy(trackId = newTrackId, serverHost = newHost)
                fileFor("$newTrackId$variant").writeText(json.encodeToString(migrated))
                file.delete()
            }
        }
    }
}

private fun LyricLine.toDto() = CachedLineDto(
    startMs = startMs,
    text = text,
    romanizedText = romanizedText,
    words = words?.map { CachedWordDto(it.text, it.startMs, it.durationMs, it.isBackground, it.romanizedText) }
)

private fun CachedLineDto.toLyricLine() = LyricLine(
    startMs = startMs,
    text = text,
    words = words?.map { LyricWord(it.text, it.startMs, it.durationMs, it.isBackground, it.romanizedText) },
    romanizedText = romanizedText
)
