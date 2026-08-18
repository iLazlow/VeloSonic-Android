package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors api.atomix.one/rl-api's response shape (see iOS's `RLResponse`/`RLLineData`/
 *  `RLSyllable` in RadiantLyricsManager.swift) — `type` is "Word" when syllabus-level timing is
 *  present, some other value (e.g. "Line") for line-only results. [synthesized] reflects whether
 *  the API actually ran AI synthesis for this result, not whether it was requested — Radiant can
 *  ignore a `synthesize=true` request when it already has real matched data. */
@Serializable
data class RadiantLyricsResponseDto(
    val type: String = "",
    val data: List<RadiantLineDto> = emptyList(),
    @SerialName("_synthesized") val synthesized: Boolean? = null
)

@Serializable
data class RadiantLineDto(
    val text: String = "",
    val startTime: Double = 0.0,
    val syllabus: List<RadiantSyllableDto>? = null,
    val romanized: String? = null
)

@Serializable
data class RadiantSyllableDto(
    val text: String = "",
    val time: Int = 0,
    val duration: Int = 0,
    val isBackground: Boolean = false,
    val romanized: String? = null
)
