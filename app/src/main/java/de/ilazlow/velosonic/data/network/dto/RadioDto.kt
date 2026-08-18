package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RadioStationDto(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
    val coverArt: String? = null
)

@Serializable
data class InternetRadioStationsNodeDto(
    val internetRadioStation: List<RadioStationDto>? = null
)
