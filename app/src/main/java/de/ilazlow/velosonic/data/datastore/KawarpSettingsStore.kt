package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.kawarpSettingsDataStore by preferencesDataStore(name = "kawarp_settings")

/** Every tunable exposed by Kawarp-AGSL's README, at that library's own documented defaults —
 *  Android-only, no iOS equivalent. See [de.ilazlow.velosonic.ui.player.KawarpBackdrop] for where
 *  these actually get applied. */
data class KawarpSettings(
    /** Master switch. The settings screen itself is only reachable on API 33+ (where AGSL's
     *  `RuntimeShader` exists), so this is never read/written below that either. */
    val enabled: Boolean = false,
    val warpIntensity: Float = 1.0f,
    val blurPasses: Int = 8,
    val animationSpeed: Float = 1.0f,
    val saturation: Float = 1.5f,
    val dithering: Float = 0.008f,
    val scale: Float = 1.0f,
    val transitionDurationMs: Int = 1000,
    val tintColorR: Float = 0.157f,
    val tintColorG: Float = 0.157f,
    val tintColorB: Float = 0.235f,
    val tintIntensity: Float = 0.15f,
    val contrast: Float = 1.0f,
    val brightness: Float = 1.0f,
    val autoDarken: Float = 0f,
    /** Off by default per Kawarp-AGSL's own README — coasts the warp to a stop while paused
     *  when on. */
    val playbackReactive: Boolean = false
)

@Singleton
class KawarpSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val WARP_INTENSITY = floatPreferencesKey("warp_intensity")
        val BLUR_PASSES = intPreferencesKey("blur_passes")
        val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
        val SATURATION = floatPreferencesKey("saturation")
        val DITHERING = floatPreferencesKey("dithering")
        val SCALE = floatPreferencesKey("scale")
        val TRANSITION_DURATION_MS = intPreferencesKey("transition_duration_ms")
        val TINT_COLOR_R = floatPreferencesKey("tint_color_r")
        val TINT_COLOR_G = floatPreferencesKey("tint_color_g")
        val TINT_COLOR_B = floatPreferencesKey("tint_color_b")
        val TINT_INTENSITY = floatPreferencesKey("tint_intensity")
        val CONTRAST = floatPreferencesKey("contrast")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val AUTO_DARKEN = floatPreferencesKey("auto_darken")
        val PLAYBACK_REACTIVE = booleanPreferencesKey("playback_reactive")
    }

    val settings: Flow<KawarpSettings> = context.kawarpSettingsDataStore.data.map { prefs ->
        val defaults = KawarpSettings()
        KawarpSettings(
            enabled = prefs[Keys.ENABLED] ?: defaults.enabled,
            warpIntensity = prefs[Keys.WARP_INTENSITY] ?: defaults.warpIntensity,
            blurPasses = prefs[Keys.BLUR_PASSES] ?: defaults.blurPasses,
            animationSpeed = prefs[Keys.ANIMATION_SPEED] ?: defaults.animationSpeed,
            saturation = prefs[Keys.SATURATION] ?: defaults.saturation,
            dithering = prefs[Keys.DITHERING] ?: defaults.dithering,
            scale = prefs[Keys.SCALE] ?: defaults.scale,
            transitionDurationMs = prefs[Keys.TRANSITION_DURATION_MS] ?: defaults.transitionDurationMs,
            tintColorR = prefs[Keys.TINT_COLOR_R] ?: defaults.tintColorR,
            tintColorG = prefs[Keys.TINT_COLOR_G] ?: defaults.tintColorG,
            tintColorB = prefs[Keys.TINT_COLOR_B] ?: defaults.tintColorB,
            tintIntensity = prefs[Keys.TINT_INTENSITY] ?: defaults.tintIntensity,
            contrast = prefs[Keys.CONTRAST] ?: defaults.contrast,
            brightness = prefs[Keys.BRIGHTNESS] ?: defaults.brightness,
            autoDarken = prefs[Keys.AUTO_DARKEN] ?: defaults.autoDarken,
            playbackReactive = prefs[Keys.PLAYBACK_REACTIVE] ?: defaults.playbackReactive
        )
    }

    suspend fun setEnabled(value: Boolean) = context.kawarpSettingsDataStore.edit { it[Keys.ENABLED] = value }
    suspend fun setWarpIntensity(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.WARP_INTENSITY] = value }
    suspend fun setBlurPasses(value: Int) = context.kawarpSettingsDataStore.edit { it[Keys.BLUR_PASSES] = value }
    suspend fun setAnimationSpeed(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.ANIMATION_SPEED] = value }
    suspend fun setSaturation(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.SATURATION] = value }
    suspend fun setDithering(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.DITHERING] = value }
    suspend fun setScale(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.SCALE] = value }
    suspend fun setTransitionDurationMs(value: Int) = context.kawarpSettingsDataStore.edit { it[Keys.TRANSITION_DURATION_MS] = value }
    suspend fun setTintColor(r: Float, g: Float, b: Float) = context.kawarpSettingsDataStore.edit {
        it[Keys.TINT_COLOR_R] = r
        it[Keys.TINT_COLOR_G] = g
        it[Keys.TINT_COLOR_B] = b
    }
    suspend fun setTintIntensity(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.TINT_INTENSITY] = value }
    suspend fun setContrast(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.CONTRAST] = value }
    suspend fun setBrightness(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.BRIGHTNESS] = value }
    suspend fun setAutoDarken(value: Float) = context.kawarpSettingsDataStore.edit { it[Keys.AUTO_DARKEN] = value }
    suspend fun setPlaybackReactive(value: Boolean) = context.kawarpSettingsDataStore.edit { it[Keys.PLAYBACK_REACTIVE] = value }

    /** Resets every tunable back to Kawarp-AGSL's own documented defaults — leaves [enabled]
     *  untouched, since "reset the effect's look" shouldn't also silently turn the effect off. */
    suspend fun resetToDefaults() {
        context.kawarpSettingsDataStore.edit { prefs ->
            val defaults = KawarpSettings()
            prefs[Keys.WARP_INTENSITY] = defaults.warpIntensity
            prefs[Keys.BLUR_PASSES] = defaults.blurPasses
            prefs[Keys.ANIMATION_SPEED] = defaults.animationSpeed
            prefs[Keys.SATURATION] = defaults.saturation
            prefs[Keys.DITHERING] = defaults.dithering
            prefs[Keys.SCALE] = defaults.scale
            prefs[Keys.TRANSITION_DURATION_MS] = defaults.transitionDurationMs
            prefs[Keys.TINT_COLOR_R] = defaults.tintColorR
            prefs[Keys.TINT_COLOR_G] = defaults.tintColorG
            prefs[Keys.TINT_COLOR_B] = defaults.tintColorB
            prefs[Keys.TINT_INTENSITY] = defaults.tintIntensity
            prefs[Keys.CONTRAST] = defaults.contrast
            prefs[Keys.BRIGHTNESS] = defaults.brightness
            prefs[Keys.AUTO_DARKEN] = defaults.autoDarken
            prefs[Keys.PLAYBACK_REACTIVE] = defaults.playbackReactive
        }
    }
}
