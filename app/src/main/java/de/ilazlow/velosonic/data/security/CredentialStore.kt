package de.ilazlow.velosonic.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed replacement for the iOS client's Keychain-based password storage
 * (KeychainManager.swift) — same "host:username" key scheme, one encrypted prefs file.
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "velosonic_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePassword(host: String, username: String, password: String) {
        prefs.edit().putString(key(host, username), password).apply()
    }

    fun loadPassword(host: String, username: String): String? =
        prefs.getString(key(host, username), null)

    fun deletePassword(host: String, username: String) {
        prefs.edit().remove(key(host, username)).apply()
    }

    private fun key(host: String, username: String) = "$host:$username"
}
