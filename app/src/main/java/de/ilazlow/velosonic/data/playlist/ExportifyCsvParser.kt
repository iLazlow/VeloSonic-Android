package de.ilazlow.velosonic.data.playlist

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.UUID

data class ExportifyParseResult(val info: ImportPlaylistInfo, val tracks: List<ImportSourceTrack>)

/**
 * Parses an Exportify (exportify.net) CSV export into the same [ImportSourceTrack]/
 * [ImportPlaylistInfo] shape a Spotify API fetch produces, so the rest of the import pipeline
 * (matching, preview UI) doesn't need to know which source it came from. Mirrors iOS's
 * `ExportifyCSVParser.parse` — columns are matched by name, not position, so Exportify
 * reordering or adding columns doesn't break this. Artwork is always absent for CSV imports
 * (Exportify's export doesn't carry it).
 */
object ExportifyCsvParser {
    private const val COL_TITLE = "Track Name"
    private const val COL_ARTIST = "Artist Name(s)"
    private const val COL_ALBUM = "Album Name"
    private const val COL_DURATION = "Duration (ms)"
    private const val COL_URI = "Track URI"

    fun parse(bytes: ByteArray, playlistName: String): ExportifyParseResult {
        val text = decodeText(bytes)
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n").filterNot { it.isBlank() }
        if (lines.size <= 1) throw PlaylistImportException.EmptyFile()

        val header = parseCsvLine(lines[0])
        val titleIdx = header.indexOf(COL_TITLE)
        val artistIdx = header.indexOf(COL_ARTIST)
        val albumIdx = header.indexOf(COL_ALBUM)
        val durationIdx = header.indexOf(COL_DURATION)
        val uriIdx = header.indexOf(COL_URI)
        if (titleIdx == -1 || artistIdx == -1 || albumIdx == -1 || durationIdx == -1) {
            throw PlaylistImportException.InvalidCsvFormat()
        }
        val maxIdx = maxOf(titleIdx, artistIdx, albumIdx, durationIdx)

        val tracks = mutableListOf<ImportSourceTrack>()
        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.size <= maxIdx) continue
            val title = cols[titleIdx].trim()
            if (title.isEmpty()) continue
            // Exportify joins multiple artists with ";" — re-join with ", " to match Spotify
            // API output format, so downstream matching doesn't need two code paths.
            val artist = cols[artistIdx].split(";").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            val album = cols[albumIdx].trim()
            val duration = cols[durationIdx].trim().toIntOrNull() ?: 0
            val trackId = uriIdx.takeIf { it != -1 && it < cols.size }
                ?.let { cols[it].trim() }
                ?.takeIf { it.isNotEmpty() }
                ?.substringAfterLast(":")
                ?.takeIf { it.isNotEmpty() }
                ?: UUID.randomUUID().toString()
            tracks += ImportSourceTrack(
                id = trackId,
                title = title,
                artist = artist,
                album = album,
                artworkUrl = null,
                durationMs = duration
            )
        }
        if (tracks.isEmpty()) throw PlaylistImportException.EmptyFile()

        val info = ImportPlaylistInfo(
            id = UUID.randomUUID().toString(),
            name = playlistName,
            description = null,
            artworkUrl = null,
            totalTracks = tracks.size,
            ownerName = null
        )
        return ExportifyParseResult(info, tracks)
    }

    /** Drops a trailing `.csv`, then replaces `_`/`-` with spaces — mirrors iOS's filename-to-
     *  playlist-name derivation in `setCSVFile`. */
    fun playlistNameFromFilename(filename: String): String =
        filename.removeSuffix(".csv").removeSuffix(".CSV").replace('_', ' ').replace('-', ' ').trim()

    /** UTF-8 first (strict — malformed bytes throw rather than silently substituting), falling
     *  back to ISO-8859-1 (which can represent any byte, so this practically always succeeds). */
    private fun decodeText(bytes: ByteArray): String {
        val strictUtf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            strictUtf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            try {
                String(bytes, Charsets.ISO_8859_1)
            } catch (e2: Exception) {
                throw PlaylistImportException.InvalidEncoding()
            }
        }
    }

    /** Hand-rolled RFC-4180-ish scanner (comma-delimited, `""` as an escaped quote inside a
     *  quoted field) — mirrors iOS's `parseCSVLine`. Does not handle embedded newlines inside a
     *  quoted field (the file is already split on `\n` before this runs), matching iOS's own
     *  limitation there. */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }
}
