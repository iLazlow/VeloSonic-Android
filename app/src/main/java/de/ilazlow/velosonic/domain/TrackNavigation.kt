package de.ilazlow.velosonic.domain

import de.ilazlow.velosonic.data.db.ArtistEntry
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.sync.compositeId

/** Composite artist-route id for one of [TrackEntity.artistEntries] — null when that entry has no
 *  raw id (a legacy row synced before per-artist ids were tracked). Mirrors
 *  [de.ilazlow.velosonic.ui.player.PlayerViewModel]'s own identically-named method, which now
 *  just delegates here — this lives in `domain` so every track-row screen can resolve artist
 *  navigation the same way without depending on PlayerViewModel. */
fun TrackEntity.artistRouteId(entry: ArtistEntry): String? =
    entry.id.takeIf(String::isNotEmpty)?.let { compositeId(serverHost, it) }

/** The track's single primary artist's composite route id — null if untracked. */
fun TrackEntity.primaryArtistRouteId(): String? =
    artistId?.takeIf(String::isNotEmpty)?.let { compositeId(serverHost, it) }

/** Resolves the (routeId, displayName) pair for TrackRow's "Go to Artist" menu item — prefers the
 *  first [TrackEntity.artistEntries] id, falling back to [TrackEntity.artistId]; null if neither
 *  is known. Direct port of `TrackRow.swift`'s two id-resolution tiers; its third tier (a
 *  SwiftData lookup by artist name for legacy rows with no id at all) isn't ported since there's
 *  no equivalent name-based `ArtistDao` query yet — a rare edge case, not worth a new query for. */
fun TrackEntity.goToArtistTarget(): Pair<String, String>? {
    val firstEntry = artistEntries().firstOrNull()
    val rawId = firstEntry?.id?.takeIf(String::isNotEmpty) ?: artistId?.takeIf(String::isNotEmpty) ?: return null
    val name = firstEntry?.name?.takeIf(String::isNotEmpty) ?: artistName
    return compositeId(serverHost, rawId) to name
}
