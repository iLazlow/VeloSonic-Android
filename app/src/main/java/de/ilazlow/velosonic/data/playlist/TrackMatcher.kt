package de.ilazlow.velosonic.data.playlist

import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.debug.LogManager
import de.ilazlow.velosonic.data.network.dto.TrackDto
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches an imported track (Spotify or CSV) against one server's library via `search3` —
 * mirrors iOS's `PlaylistImportViewModel`'s 3-stage query fallback and priority-based candidate
 * ranking. No numeric similarity score anywhere in here, by design (matching iOS): each stage is
 * a search query, each priority within a stage is a boolean predicate, and the first candidate
 * that satisfies the highest-priority rule wins — no edit distance, no fuzzy ratio, no duration
 * or album comparison.
 */
@Singleton
class TrackMatcher @Inject constructor(
    private val playlistSubsonicClient: PlaylistSubsonicClient,
    private val logManager: LogManager
) {
    suspend fun match(track: ImportSourceTrack, config: ServerConfigEntity): TrackDto? {
        val label = "'${track.title}' - '${track.artist}'"

        // Stage 1 — verbatim query.
        val q1 = "${track.title} ${track.artist}"
        findBestMatch(track, search(config, q1))?.let {
            logManager.write("[Import] matched $label via original query")
            return it
        }

        // Stage 2 — diacritic-folded/lowercased query, skipped if identical to q1 lowercased
        // (Navidrome may index e.g. 'ø' as 'o' — this catches what the verbatim query missed).
        val normTitle = normalize(track.title)
        val normArtist = normalize(track.artist)
        val q2 = "$normTitle $normArtist"
        if (q2 != q1.lowercase()) {
            val candidates = search(config, q2)
            val match = findBestMatch(track, candidates)
            if (match != null) {
                logManager.write("[Import] matched $label via normalized query '$q2'")
                return match
            }
            logManager.write("[Import] no match after normalized query '$q2' (${candidates.size} results)")
        }

        // Stage 3 — title only, for when the artist string poisons the query.
        findBestMatch(track, search(config, track.title))?.let {
            logManager.write("[Import] matched $label via title-only query")
            return it
        }

        logManager.write("[Import] no match for $label after 3 strategies")
        return null
    }

    private suspend fun search(config: ServerConfigEntity, query: String): List<TrackDto> =
        playlistSubsonicClient.searchTracks(config, query, songCount = 8)

    private fun findBestMatch(track: ImportSourceTrack, candidates: List<TrackDto>): TrackDto? {
        if (candidates.isEmpty()) return null
        val normTitle = normalize(track.title)
        val normArtist = normalize(track.artist)
        val primaryArtist = normArtist.split(Regex("[,/]")).firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: normArtist

        // Priority 1: normalized title + normalized primary-artist overlap, server order.
        candidates.firstOrNull { c ->
            val ct = normalize(c.title)
            val ca = normalize(c.artist)
            val titleOk = ct == normTitle || ct.contains(normTitle) || normTitle.contains(ct)
            val artistOk = ca.contains(primaryArtist) || primaryArtist.contains(ca) ||
                ca.contains(normArtist) || normArtist.contains(ca)
            titleOk && artistOk
        }?.let { return it }

        // Priority 2: exact normalized title match regardless of artist — guards against a
        // wrong-artist tag in a small library.
        candidates.firstOrNull { normalize(it.title) == normTitle }?.let { return it }

        // Priority 3: same shape as priority 1 but raw-lowercased, no diacritic folding — helps
        // when both sides genuinely contain the same non-Latin letter and folding would hurt.
        val rawTitle = track.title.lowercase().trim()
        val rawArtist = track.artist.lowercase().trim()
        val rawPrimaryArtist = rawArtist.split(",").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: rawArtist
        candidates.firstOrNull { c ->
            val ct = c.title.lowercase().trim()
            val ca = c.artist.lowercase().trim()
            val titleOk = ct == rawTitle || ct.contains(rawTitle) || rawTitle.contains(ct)
            val artistOk = ca.contains(rawPrimaryArtist) || rawPrimaryArtist.contains(ca)
            titleOk && artistOk
        }?.let { return it }

        return null
    }

    /** Lowercase + diacritic-fold + trim — mirrors iOS's `.folding(options: [.diacriticInsensitive,
     *  .caseInsensitive])`. Plain NFD decomposition alone doesn't fold a handful of Latin-extended
     *  letters that have no combining-mark form (ø, đ, ł, ß, æ, œ, þ), so those are replaced
     *  explicitly first — the exact cases iOS's own comments call out. */
    private fun normalize(s: String): String {
        val explicit = s.lowercase()
            .replace('ø', 'o').replace('đ', 'd').replace('ł', 'l')
            .replace("ß", "ss").replace("æ", "ae").replace("œ", "oe")
            .replace('þ', 't')
        val decomposed = Normalizer.normalize(explicit, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").trim()
    }
}
