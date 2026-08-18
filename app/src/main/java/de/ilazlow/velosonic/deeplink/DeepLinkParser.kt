package de.ilazlow.velosonic.deeplink

import android.net.Uri
import de.ilazlow.velosonic.data.db.ServerConfigEntity

/**
 * Direct port of `handleDeepLink`/`ShareTarget.appURL` from VeloSonicApp.swift — same URL shape
 * (`velosonic://host[:port]/type/id`) and the same "match by host+port against configured
 * servers, fall back to the first configured server if none match" behavior (iOS logs a warning
 * and falls back rather than dropping the link; ported that exact leniency, not a stricter
 * reject-if-unmatched policy).
 */
object DeepLinkParser {
    fun parse(uri: Uri, configs: List<ServerConfigEntity>): DeepLinkTarget? {
        if (uri.scheme != "velosonic") return null
        if (configs.isEmpty()) return null

        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val type = segments[0]
        val id = segments[1]
        if (id.isBlank()) return null

        val linkHost = uri.host
        val linkPort = uri.port.takeIf { it >= 0 }
        val matched = configs.firstOrNull { config ->
            val configUri = runCatching { Uri.parse(config.host) }.getOrNull() ?: return@firstOrNull false
            configUri.host == linkHost && configUri.port.takeIf { it >= 0 } == linkPort
        } ?: configs.first()

        val compositeId = "${matched.host}_$id"
        return when (type) {
            "album" -> DeepLinkTarget.Album(compositeId)
            "artist" -> DeepLinkTarget.Artist(compositeId)
            "playlist" -> DeepLinkTarget.Playlist(compositeId)
            "song" -> DeepLinkTarget.Song(compositeId)
            else -> null
        }
    }
}
