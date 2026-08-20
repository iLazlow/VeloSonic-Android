package de.ilazlow.velosonic.data.lyrics

import de.ilazlow.velosonic.BuildConfig
import de.ilazlow.velosonic.data.datastore.LyricsSettingsStore
import de.ilazlow.velosonic.data.datastore.LyricsSource
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.LrcLibApi
import de.ilazlow.velosonic.data.network.RadiantLyricsApi
import de.ilazlow.velosonic.data.network.dto.RadiantSyllableDto
import de.ilazlow.velosonic.data.network.dto.CueDto
import de.ilazlow.velosonic.data.network.dto.StructuredLyricsDto
import de.ilazlow.velosonic.data.network.dto.StructuredLyricsLineDto
import de.ilazlow.velosonic.data.network.SubsonicApi
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.domain.supportsOpenSubsonicExtensions
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors LyricsManager.swift's source waterfall: Radiant Lyrics (word-level karaoke, gated by
 * its own enable toggle — see [LyricsSettingsStore]) → Navidrome's `getLyricsBySongId`
 * (OpenSubsonic extension) → lrclib.net → whatever the background sync job already cached
 * locally on [TrackEntity]. Embedded ID3/Vorbis tag extraction is skipped (it only ever fires
 * for locally-cached files, and Media3 doesn't decode USLT frames to text out of the box — a
 * low-value fallback for the added complexity).
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val api: SubsonicApi,
    private val lrcLibApi: LrcLibApi,
    private val radiantLyricsApi: RadiantLyricsApi,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val trackDao: TrackDao,
    private val lyricsSettingsStore: LyricsSettingsStore,
    private val radiantCacheStore: RadiantLyricsCacheStore
) {
    private fun url(config: ServerConfigEntity, endpoint: String, extra: Map<String, String> = emptyMap()) =
        SubsonicUrlBuilder.build(config.host, endpoint, config.username, config.token, config.salt, extraParams = extra)

    /** On-demand resolve for the Player — tries the network sources fresh every time (no
     *  connectivity check; a failed/offline call just falls through), then the local cache. */
    suspend fun resolve(track: TrackEntity): LyricsContent? {
        fetchFromPreferredSource(track)?.let { return it }
        return localCached(track)
    }

    /** Sync-job variant: fetches (network sources only, no need to re-derive what's already
     *  cached) and persists the result — or the lack of one — onto [TrackEntity], stamping
     *  [TrackEntity.lyricsCheckedAt] either way so a track with no lyrics anywhere isn't
     *  re-queried on every sync pass. A Radiant word-synced result is deliberately NOT persisted
     *  here (its LRC-string columns can't carry per-word timing) — it stays purely on-demand,
     *  refetched each time the Player opens that track. */
    suspend fun fetchAndPersist(track: TrackEntity) {
        val config = coverArtUrlResolver.configFor(track.serverHost)
        val navidromeConfig = config?.takeIf { it.supportsOpenSubsonicExtensions }
        val source = lyricsSettingsStore.settings.first().source
        val content = when (source) {
            LyricsSource.AUTO -> navidromeConfig?.let { fetchNavidrome(it, track) } ?: fetchLrclib(track)
            LyricsSource.NAVIDROME -> navidromeConfig?.let { fetchNavidrome(it, track) }
            LyricsSource.LRCLIB -> fetchLrclib(track)
        }
        val updated = when (content) {
            is LyricsContent.Synced -> track.copy(
                syncedLyricsLRC = LrcParser.encode(content.lines),
                plainLyrics = null,
                lyricsCheckedAt = System.currentTimeMillis()
            )
            is LyricsContent.Plain -> track.copy(
                plainLyrics = content.text,
                syncedLyricsLRC = null,
                lyricsCheckedAt = System.currentTimeMillis()
            )
            null -> track.copy(lyricsCheckedAt = System.currentTimeMillis())
        }
        trackDao.upsertAll(listOf(updated))
    }

    /** Radiant first (word-level karaoke, on-demand only — see [fetchAndPersist]'s doc comment),
     *  then whichever of Navidrome/lrclib [LyricsSettingsStore]'s source preference picks —
     *  AUTO tries Navidrome (when the server supports the OpenSubsonic extension) then falls
     *  through to lrclib, matching StreamingSettings.lyricsSource's `.auto` case exactly;
     *  NAVIDROME/LRCLIB pin to one source only, with no fallback to the other. */
    private suspend fun fetchFromPreferredSource(track: TrackEntity): LyricsContent? {
        val settings = lyricsSettingsStore.settings.first()
        if (settings.radiantLyricsEnabled) {
            fetchRadiant(track, settings.radiantLyricsRomanization, settings.radiantLyricsAiSynthesizeEnabled)?.let { return it }
        }
        val config = coverArtUrlResolver.configFor(track.serverHost)
        val navidromeConfig = config?.takeIf { it.supportsOpenSubsonicExtensions }
        return when (settings.source) {
            LyricsSource.AUTO -> navidromeConfig?.let { fetchNavidrome(it, track) } ?: fetchLrclib(track)
            LyricsSource.NAVIDROME -> navidromeConfig?.let { fetchNavidrome(it, track) }
            LyricsSource.LRCLIB -> fetchLrclib(track)
        }
    }

    private fun localCached(track: TrackEntity): LyricsContent? {
        track.syncedLyricsLRC?.let { return LyricsContent.Synced(LrcParser.parse(it), LyricsSourceKind.LOCAL) }
        track.plainLyrics?.let { return LyricsContent.Plain(it, LyricsSourceKind.LOCAL) }
        return null
    }

    /** Word-synced results are cached to disk (see [RadiantLyricsCacheStore]) so offline playback
     *  — and the "Cached Lyrics" Settings browser — still get the richer view for a track already
     *  looked up once; line-only Radiant results (no `words`) aren't cached, since there'd be
     *  nothing this offers over a fresh Navidrome/lrclib fetch next time. Mirrors iOS's
     *  `RadiantLyricsManager.fetchLyrics`/`performFetch` cache-key scheme exactly. */
    private suspend fun fetchRadiant(track: TrackEntity, romanize: Boolean, synthesize: Boolean): LyricsContent? {
        val cacheKey = "${track.id}_${if (romanize) "romanized" else "plain"}${if (synthesize) "_ai" else ""}"
        radiantCacheStore.read(cacheKey)?.let { return LyricsContent.Synced(it, LyricsSourceKind.RADIANT, isAiSynthesized = synthesize) }

        return try {
            val response = radiantLyricsApi.get(
                tokenId = BuildConfig.RADIANT_LYRICS_API_ID,
                token = BuildConfig.RADIANT_LYRICS_API_KEY,
                title = track.title,
                artist = firstArtist(track.artistName),
                isrc = track.isrc?.takeIf(String::isNotEmpty),
                romanize = romanize,
                synthesize = if (synthesize) true else null
            )
            if (response.data.isEmpty()) {
                null
            } else {
                val lines = response.data.map { line ->
                    // Background/harmony vocals overlap the lead line in time rather than follow
                    // it sequentially — mixed in, that breaks the "words are chronologically
                    // ordered" assumption the sweep/tap-to-seek both depend on, so only the lead
                    // vocal is kept (matches iOS's RadiantLyricsManager.convert).
                    val words = line.syllabus
                        ?.filter { !it.isBackground }
                        ?.let { mergeSyllablesIntoWords(it) }
                        ?.sortedBy { it.startMs }
                        ?.takeIf { it.isNotEmpty() }
                    LyricLine(
                        startMs = (line.startTime * 1000).toInt(),
                        text = line.text,
                        words = words,
                        romanizedText = line.romanized
                    )
                }
                // Reflects whether the API actually used AI, not whether synthesize=true was
                // sent — Radiant can (and does) ignore that request when it already has real
                // matched data.
                val aiSynthesized = response.synthesized ?: false
                if (lines.any { !it.words.isNullOrEmpty() }) {
                    radiantCacheStore.write(cacheKey, track.id, track.serverHost, track.title, track.artistName, romanize, aiSynthesized, lines)
                }
                LyricsContent.Synced(lines, LyricsSourceKind.RADIANT, isAiSynthesized = aiSynthesized)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Radiant's "syllabus" granularity isn't consistent across songs: some give one entry per
     *  whole word, others give true sub-word syllables (e.g. "Immer" arrives as "Im" + "mer",
     *  "Schulter" as "Schul" + "ter"). Treating every entry as its own word — one FlowRow element,
     *  one gap after it — looks fine for the first case but inserts a bogus space *inside* a word
     *  for the second. The signal for "this fragment continues directly into the next one" is the
     *  absence of a trailing space in the entry's own (untrimmed) text — real word/phrase entries
     *  reliably carry one, syllable fragments of the same word don't — so fragments lacking a
     *  trailing space are glued to whatever follows until one is found (or the line runs out),
     *  reconstructing whole words before they ever reach the sweep renderer. Timing is summed
     *  across the merged span. Mirrors iOS's `mergeSyllablesIntoWords`. */
    private fun mergeSyllablesIntoWords(syllabus: List<RadiantSyllableDto>): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        var i = 0
        while (i < syllabus.size) {
            val first = syllabus[i]
            val text = StringBuilder(first.text)
            val romanized = StringBuilder(first.romanized.orEmpty())
            var duration = first.duration
            var j = i
            while (!syllabus[j].text.endsWith(" ") && j + 1 < syllabus.size) {
                j += 1
                text.append(syllabus[j].text)
                syllabus[j].romanized?.let { romanized.append(it) }
                duration += syllabus[j].duration
            }
            val trimmedRomanized = romanized.toString().trim()
            words.add(
                LyricWord(
                    text = text.toString().trim(),
                    startMs = first.time,
                    durationMs = duration.coerceAtLeast(1),
                    isBackground = first.isBackground,
                    romanizedText = trimmedRomanized.ifEmpty { null }
                )
            )
            i = j + 1
        }
        return words
    }

    /** Radiant Lyrics wants a single artist, not "A, B" / "A feat. B" style credits — take
     *  whichever recognized separator appears earliest and keep only the part before it
     *  (mirrors iOS's `firstArtist`; includes " • ", the app's own multi-artist join separator
     *  used elsewhere in the UI, in case [TrackEntity.artistName] ever carries one). */
    private fun firstArtist(artist: String): String {
        val delimiters = listOf(" • ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " & ", ", ", " x ", " X ", ";", " / ", "/")
        var earliest = -1
        for (d in delimiters) {
            val idx = artist.indexOf(d, ignoreCase = true)
            if (idx >= 0 && (earliest == -1 || idx < earliest)) earliest = idx
        }
        return if (earliest == -1) artist.trim() else artist.substring(0, earliest).trim()
    }

    /** OpenSubsonic `songLyrics` extension (v1 line-synced + v2 word-level `cueLine`/`cue` timing,
     *  see https://opensubsonic.netlify.app/docs/extensions/songlyrics/) — `enhanced=true` opts
     *  into `cueLine` when the server supports v2; a v1-only server just ignores the unknown
     *  param and returns its normal v1 shape (no `cueLine`, [wordsForLine] then returns null and
     *  the player falls back to its synthesized per-character sweep, same as before this existed).
     *  Prefers the `kind == "main"` entry when the server sent multiple (translation/pronunciation
     *  layers), falling back to the first non-empty entry for a v1 response that has no `kind`. */
    private suspend fun fetchNavidrome(config: ServerConfigEntity, track: TrackEntity): LyricsContent? = try {
        val entries = api.get(url(config, "getLyricsBySongId", mapOf("id" to track.subsonicId, "enhanced" to "true")))
            .subsonicResponse?.lyricsList?.structuredLyrics.orEmpty()
        val structured = entries.firstOrNull { it.kind == "main" && !it.line.isNullOrEmpty() }
            ?: entries.firstOrNull { !it.line.isNullOrEmpty() }
        structured?.let {
            val lines = it.line.orEmpty()
            if (it.synced) {
                LyricsContent.Synced(
                    lines.mapIndexed { index, line -> toLyricLine(index, line, it) },
                    LyricsSourceKind.NAVIDROME
                )
            } else {
                LyricsContent.Plain(lines.joinToString("\n") { line -> line.value }, LyricsSourceKind.NAVIDROME)
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun toLyricLine(index: Int, line: StructuredLyricsLineDto, structured: StructuredLyricsDto): LyricLine =
        LyricLine(startMs = line.start ?: 0, text = line.value, words = wordsForLine(index, structured))

    /** `cueLine.index` references the [StructuredLyricsDto.line] entry it times. Multiple cueLines
     *  can share an index for overlapping multi-vocal layers (lead + background/harmony sung at
     *  the same time, not sequentially) — mixing them in would break the "words are chronologically
     *  ordered" assumption the sweep/tap-to-seek both depend on, so only the `role == "main"` layer
     *  (or the first cueLine when there's no agent metadata at all) is kept, same principle as
     *  [fetchRadiant]'s background-vocal exclusion. */
    private fun wordsForLine(index: Int, structured: StructuredLyricsDto): List<LyricWord>? {
        val cueLines = structured.cueLine.orEmpty().filter { it.index == index }
        if (cueLines.isEmpty()) return null
        val agentsById = structured.agents.orEmpty().associateBy { it.id }
        val chosen = cueLines.firstOrNull { agentsById[it.agentId]?.role == "main" } ?: cueLines.first()
        val cues = chosen.cue.orEmpty()
        if (cues.isEmpty()) return null
        return cues.mapIndexed { i, cue ->
            LyricWord(
                text = sliceUtf8(chosen.value, cue.byteStart, cue.byteEnd),
                startMs = cue.start,
                durationMs = cueDurationMs(cue, cues.getOrNull(i + 1)?.start),
                isBackground = false,
                romanizedText = null
            )
        }
    }

    /** [CueDto.end] is optional (present on all cues in a cueLine or none, per spec) — when
     *  missing, the next cue's start stands in for it, and the very last cue in a line falls back
     *  to a short fixed duration since there's nothing to measure against. */
    private fun cueDurationMs(cue: CueDto, nextCueStartMs: Int?): Int {
        val end = cue.end
        val duration = when {
            end != null -> end - cue.start
            nextCueStartMs != null -> nextCueStartMs - cue.start
            else -> 200
        }
        return duration.coerceAtLeast(1)
    }

    /** [CueDto.byteStart]/[byteEnd] are 0-based inclusive offsets into the UTF-8 encoding of
     *  [value], not char/codepoint indices — a JVM String is UTF-16, so this re-encodes to bytes,
     *  slices, and decodes back rather than substring-ing [value] directly. */
    private fun sliceUtf8(value: String, byteStart: Int, byteEnd: Int): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val start = byteStart.coerceIn(0, bytes.size)
        val end = (byteEnd + 1).coerceIn(start, bytes.size)
        return String(bytes, start, end - start, Charsets.UTF_8)
    }

    /** lrclib's `/api/get` exact-matches whichever of `album_name`/`duration` are passed — and
     *  Navidrome's album title frequently isn't byte-identical to whatever lrclib has on file
     *  (a different edition/deluxe suffix, "feat." formatting, etc.), which 404s the whole
     *  lookup even when artist+track+duration all line up. Retrying with progressively fewer
     *  filters (drop album, then drop duration too) recovers those cases instead of silently
     *  falling through to "no lyrics" the first time either field doesn't match byte-for-byte. */
    private suspend fun fetchLrclib(track: TrackEntity): LyricsContent? {
        fetchLrclibOnce(track, includeAlbum = true, includeDuration = true)?.let { return it }
        fetchLrclibOnce(track, includeAlbum = false, includeDuration = true)?.let { return it }
        return fetchLrclibOnce(track, includeAlbum = false, includeDuration = false)
    }

    private suspend fun fetchLrclibOnce(track: TrackEntity, includeAlbum: Boolean, includeDuration: Boolean): LyricsContent? = try {
        val response = lrcLibApi.get(
            artistName = track.artistName,
            trackName = track.title,
            albumName = if (includeAlbum) track.albumName else null,
            durationSeconds = if (includeDuration) track.duration else null
        )
        when {
            !response.syncedLyrics.isNullOrBlank() -> LyricsContent.Synced(LrcParser.parse(response.syncedLyrics), LyricsSourceKind.LRCLIB)
            !response.plainLyrics.isNullOrBlank() -> LyricsContent.Plain(response.plainLyrics, LyricsSourceKind.LRCLIB)
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
