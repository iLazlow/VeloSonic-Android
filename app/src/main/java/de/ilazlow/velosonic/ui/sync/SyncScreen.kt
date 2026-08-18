package de.ilazlow.velosonic.ui.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R

@Composable
fun SyncScreen(host: String, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Mirrors iOS's SyncView.onAppear — the very first sync attempt is a fire-and-forget kicked
    // off by ServerRepository.addServer, which this screen has no way to observe the outcome of
    // beyond SyncEngine.state. Landing here with nothing in flight (a fresh process after the
    // first attempt was interrupted, or a retry after failure) needs its own trigger, or the
    // screen sits on "Preparing…" forever with no code path left to advance it. Safe to call
    // even if a sync is already running — performInitialSync no-ops via its own syncingHosts
    // guard rather than starting a second, competing attempt.
    LaunchedEffect(host) { viewModel.retry(host) }

    val infiniteTransition = rememberInfiniteTransition(label = "sync-icon-rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync-icon-rotation-value"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.hasFailed) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.error
            )
        } else {
            // Two fixes for the reported "spins around its own axle weirdly" glitch:
            // 1. `.rotate()` used to come BEFORE `.padding(bottom = 8.dp)` in the chain, which put
            //    the bottom padding inside rotate's own measured bounds — so it was pivoting
            //    around the center of a 24×32dp box, not the icon's true 24×24 center, making it
            //    visibly orbit off-axis rather than spin in place. Padding now comes first.
            // 2. Autorenew's two curved arrows are 180°-symmetric (Sync's aren't), which reads as
            //    a clean spin rather than a lopsided wobble even with a correct pivot.
            Icon(
                imageVector = Icons.Filled.Autorenew,
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(id = if (state.hasFailed) R.string.sync_failed_title else R.string.sync_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(id = if (state.hasFailed) R.string.sync_failed_description else R.string.sync_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.hasFailed) {
            Button(onClick = { viewModel.retry(host) }, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(id = R.string.sync_retry))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { state.progress.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = state.statusMessage.ifEmpty { stringResource(id = R.string.sync_preparing) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
