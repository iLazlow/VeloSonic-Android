package de.ilazlow.velosonic.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShareEntryDto(
    val id: String,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null
)

@Serializable
data class SubsonicShareDto(
    val id: String,
    val url: String,
    val description: String? = null,
    val username: String? = null,
    val created: String? = null,
    val expires: String? = null,
    val visitCount: Int? = null,
    val entry: List<ShareEntryDto>? = null
)

@Serializable
data class SharesNodeDto(
    val share: List<SubsonicShareDto>? = null
)
