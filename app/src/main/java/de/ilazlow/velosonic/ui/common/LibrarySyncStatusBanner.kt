package de.ilazlow.velosonic.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R

private val SyncingColor = Color(0xFF4A90D9)
private val SyncFailedColor = Color(0xFFE0524D)

/**
 * Global "library is syncing" status pill — replaces the old full-screen blocking SyncScreen: the
 * app now drops the user straight into AppShell the moment a server is configured, and this is
 * what tells them a sync is still filling in the library underneath them instead. Mounted once in
 * AppShell above the NavHost, same pattern as [ConnectivityStatusBanner]. A failed sync (rare — a
 * transient network blip, see [de.ilazlow.velosonic.data.sync.SyncEngine.performInitialSync]'s
 * doc comment) shows an informational pill rather than a retry action here — Settings → Manage
 * Servers already has a per-server Resync button for that, no need to duplicate it.
 */
@Composable
fun LibrarySyncStatusBanner(modifier: Modifier = Modifier, viewModel: LibrarySyncBannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AnimatedVisibility(
        modifier = modifier,
        visible = state.isSyncing || state.hasFailed,
        enter = slideInVertically(animationSpec = tween(200)) { -it } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(200))
    ) {
        when {
            state.hasFailed -> SyncPill(label = stringResource(id = R.string.status_sync_failed), color = SyncFailedColor)
            state.isSyncing -> {
                val label = if (state.serverProgressLabel.isNotBlank()) {
                    "${state.serverProgressLabel}: ${state.statusMessage}"
                } else {
                    state.statusMessage
                }
                SyncPill(label = label, color = SyncingColor, showSpinner = true)
            }
        }
    }
}

@Composable
private fun SyncPill(label: String, color: Color, showSpinner: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(color)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showSpinner) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
            } else {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
