package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlayQueueNodeDto(
    val entry: List<TrackDto>? = null,
    @Serializable(with = StringOrIntSerializer::class)
    val current: String? = null,
    val position: Int? = null,
    /** ISO-8601 UTC, e.g. "2021-01-01T00:00:00.000Z" — sortable as a plain string. Used to pick
     *  the most-recently-saved queue when more than one server has one (see
     *  [de.ilazlow.velosonic.ui.home.HomeViewModel.loadQueueFromServer]). */
    val changed: String? = null
)
