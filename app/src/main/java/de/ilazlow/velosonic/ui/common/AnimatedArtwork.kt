package de.ilazlow.velosonic.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Looping, muted MP4 playback for animated artwork — Android equivalent of `LoopingVideoView`
 * (`AVQueuePlayer` + `AVPlayerLooper`). Uses its own private [ExoPlayer] instance (not the app's
 * shared playback engine — this never plays audio and has an entirely different lifecycle, tied
 * to whichever artwork composable is on-screen right now, not the current track).
 */
@Composable
fun LoopingVideoArtwork(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }
    )
}

/**
 * The "detail" artwork slot (full player, album/playlist detail header) — mirrors
 * `ArtworkView.artworkLayer(animate:showVideo:)`'s priority: a resolved animated video wins over
 * the static/animated-WebP image whenever both are available, exactly like iOS. [videoUri] should
 * already be settings-gated by the caller (see [de.ilazlow.velosonic.data.artwork.AnimatedArtworkRepository]);
 * [animateWebp] should already reflect `animatedArtworksEnabled`.
 */
@Composable
fun DetailArtwork(
    staticUrl: String?,
    videoUri: String?,
    contentDescription: String?,
    animateWebp: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (videoUri != null) {
            LoopingVideoArtwork(uri = videoUri, modifier = Modifier.matchParentSize())
        } else {
            CoverArtTile(
                url = staticUrl,
                contentDescription = contentDescription,
                animate = animateWebp,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
