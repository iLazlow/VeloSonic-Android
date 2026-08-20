package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class StructuredLyricsLineDto(
    val start: Int? = null,
    val value: String = ""
)

/** `agents[]` on a `structuredLyrics` entry (songLyrics v2) — reusable vocal-layer attribution
 *  a [CueLineDto] points back to via [CueLineDto.agentId]. Exactly one entry must have
 *  `role == "main"` when agents are present at all. */
@Serializable
data class LyricsAgentDto(
    val id: String = "",
    val role: String = "",
    val name: String? = null
)

/** One word/syllable timing span within a [CueLineDto] (songLyrics v2, `enhanced=true` only).
 *  [byteStart]/[byteEnd] are 0-based inclusive offsets into the UTF-8 encoding of the parent
 *  cueLine's [CueLineDto.value] — not char/codepoint indices, see
 *  [de.ilazlow.velosonic.data.lyrics.LyricsRepository]'s parsing for the UTF-8-aware slicing this
 *  requires. */
@Serializable
data class CueDto(
    val start: Int = 0,
    val end: Int? = null,
    val value: String = "",
    val byteStart: Int = 0,
    val byteEnd: Int = 0
)

/** Word/syllable-level timing for one lyric line (songLyrics v2, `enhanced=true` only) — servers
 *  must not emit these for unsynced lyrics. [index] references the corresponding [line][StructuredLyricsDto.line]
 *  entry; multiple cueLines can share an [index] for multi-vocal lines, with the `role == "main"`
 *  one always first. */
@Serializable
data class CueLineDto(
    val index: Int = 0,
    val agentId: String? = null,
    val start: Int = 0,
    val end: Int? = null,
    val value: String = "",
    val cue: List<CueDto>? = null
)

@Serializable
data class StructuredLyricsDto(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String? = null,
    val offset: Int? = null,
    val synced: Boolean = false,
    /** songLyrics v2 (`enhanced=true`): `"main"`, `"translation"`, or `"pronunciation"` — null on
     *  a v1 server/response, treated as `"main"` by callers. */
    val kind: String? = null,
    val agents: List<LyricsAgentDto>? = null,
    val line: List<StructuredLyricsLineDto>? = null,
    val cueLine: List<CueLineDto>? = null
)

@Serializable
data class LyricsListNodeDto(
    val structuredLyrics: List<StructuredLyricsDto>? = null
)
