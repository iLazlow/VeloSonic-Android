package de.ilazlow.velosonic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
            val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            run {
                val resId = context.resources.getIdentifier("system_accent1_500", "color", "android")
                val rawAccent1 = if (resId != 0) Integer.toHexString(context.getColor(resId)) else "N/A"
                android.util.Log.d(
                    "ThemeDebug",
                    "darkTheme=$darkTheme rawAndroidSystemAccent1_500=$rawAccent1 " +
                        "resolvedPrimary=${Integer.toHexString(scheme.primary.toArgb())} " +
                        "resolvedSecondary=${Integer.toHexString(scheme.secondary.toArgb())} " +
                        "resolvedTertiary=${Integer.toHexString(scheme.tertiary.toArgb())}"
                )
            }
            scheme
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
