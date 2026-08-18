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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R

private val OfflineModeColor = Color(0xFFFF9500)
private val NoNetworkColor = Color(0xFF8E8E93)

/**
 * Global status pill mirroring iOS's `ConnectivityBanner.swift` — orange "Offline Mode" when the
 * user has the Offline Mode toggle on, gray "No Network" when the device has no real connectivity,
 * nothing otherwise. Mounted once in [de.ilazlow.velosonic.ui.AppShell] above the NavHost so every
 * screen gets it for free, rather than iOS's per-screen `.safeAreaInset(edge: .top)` attachment.
 */
@Composable
fun ConnectivityStatusBanner(modifier: Modifier = Modifier, viewModel: ConnectivityBannerViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()

    AnimatedVisibility(
        modifier = modifier,
        visible = status != ConnectivityStatus.CONNECTED,
        enter = slideInVertically(animationSpec = tween(200)) { -it } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(200))
    ) {
        when (status) {
            ConnectivityStatus.OFFLINE_MODE -> ConnectivityPill(
                icon = Icons.Filled.WifiOff,
                label = stringResource(id = R.string.status_offline_mode),
                color = OfflineModeColor
            )
            ConnectivityStatus.NO_NETWORK -> ConnectivityPill(
                icon = Icons.Filled.CloudOff,
                label = stringResource(id = R.string.status_no_network),
                color = NoNetworkColor
            )
            ConnectivityStatus.CONNECTED -> Unit
        }
    }
}

@Composable
private fun ConnectivityPill(icon: ImageVector, label: String, color: Color) {
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
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
