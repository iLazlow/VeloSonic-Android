package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mirrors SDRadioStation. `id` is composite: "{serverHost}_{subsonicId}". */
@Entity(
    tableName = "radio_stations",
    indices = [Index("serverHost")]
)
data class RadioStationEntity(
    @PrimaryKey val id: String,
    val subsonicId: String,
    val serverHost: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
    val coverArt: String? = null
)
