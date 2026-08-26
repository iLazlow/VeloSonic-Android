package de.ilazlow.velosonic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    secondary = BrandSecondaryDark,
    tertiary = BrandTertiaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    secondary = BrandSecondaryLight,
    tertiary = BrandTertiaryLight
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VeloSonicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic (Material You) color — reflecting whatever the system's current color is,
    // wallpaper-extracted or a manually-picked accent — is available on Android 12+; the brand
    // seed above (matching iOS AccentColor) is the fallback on older devices.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Confirmed live on two separate Samsung devices/OneUI versions: the OS-generated
            // Material You palette (dynamicLightColorScheme/dynamicDarkColorScheme just read
            // system_accent1/2/3 resources OneUI itself populates) can come back with individual
            // tonal values broken — resolving to near-white in a light scheme, making text using
            // that token unreadable against the surface behind it. Confirmed to hit more than
            // just onSurfaceVariant (unfocused field labels/placeholders): the Sync screen's
            // description text and progress caption — same token, different screen — were
            // unreadable too, and reportedly the actual typed text inside a field (onSurface) at
            // one point as well, so this checks every token pairing this app's screens actually
            // lean on for text, not just the one first confirmed broken. This is a device-side
            // palette-cache bug, not something wrong in how this app builds its ColorScheme, and
            // there's no way to detect or repair the underlying OS cache from here — but trusting
            // the dynamic extraction unconditionally turns that OS bug into a hard usability
            // blocker (unreadable text) instead of just a cosmetic hue mismatch. Falling back to
            // the static brand-seeded scheme when any of them are clearly broken costs nothing on
            // the (overwhelming) majority of devices where the OS palette is fine.
            if (dynamic.isLegible()) dynamic else if (darkTheme) DarkColorScheme else LightColorScheme
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

/** Every foreground/background pairing this app actually renders text or icons with —
 *  onSurface/onSurfaceVariant are the two body-text tones (titles/input values vs
 *  descriptions/captions/placeholders), onPrimary/primary covers filled-button label text, and
 *  the surfaceContainer variants cover Material3's tonal card/sheet backgrounds used throughout
 *  Settings and dialogs. 3.0 is well below WCAG AA's 4.5:1 minimum for normal text (this isn't an
 *  accessibility check, just a "not literally unreadable" one) but comfortably above the ~1.0-1.2
 *  a near-white-on-white broken token actually produces — a legitimately generated dynamic
 *  palette practically never lands anywhere close to that. */
private fun ColorScheme.isLegible(): Boolean {
    val criticalPairs = listOf(
        onSurface to surface,
        onSurfaceVariant to surface,
        onSurface to surfaceContainer,
        onSurfaceVariant to surfaceContainer,
        onPrimary to primary,
        onPrimaryContainer to primaryContainer
    )
    return criticalPairs.all { (foreground, background) ->
        ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb()) >= 3.0
    }
}
