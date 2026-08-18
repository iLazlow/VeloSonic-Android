package de.ilazlow.velosonic.ui.common

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

private val BAR_RANGES = listOf(4f to 13f, 7f to 11f, 5f to 14f, 9f to 6f, 3f to 12f)
private const val STILL_HEIGHT = 3f

/**
 * Animated equalizer-bar "now playing" glyph — mirrors TrackRow.swift's `NowPlayingIndicator`,
 * which replaces the track-number/artwork leading slot (or, in the queue, the trailing duration)
 * for whichever row is the current track. Each bar loops independently between its own min/max
 * height on a staggered duration so the bars visibly desync rather than pulse in lockstep, and
 * settles to a flat 3dp line when playback pauses instead of freezing mid-bounce.
 *
 * Each bar box is laid out once at its own fixed (max) height; the bounce itself is a
 * [Modifier.graphicsLayer] `scaleY` anchored to the bottom edge, not an animated [Modifier.height]
 * — a draw-phase-only, GPU-composited change that never touches layout, which is strictly better
 * practice than re-running layout on every one of the ~60 frames/sec this plays for. Kept even
 * though it turned out NOT to be the fix for a live-profiled RenderThread spike (~0% paused,
 * ~20-40% playing, on Album Detail specifically): that cost persisted identically after this
 * change and after removing Album Detail's background blur entirely, so it isn't this animation's
 * layout-vs-draw-phase choice, or the blur — most likely emulator-specific GPU-compositing
 * overhead rather than something left to fix here. See git history / conversation log for the
 * full investigation if this needs revisiting.
 */
@Composable
fun NowPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.height(15.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
    ) {
        BAR_RANGES.forEachIndexed { index, (minHeight, maxHeight) ->
            val scale = remember { Animatable(STILL_HEIGHT / maxHeight) }
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    val durationMillis = 400 + index * 70
                    while (isActive) {
                        scale.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
                        scale.animateTo(minHeight / maxHeight, tween(durationMillis, easing = FastOutSlowInEasing))
                    }
                } else {
                    scale.animateTo(STILL_HEIGHT / maxHeight, tween(200, easing = LinearOutSlowInEasing))
                }
            }
            Row(
                modifier = Modifier
                    .width(3.dp)
                    .height(maxHeight.dp)
                    .graphicsLayer {
                        scaleY = scale.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            ) {}
        }
    }
}
