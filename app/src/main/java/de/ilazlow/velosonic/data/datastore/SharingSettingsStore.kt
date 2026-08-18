package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sharingSettingsDataStore by preferencesDataStore(name = "sharing_settings")

/** Mirrors iOS's `ShareLinkType` — whether a newly-created share defaults to a public web link
 *  or a VeloSonic app-link (deep link into this app on the recipient's device). */
enum class ShareLinkType(val label: String) {
    PUBLIC_LINK("Public Link"), APP_LINK("App Link")
}

data class SharingSettings(val linkType: ShareLinkType = ShareLinkType.PUBLIC_LINK)

@Singleton
class SharingSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LINK_TYPE = stringPreferencesKey("share_link_type")
    }

    val settings: Flow<SharingSettings> = context.sharingSettingsDataStore.data.map { prefs ->
        SharingSettings(
            linkType = prefs[Keys.LINK_TYPE]?.let { raw -> runCatching { ShareLinkType.valueOf(raw) }.getOrNull() } ?: ShareLinkType.PUBLIC_LINK
        )
    }

    suspend fun setLinkType(type: ShareLinkType) {
        context.sharingSettingsDataStore.edit { it[Keys.LINK_TYPE] = type.name }
    }
}
