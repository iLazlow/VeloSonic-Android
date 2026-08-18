package de.ilazlow.velosonic.data.artwork

import java.security.MessageDigest

private val UNSAFE_ARTWORK_ID_CHARS = charArrayOf('/', ':', '?', '&', '=', '%', '#', ' ')

/**
 * Mirrors iOS's `ArtworkManager.sanitizedKey`. Some servers (e.g. Tidal-wrapper OpenSubsonic
 * proxies) hand back a full external URL as the "coverArt" id instead of the short opaque token
 * native Navidrome/Subsonic servers use. Used raw as a filename/path component, an embedded "/"
 * gets reinterpreted as a directory separator, scattering the cached file into a nested directory
 * a flat `File(dir, name).exists()` lookup never finds again — every request looks like a
 * permanent cache miss, so that server's artwork never loads from cache. Hashing any id that
 * contains filesystem- or URL-unsafe characters collapses it back to one flat, stable key;
 * ordinary short ids (the overwhelming majority of servers) pass through unchanged so existing
 * caches aren't invalidated by this.
 */
fun sanitizedArtworkId(artworkId: String): String {
    if (artworkId.none { it in UNSAFE_ARTWORK_ID_CHARS }) return artworkId
    val digest = MessageDigest.getInstance("SHA-256").digest(artworkId.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
