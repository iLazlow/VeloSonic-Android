package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.ui.common.CoverArtTile

/**
 * Transient download activity — mirrors `DownloadQueueView.swift`: three sections (in progress,
 * failed, completed) with per-item progress, per-item retry on failed rows, "Retry All"/"Cancel
 * Queue"/"Clear" actions. Presented as a local overlay from [DownloadsScreen], not a NavHost
 * destination, same pattern as [de.ilazlow.velosonic.ui.player.QueueScreen].
 */
@Composable
fun DownloadQueueScreen(onDismiss: () -> Unit, viewModel: DownloadQueueViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                text = stringResource(id = R.string.download_queue_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (state.hasActive) {
                TextButton(onClick = viewModel::cancelQueue) {
                    Text(stringResource(id = R.string.download_queue_cancel_queue), color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.hasFailed) {
                TextButton(onClick = viewModel::retryAllFailed) { Text(stringResource(id = R.string.download_queue_retry_all)) }
            }
            if (state.hasFinished) {
                TextButton(onClick = viewModel::clearFinished) { Text(stringResource(id = R.string.download_queue_clear)) }
            }
        }

        if (state.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(id = R.string.download_queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val inProgressLabel = stringResource(id = R.string.download_queue_section_in_progress)
            val failedLabel = stringResource(id = R.string.download_queue_section_failed)
            val completedLabel = stringResource(id = R.string.download_queue_section_completed)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.inProgress.isNotEmpty()) {
                    section(inProgressLabel)
                    items(state.inProgress, key = { it.download.request.id }) { item ->
                        DownloadActivityRow(item = item, coverArtUrl = viewModel::coverArtUrl, onRetry = null)
                    }
                }
                if (state.failed.isNotEmpty()) {
                    section(failedLabel)
                    items(state.failed, key = { it.download.request.id }) { item ->
                        DownloadActivityRow(
                            item = item,
                            coverArtUrl = viewModel::coverArtUrl,
                            onRetry = { viewModel.retry(item.download.request.id) }
                        )
                    }
                }
                if (state.completed.isNotEmpty()) {
                    section(completedLabel)
                    items(state.completed, key = { it.download.request.id }) { item ->
                        DownloadActivityRow(item = item, coverArtUrl = viewModel::coverArtUrl, onRetry = null)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DownloadActivityRow(
    item: DownloadActivityItem,
    coverArtUrl: (String, String?, Int) -> String?,
    onRetry: (() -> Unit)?
) {
    val track = item.track
    val download = item.download
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverArtTile(
                url = track?.let { coverArtUrl(it.serverHost, it.coverArt, 100) },
                contentDescription = track?.title,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: download.request.id,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track != null) {
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            DownloadStatusIcon(download = download, onRetry = onRetry)
        }
        if (download.state == Download.STATE_DOWNLOADING && download.percentDownloaded > 0f) {
            LinearProgressIndicator(
                progress = { (download.percentDownloaded / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun DownloadStatusIcon(download: Download, onRetry: (() -> Unit)?) {
    when (download.state) {
        Download.STATE_QUEUED -> Icon(
            Icons.Filled.HourglassEmpty,
            contentDescription = stringResource(id = R.string.download_queue_status_queued_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Download.STATE_DOWNLOADING -> if (download.percentDownloaded > 0f) {
            Text(
                text = stringResource(id = R.string.download_queue_status_percent, download.percentDownloaded.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Download.STATE_COMPLETED -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = stringResource(id = R.string.download_queue_status_completed_content_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Download.STATE_FAILED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.Filled.Error,
                contentDescription = stringResource(id = R.string.download_queue_status_failed_content_description),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            if (onRetry != null) {
                IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.download_queue_status_retry_content_description), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
