package de.ilazlow.velosonic.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Flat color wash for a whole screen — used by Album Detail behind its aspect-ratio-preserving
 * hero banner (see HeroBanner). Lighter near the top, settling to a darker shade toward the
 * bottom.
 */
@Composable
fun GradientBackdrop(baseColor: Color, modifier: Modifier = Modifier) {
    val top = baseColor.adjustBrightness(0.16f)
    val bottom = baseColor.adjustBrightness(-0.18f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, baseColor, bottom)))
    )
}

/** Full-width blurred banner of the artwork, sized by its own aspect ratio (1:1) so it isn't
 *  stretched or arbitrarily cropped — sits behind Album Detail's top bar/header, fading into
 *  [fadeToColor] (the flat wash below) toward its bottom edge. 40dp matches iOS's
 *  `AlbumDetailView` blur radius exactly (was 60dp — heavier than iOS with no reason for it). A
 *  pre-baked/downsampled-bitmap alternative to this live blur was tried and measured live: it
 *  made no difference to the RenderThread cost that shows up while a track is playing on this
 *  screen, which turned out to be unrelated to the blur (see [de.ilazlow.velosonic.ui.common.NowPlayingIndicator]'s
 *  doc comment) — so this stays a plain, live blur rather than carrying that extra complexity
 *  for no benefit. */
@Composable
fun HeroBanner(artworkUrl: String?, fadeToColor: Color, modifier: Modifier = Modifier) {
    if (artworkUrl == null) return
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(40.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to fadeToColor.copy(alpha = 0.75f),
                        1f to fadeToColor
                    )
                )
        )
    }
}

/**
 * Full-bleed blurred artwork covering the whole screen with a dark scrim on top — the Player
 * sheet's background: the real artwork stretched/cropped to fill the screen, not aspect-ratio
 * constrained like Album Detail's banner. Falls back to a flat [fallbackColor] fill (no image)
 * when there's no artwork at all — e.g. a radio station with no cover art.
 */
@Composable
fun FullBleedBackdrop(
    artworkUrl: String?,
    fallbackColor: Color = Color.Black,
    scrimAlpha: Float = 0.45f,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(fallbackColor)) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp)
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
    }
}
