package de.ilazlow.velosonic.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "E" pill — Android stand-in for iOS's `e.square.fill` SF Symbol used throughout TrackRow.swift
 * for explicit tracks. Parameterized so it can sit on both a themed surface (Search/Artist/
 * Playlist/Queue rows) and AlbumDetailScreen's white-on-dominant-color backdrop.
 */
@Composable
fun ExplicitBadge(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White.copy(alpha = 0.25f),
    contentColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "E", style = MaterialTheme.typography.labelSmall, color = contentColor, fontWeight = FontWeight.Bold)
    }
}
