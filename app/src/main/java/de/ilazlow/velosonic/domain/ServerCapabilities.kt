package de.ilazlow.velosonic.domain

import de.ilazlow.velosonic.data.db.ServerConfigEntity

/** Mirrors the ServerConfig extension in ServerConfig.swift — version-gated capability checks. */

val ServerConfigEntity.serverType: ServerType
    get() = ServerType.fromRaw(serverTypeRaw)

private fun ServerConfigEntity.parsedVersion(): Triple<Int, Int, Int>? {
    val v = serverVersion ?: return null
    val parts = v.split(".").mapNotNull { it.toIntOrNull() }
    if (parts.size < 2) return null
    return Triple(parts[0], parts[1], parts.getOrElse(2) { 0 })
}

fun ServerConfigEntity.isAtLeastVersion(major: Int, minor: Int, patch: Int = 0): Boolean {
    val (vMajor, vMinor, vPatch) = parsedVersion() ?: return false
    if (vMajor != major) return vMajor > major
    if (vMinor != minor) return vMinor > minor
    return vPatch >= patch
}

/** True when this server supports OpenSubsonic extensions (lyrics, play queue, artist info,
 *  similar songs). Unknown/undetected servers are treated optimistically. */
val ServerConfigEntity.supportsOpenSubsonicExtensions: Boolean
    get() = serverTypeRaw == null || serverType != ServerType.SUBSONIC

/** True when this server is confirmed Navidrome, or its type is not yet detected. */
val ServerConfigEntity.mightSupportRadio: Boolean
    get() = serverTypeRaw == null || serverType == ServerType.NAVIDROME

/** Navidrome >= 0.62.0 — the OpenSubsonic `reportPlayback` extension: the server auto-scrobbles
 *  when state=stopped and positionMs >= min(50%*duration, 240s), replacing separate now-playing
 *  pings and client-side scrobble timing. */
val ServerConfigEntity.supportsReportPlayback: Boolean
    get() = serverType == ServerType.NAVIDROME && isAtLeastVersion(0, 62)

/** Navidrome >= 0.62.0 with the AudioMuse-AI WASM plugin — `getSonicSimilarTracks`. */
val ServerConfigEntity.supportsSonicSimilarity: Boolean
    get() = serverType == ServerType.NAVIDROME && isAtLeastVersion(0, 62)

/** Navidrome >= 0.61.0 — supports uploading custom playlist artwork via the native
 *  `/api/playlist/{id}/image` endpoint (used by playlist import to carry over Spotify artwork). */
val ServerConfigEntity.supportsPlaylistArtworkUpload: Boolean
    get() = serverType == ServerType.NAVIDROME && isAtLeastVersion(0, 61)

/** Confirmed Navidrome — gates use of Navidrome's native `/api/...` JWT-authenticated surface
 *  (e.g. PATCHing a share's downloadable flag), which classic Subsonic/OpenSubsonic servers
 *  don't expose at all. Unlike the "optimistic" checks above, an undetected server type is
 *  treated as *not* Navidrome here, since calling a native endpoint that doesn't exist is a
 *  guaranteed failure rather than a graceful fallback. */
val ServerConfigEntity.isNavidrome: Boolean
    get() = serverType == ServerType.NAVIDROME
