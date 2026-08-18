package de.ilazlow.velosonic.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Mirrors iOS's `SectionIndex` (`LibraryView.swift`) — a trailing-aligned A–Z strip that jumps a
 * list to a section as the finger drags down it, rather than requiring a scroll gesture per
 * letter. Used by Artists (existing sticky-header groups) and Genres.
 */
@Composable
fun SectionIndexScrubber(
    keys: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keys.size <= 1) return
    var totalHeightPx by remember { mutableStateOf(0f) }

    fun selectForOffsetY(y: Float) {
        val rowHeightPx = totalHeightPx / keys.size
        if (rowHeightPx <= 0f) return
        val index = (y / rowHeightPx).roundToInt().coerceIn(0, keys.lastIndex)
        onSelect(keys[index])
    }

    Column(
        modifier = modifier
            .padding(end = 4.dp)
            .onGloballyPositioned { totalHeightPx = it.size.height.toFloat() }
            .pointerInput(keys) {
                detectDragGestures(
                    onDragStart = { offset -> selectForOffsetY(offset.y) },
                    onDrag = { change, _ -> selectForOffsetY(change.position.y) }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { key ->
            Text(
                text = key,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 1.dp, horizontal = 4.dp)
            )
        }
    }
}
