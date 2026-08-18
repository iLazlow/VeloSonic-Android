package de.ilazlow.velosonic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/**
 * Thin capsule scrubber — mirrors CustomProgressBar.swift (a bare Capsule track + fill, no
 * visible thumb) rather than Material's default Slider, which has a large draggable thumb and a
 * much thicker track that reads as a completely different, heavier control.
 */
@Composable
fun PlayerProgressBar(
    fraction: Float,
    isScrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    fillColor: Color = Color.White,
    /** Radio uses this for a permanently-full, non-interactive bar — mirrors iOS's
     *  `CustomProgressBar(...).allowsHitTesting(false)` for `isPlayingRadio`, where seeking a
     *  live stream position isn't a meaningful action. */
    enabled: Boolean = true
) {
    val widthPx = remember { mutableFloatStateOf(0f) }
    val lastFraction = remember { mutableFloatStateOf(fraction) }
    lastFraction.floatValue = fraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(25.dp)
            .onSizeChanged { widthPx.floatValue = it.width.toFloat() }
            .let { base ->
                if (!enabled) return@let base
                base
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                onScrubStart()
                                val f = (offset.x / widthPx.floatValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                lastFraction.floatValue = f
                                onScrub(f)
                            },
                            onDrag = { change, _ ->
                                val f = (change.position.x / widthPx.floatValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                lastFraction.floatValue = f
                                onScrub(f)
                            },
                            onDragEnd = { onScrubEnd(lastFraction.floatValue) },
                            onDragCancel = { onScrubEnd(lastFraction.floatValue) }
                        )
                    }
                    // detectDragGestures alone never fires for a stationary tap (no motion past
                    // touch slop) — confirmed the reported bug: tapping to jump to a position
                    // silently did nothing, only an actual drag moved the scrubber. A separate
                    // tap detector on the same pointerInput target covers the "jump straight to
                    // X" gesture iOS's CustomProgressBar supports alongside dragging.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { offset ->
                            val f = (offset.x / widthPx.floatValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            lastFraction.floatValue = f
                            onScrubStart()
                            onScrub(f)
                            onScrubEnd(f)
                        })
                    }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val thickness = if (isScrubbing) 7.dp else 5.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thickness)
                .background(trackColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(thickness)
                .background(fillColor, CircleShape)
        )
    }
}
