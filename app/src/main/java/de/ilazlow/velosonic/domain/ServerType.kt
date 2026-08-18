package de.ilazlow.velosonic.domain

/** Mirrors ServerType.swift. */
enum class ServerType(val raw: String) {
    NAVIDROME("navidrome"),
    AIRSONIC("airsonic"),
    SUBSONIC("subsonic"),
    OPEN_SUBSONIC("openSubsonic"),
    UNKNOWN("unknown");

    val displayName: String
        get() = when (this) {
            NAVIDROME -> "Navidrome"
            AIRSONIC -> "Airsonic"
            SUBSONIC -> "Subsonic"
            OPEN_SUBSONIC -> "OpenSubsonic"
            UNKNOWN -> "Subsonic"
        }

    companion object {
        fun fromRaw(raw: String?): ServerType = entries.firstOrNull { it.raw == raw } ?: UNKNOWN

        /** Initialise from the raw `type` string returned by the server's ping response. */
        fun fromServerResponse(raw: String): ServerType = when (raw.lowercase()) {
            "navidrome" -> NAVIDROME
            "airsonic", "airsonic-refix", "airsonic-advanced" -> AIRSONIC
            "subsonic" -> SUBSONIC
            "opensubsonic" -> OPEN_SUBSONIC
            else -> OPEN_SUBSONIC // unknown but OpenSubsonic-compatible
        }
    }
}
