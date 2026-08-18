package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mirrors SDArtist. `id` is composite: "{serverHost}_{subsonicId}". */
@Entity(
    tableName = "artists",
    indices = [Index("serverHost")]
)
data class ArtistEntity(
    @PrimaryKey val id: String,
    val subsonicId: String,
    val serverHost: String,
    val name: String,
    val isStarred: Boolean = false
)
