package de.ilazlow.velosonic.data.lyrics

/** One word/syllable within a [LyricLine] with real per-word timing — only ever populated for
 *  Radiant Lyrics "Word"-type results; mirrors iOS's `LyricWord`. */
data class LyricWord(
    val text: String,
    val startMs: Int,
    val durationMs: Int,
    val isBackground: Boolean,
    val romanizedText: String?
)

/** One synced lyric line — mirrors iOS's `LyricLine`. [words] is only non-null/non-empty for a
 *  Radiant "Word"-type line; every other source leaves it null and the player synthesizes a
 *  proportional-by-character-count pseudo-sweep instead (see PlayerLyricsView). */
data class LyricLine(
    val startMs: Int,
    val text: String,
    val words: List<LyricWord>? = null,
    val romanizedText: String? = null
)

enum class LyricsSourceKind { RADIANT, NAVIDROME, LRCLIB, LOCAL }

/** Mirrors iOS's `LyricsContent` enum — either line-synced or plain, never both.
 *  [Synced.isAiSynthesized] is only ever true for a Radiant result where the API actually ran
 *  AI-generated word timing (distinct from merely requesting it — Radiant can and does ignore
 *  that request when it already has real matched data). */
sealed class LyricsContent {
    data class Synced(val lines: List<LyricLine>, val source: LyricsSourceKind, val isAiSynthesized: Boolean = false) : LyricsContent()
    data class Plain(val text: String, val source: LyricsSourceKind) : LyricsContent()
}

sealed class LyricsUiState {
    data object Loading : LyricsUiState()
    data object Empty : LyricsUiState()
    data class Loaded(val content: LyricsContent) : LyricsUiState()
}
