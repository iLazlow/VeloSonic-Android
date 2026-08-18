package de.ilazlow.velosonic.deeplink

/** Resolved `velosonic://host[:port]/type/id` target, already matched to a locally-configured
 *  server — [compositeId] is this app's own `"{serverHost}_{subsonicId}"` key (matching every
 *  other entity id in the app, e.g. [de.ilazlow.velosonic.data.db.AlbumEntity.id]), not the raw
 *  Subsonic id the link itself carries. */
sealed interface DeepLinkTarget {
    data class Album(val compositeId: String) : DeepLinkTarget
    data class Artist(val compositeId: String) : DeepLinkTarget
    data class Playlist(val compositeId: String) : DeepLinkTarget
    data class Song(val compositeId: String) : DeepLinkTarget
}
