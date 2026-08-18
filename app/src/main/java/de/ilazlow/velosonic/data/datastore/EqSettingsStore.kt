package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.playback.EQ_BAND_COUNT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.eqSettingsDataStore by preferencesDataStore(name = "eq_settings")

@Serializable
data class EqPreset(
    val id: String,
    val name: String,
    val gains: List<Float>,
    val builtInKey: String? = null
) {
    val isBuiltIn: Boolean get() = builtInKey != null
}

/** Verbatim gain tables from EQManager.swift's `builtInPresets` — same stable UUIDs too, kept
 *  only so a future cross-platform preset export/import would round-trip cleanly. */
object BuiltInEqPresets {
    val presets: List<EqPreset> = listOf(
        EqPreset("00000000-0000-0000-0000-000000000001", "Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), "Flat"),
        EqPreset("00000000-0000-0000-0000-000000000002", "Bass Boost", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), "Bass Boost"),
        EqPreset("00000000-0000-0000-0000-000000000003", "Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f), "Treble Boost"),
        EqPreset("00000000-0000-0000-0000-000000000004", "Rock", listOf(4f, 3f, 2f, 0f, -1f, 0f, 2f, 3f, 3f, 3f), "Rock"),
        EqPreset("00000000-0000-0000-0000-000000000005", "Pop", listOf(-1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f, -1f, -1f), "Pop"),
        EqPreset("00000000-0000-0000-0000-000000000006", "Jazz", listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f), "Jazz"),
        EqPreset("00000000-0000-0000-0000-000000000007", "Classical", listOf(3f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 2f, 3f), "Classical"),
        EqPreset("00000000-0000-0000-0000-000000000008", "Electronic", listOf(4f, 3f, 1f, 0f, -1f, -1f, 0f, 2f, 3f, 4f), "Electronic"),
        EqPreset("00000000-0000-0000-0000-000000000009", "Vocal Boost", listOf(-1f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f), "Vocal Boost")
    )
    val flat: EqPreset get() = presets.first { it.builtInKey == "Flat" }
}

data class EqSettings(
    val enabled: Boolean = false,
    val gains: List<Float> = List(EQ_BAND_COUNT) { 0f },
    val selectedPresetId: String? = null,
    val customPresets: List<EqPreset> = emptyList()
) {
    val allPresets: List<EqPreset> get() = BuiltInEqPresets.presets + customPresets
}

/** Mirrors EQManager.swift's UserDefaults-backed properties (`eqEnabled`/`eqBandGains`/
 *  `eqPresetID`/`eqPresetsV2`) as one DataStore-backed settings object — this is the first store
 *  in the codebase to serialize a structured list ([EqPreset]) into a `stringPreferencesKey`
 *  rather than a single primitive, since presets need arbitrary user-created entries. */
@Singleton
class EqSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("eq_enabled")
        val GAINS = stringPreferencesKey("eq_gains")
        val SELECTED_PRESET_ID = stringPreferencesKey("eq_selected_preset_id")
        val CUSTOM_PRESETS = stringPreferencesKey("eq_custom_presets")
    }

    val settings: Flow<EqSettings> = context.eqSettingsDataStore.data.map { prefs ->
        EqSettings(
            enabled = prefs[Keys.ENABLED] ?: false,
            gains = prefs[Keys.GAINS]?.let { decodeGains(it) } ?: List(EQ_BAND_COUNT) { 0f },
            selectedPresetId = prefs[Keys.SELECTED_PRESET_ID],
            customPresets = prefs[Keys.CUSTOM_PRESETS]?.let { decodePresets(it) } ?: emptyList()
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.eqSettingsDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setGain(band: Int, value: Float) {
        val current = settings.first()
        val updated = current.gains.toMutableList().also { it[band] = value }
        setGains(updated)
        val matching = current.allPresets.firstOrNull { it.gains == updated }
        setSelectedPresetId(matching?.id)
    }

    suspend fun applyPreset(preset: EqPreset) {
        setGains(preset.gains)
        setSelectedPresetId(preset.id)
    }

    suspend fun resetToFlat() = applyPreset(BuiltInEqPresets.flat)

    suspend fun saveCurrentAsPreset(name: String): EqPreset {
        val current = settings.first()
        val preset = EqPreset(id = UUID.randomUUID().toString(), name = name, gains = current.gains)
        setCustomPresets(current.customPresets + preset)
        setSelectedPresetId(preset.id)
        return preset
    }

    suspend fun updatePreset(updated: EqPreset) {
        val current = settings.first()
        setCustomPresets(current.customPresets.map { if (it.id == updated.id) updated else it })
        if (current.selectedPresetId == updated.id) setGains(updated.gains)
    }

    suspend fun deletePreset(preset: EqPreset) {
        if (preset.isBuiltIn) return
        val current = settings.first()
        setCustomPresets(current.customPresets.filterNot { it.id == preset.id })
        if (current.selectedPresetId == preset.id) setSelectedPresetId(null)
    }

    private suspend fun setGains(gains: List<Float>) {
        context.eqSettingsDataStore.edit { it[Keys.GAINS] = encodeGains(gains) }
    }

    private suspend fun setSelectedPresetId(id: String?) {
        context.eqSettingsDataStore.edit {
            if (id != null) it[Keys.SELECTED_PRESET_ID] = id else it.remove(Keys.SELECTED_PRESET_ID)
        }
    }

    private suspend fun setCustomPresets(presets: List<EqPreset>) {
        context.eqSettingsDataStore.edit { it[Keys.CUSTOM_PRESETS] = Json.encodeToString(presets) }
    }

    private fun encodeGains(gains: List<Float>) = gains.joinToString(",")

    private fun decodeGains(raw: String): List<Float> =
        runCatching { raw.split(",").map { it.toFloat() } }.getOrNull()
            ?.takeIf { it.size == EQ_BAND_COUNT }
            ?: List(EQ_BAND_COUNT) { 0f }

    private fun decodePresets(raw: String): List<EqPreset> =
        runCatching { Json.decodeFromString<List<EqPreset>>(raw) }.getOrDefault(emptyList())
}
