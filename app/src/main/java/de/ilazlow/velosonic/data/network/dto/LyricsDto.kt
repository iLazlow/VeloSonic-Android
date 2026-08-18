package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class StructuredLyricsLineDto(
    val start: Int? = null,
    val value: String = ""
)

@Serializable
data class StructuredLyricsDto(
    val synced: Boolean = false,
    val lang: String? = null,
    val line: List<StructuredLyricsLineDto>? = null
)

@Serializable
data class LyricsListNodeDto(
    val structuredLyrics: List<StructuredLyricsDto>? = null
)
