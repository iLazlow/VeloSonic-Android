package de.ilazlow.velosonic.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.security.ApiKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Mirrors APISettingsView.swift — Spotify Client ID/Secret, stored via [ApiKeyStore]
 *  (EncryptedSharedPreferences) rather than any DataStore, since these are secrets, not
 *  ordinary settings. */
@HiltViewModel
class ApiSettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {
    private val _clientId = MutableStateFlow(apiKeyStore.loadSpotifyClientId().orEmpty())
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow(apiKeyStore.loadSpotifyClientSecret().orEmpty())
    val clientSecret: StateFlow<String> = _clientSecret.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    fun setClientId(value: String) { _clientId.value = value }
    fun setClientSecret(value: String) { _clientSecret.value = value }

    fun save() {
        apiKeyStore.saveSpotifyClientId(_clientId.value.trim())
        apiKeyStore.saveSpotifyClientSecret(_clientSecret.value.trim())
        _justSaved.value = true
    }

    fun clearJustSaved() { _justSaved.value = false }
}
