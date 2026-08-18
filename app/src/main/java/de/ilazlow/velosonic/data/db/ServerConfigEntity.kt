package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per configured server. `host` is the primary key — every cached library row
 * elsewhere keys off this same host string, mirroring the iOS client's ServerConfig.
 *
 * The raw login password is deliberately NOT stored here (unlike the iOS client's
 * `navPassword`, which duplicates it into SwiftData so JWT refresh survives a DB-file import
 * on a new device) — it lives only in the Keystore-backed credential store. Android has no
 * equivalent "drop a DB file on a new device" restore flow planned yet (see Phase 13), so
 * there is no correctness need to duplicate the password into a less-protected store.
 */
@Entity(tableName = "server_configs")
data class ServerConfigEntity(
    @PrimaryKey val host: String,
    val username: String,
    val token: String,
    val salt: String,
    val name: String,
    val serverTypeRaw: String? = null,
    val serverVersion: String? = null,
    val navToken: String? = null,
    val isVisible: Boolean = true,
    val isAdmin: Boolean = false
)
