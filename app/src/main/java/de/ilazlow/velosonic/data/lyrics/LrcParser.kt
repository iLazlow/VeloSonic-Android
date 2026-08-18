package de.ilazlow.velosonic.data.lyrics

/**
 * Direct port of LyricsManager.swift's `parseLRC`/`encodeLRC` — line-only (no stacked
 * multi-timestamp lines, no word-level `<mm:ss.xx>` tags; Navidrome/lrclib never emit either).
 * Fractional-seconds width is auto-detected per line: 2 digits = centiseconds, 3 = milliseconds,
 * anything else = treated as hundredths, matching the Swift switch exactly.
 */
object LrcParser {
    fun parse(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.split("\n")) {
            val line = raw.trim()
            if (!line.startsWith("[")) continue
            val closeIdx = line.indexOf(']')
            if (closeIdx < 0) continue
            val timeStr = line.substring(1, closeIdx)
            val text = line.substring(closeIdx + 1).trim()
            val parts = timeStr.split(":")
            if (parts.size != 2) continue
            val minutes = parts[0].toIntOrNull() ?: continue
            val secParts = parts[1].split(".")
            if (secParts.size != 2) continue
            val secs = secParts[0].toIntOrNull() ?: continue
            val subStr = secParts[1]
            val sub = subStr.toIntOrNull() ?: 0
            val ms = when (subStr.length) {
                2 -> (minutes * 60 + secs) * 1000 + sub * 10
                3 -> (minutes * 60 + secs) * 1000 + sub
                else -> (minutes * 60 + secs) * 1000 + sub * 100
            }
            lines.add(LyricLine(ms, text))
        }
        return lines.sortedBy { it.startMs }
    }

    fun encode(lines: List<LyricLine>): String = lines.joinToString("\n") { line ->
        val totalCentiseconds = line.startMs / 10
        val minutes = totalCentiseconds / 6000
        val seconds = (totalCentiseconds / 100) % 60
        val centis = totalCentiseconds % 100
        "[%02d:%02d.%02d]%s".format(minutes, seconds, centis, line.text)
    }
}
