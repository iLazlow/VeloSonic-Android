package de.ilazlow.velosonic.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed replacement for iOS's `KeychainManager.saveAPIKey/loadAPIKey/deleteAPIKey`
 * (identifiers `"spotify.clientId"` / `"spotify.clientSecret"`) — same pattern as
 * [CredentialStore], a separate encrypted prefs file so a compromised credential store doesn't
 * also leak third-party API secrets.
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "velosonic_api_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSpotifyClientId(value: String) = prefs.edit().putString(KEY_SPOTIFY_CLIENT_ID, value).apply()
    fun loadSpotifyClientId(): String? = prefs.getString(KEY_SPOTIFY_CLIENT_ID, null)

    fun saveSpotifyClientSecret(value: String) = prefs.edit().putString(KEY_SPOTIFY_CLIENT_SECRET, value).apply()
    fun loadSpotifyClientSecret(): String? = prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, null)

    private companion object {
        const val KEY_SPOTIFY_CLIENT_ID = "spotify.clientId"
        const val KEY_SPOTIFY_CLIENT_SECRET = "spotify.clientSecret"
    }
}
