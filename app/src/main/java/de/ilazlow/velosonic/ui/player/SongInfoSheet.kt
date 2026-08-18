package de.ilazlow.velosonic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.ui.common.CoverArtTile

private data class InfoRow(val label: String, val value: String)

/**
 * Mirrors SongInfoSheet.swift's field list, trimmed to what a listener (not a developer poking
 * at raw Subsonic ids) actually finds useful — same header block, then Basic/Audio/Extended
 * groups. The local-file-probing section (iOS reads the actual cached/downloaded file's own
 * format/bitrate/size off disk) is approximated with the synced [TrackEntity] fields plus
 * [storageStatusLabel] instead of a second filesystem read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoSheet(
    track: TrackEntity,
    coverArtUrl: String?,
    storageStatusLabel: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val basic = listOfNotNull(
        track.albumName?.let { InfoRow("Album", it) },
        track.genre?.let { InfoRow("Genre", it) },
        track.trackNumber?.let { InfoRow("Track", it.toString()) },
        track.year?.let { InfoRow("Year", it.toString()) }
    )
    val audio = listOfNotNull(
        InfoRow("Status", storageStatusLabel),
        (track.suffix ?: track.contentType)?.let { InfoRow("Format", it.uppercase()) },
        track.bitRate?.takeIf { it > 0 }?.let { InfoRow("Bitrate", "$it kbps") },
        track.bitDepth?.takeIf { it > 0 }?.let { InfoRow("Bit Depth", "$it-bit") },
        track.samplingRate?.takeIf { it > 0 }?.let { InfoRow("Sample Rate", "${it / 1000.0} kHz") },
        track.channelCount?.let { InfoRow("Channels", it.toString()) },
        track.fileSize?.let { InfoRow("Size", formatFileSize(it)) },
        InfoRow("Duration", formatMs(track.duration * 1000L))
    )
    val extended = listOfNotNull(
        track.bpm?.takeIf { it > 0 }?.let { InfoRow("BPM", it.toString()) },
        track.isrc?.let { InfoRow("ISRC", it) },
        track.explicitStatus?.let { InfoRow("Explicit", it.replaceFirstChar(Char::uppercase)) },
        track.playCount?.let { InfoRow("Play Count", it.toString()) },
        track.comment?.takeIf(String::isNotBlank)?.let { InfoRow("Comment", it) }
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CoverArtTile(
                        url = coverArtUrl,
                        contentDescription = track.title,
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Column {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subline = listOfNotNull(track.year?.toString(), formatMs(track.duration * 1000L)).joinToString(" • ")
                        Text(
                            text = subline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (basic.isNotEmpty()) {
                item { SectionHeader("Basic") }
                items(basic) { InfoRowView(it) }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
            }
            if (audio.isNotEmpty()) {
                item { SectionHeader("Audio") }
                items(audio) { InfoRowView(it) }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
            }
            if (extended.isNotEmpty()) {
                item { SectionHeader("Extended") }
                items(extended) { InfoRowView(it) }
            }
            item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun InfoRowView(row: InfoRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1_000.0)
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
