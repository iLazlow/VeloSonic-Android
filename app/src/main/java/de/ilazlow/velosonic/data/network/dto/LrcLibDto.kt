package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

/** lrclib.net's `GET /api/get` response — `syncedLyrics` is already ready-to-parse LRC text. */
@Serializable
data class LrcLibResponseDto(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)
