package de.ilazlow.velosonic.ui.settings

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import de.ilazlow.velosonic.R

/** In-app per-app language override (iOS lets you set the app's language independently of the
 *  device language) — uses AppCompatDelegate's per-app language API instead of just deep-linking
 *  to Android's system App Info screen. On API 33+ this delegates to the platform LocaleManager
 *  (persists itself, also shows up in system Settings); on API 26-32 AppCompatDelegate's own
 *  backport persists the choice and re-applies it on next cold start automatically — no manual
 *  DataStore/SharedPreferences needed either way. Each entry's name is shown in its own language,
 *  not translated, matching every other OS/app language picker. */
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotBlank() }

    val languages = remember {
        listOf(
            "en" to "English",
            "de" to "Deutsch",
            "ru" to "Русский",
            "zh-Hans" to "简体中文",
            "ja" to "日本語",
            "fr" to "Français",
            "vi" to "Tiếng Việt",
            "ro" to "Română",
            "ar" to "العربية",
            "nl" to "Nederlands",
            "es" to "Español",
            "it" to "Italiano",
            "pl" to "Polski",
            "cs" to "Čeština",
            "hu" to "Magyar",
            "tr" to "Türkçe",
            "hr" to "Hrvatski",
            "sr" to "Српски",
            "ko" to "한국어",
            "pt-BR" to "Português (Brasil)",
            "pt-PT" to "Português (Portugal)",
            "el" to "Ελληνικά",
            "yi" to "יידיש"
        )
    }

    fun applyLocale(tag: String?) {
        val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
        (context as? Activity)?.recreate()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(id = R.string.settings_root_change_app_language), onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                LanguageOptionRow(
                    label = stringResource(id = R.string.settings_language_system_default),
                    selected = currentTag == null,
                    onClick = { applyLocale(null) }
                )
            }
            item { HorizontalDivider() }
            items(languages, key = { it.first }) { (tag, name) ->
                LanguageOptionRow(
                    label = name,
                    selected = currentTag?.equals(tag, ignoreCase = true) == true,
                    onClick = { applyLocale(tag) }
                )
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
