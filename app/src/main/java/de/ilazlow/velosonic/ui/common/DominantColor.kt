package de.ilazlow.velosonic.ui.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a wash-worthy color from artwork — mirrors AlbumDetailViewModel's
 * `extractColor`/`averageColor`, but via `Palette.dominantSwatch` rather than a hand-rolled
 * pixel average. Both exist to answer the same question ("what color should the background
 * behind this art be tinted"), and Palette's dominant-swatch clustering is the standard
 * AndroidX primitive for exactly this — same visual intent, not a literal port of the pixel math.
 */
@Composable
fun rememberDominantColor(imageUrl: String?, fallback: Color): Color {
    var color by remember(imageUrl) { mutableStateOf(fallback) }
    val context = LocalContext.current

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) {
            color = fallback
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                val bitmap: Bitmap? = (result as? SuccessResult)?.image?.toBitmap()
                bitmap?.let { Palette.from(it).generate().getDominantColor(fallback.toArgb()) }
            } catch (e: Exception) {
                null
            }
        }
        if (extracted != null) color = Color(extracted)
    }

    return color
}

/** Mirrors AlbumDetailView's `.saturation(1.4)` boost on the extracted background color —
 *  a straight average color tends to read as washed-out/gray without this. */
fun Color.boostSaturation(factor: Float = 1.4f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Adjusts HSV value (brightness) by an additive amount — used to build the light-top/dark-bottom
 *  gradients both detail screens wash their background with, without losing the extracted hue. */
fun Color.adjustBrightness(delta: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[2] = (hsv[2] + delta).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
