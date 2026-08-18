package de.ilazlow.velosonic.ui.player

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import de.ilazlow.velosonic.data.datastore.KawarpSettings
import dev.kawarp.KawarpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live AGSL "liquid cover" Player backdrop — [Kawarp-AGSL](https://github.com/meowarex/kawarp-agsl)
 * (LGPL-3.0; see `THIRD-PARTY.md` in that repo for the upstream MIT attribution it carries,
 * settings.gradle.kts for the JitPack coordinate). Gated behind Settings → Appearance → "Liquid
 * Cover Backdrop" ([KawarpSettings.enabled]) — off by default, a real per-frame GPU shader. Only
 * ever composed when [KawarpEngine.isSupported] is true (API 33+, where AGSL's `RuntimeShader`
 * exists); callers are responsible for falling back to
 * [de.ilazlow.velosonic.ui.common.FullBleedBackdrop] otherwise or when the setting is off, exactly
 * like [de.ilazlow.velosonic.ui.player.PlayerScreen] does.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun KawarpBackdrop(artworkUrl: String?, isPlaying: Boolean, settings: KawarpSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember { KawarpEngine() }

    LaunchedEffect(engine, settings) {
        engine.setWarpIntensity(settings.warpIntensity)
        engine.setBlurPasses(settings.blurPasses)
        engine.setAnimationSpeed(settings.animationSpeed)
        engine.setSaturation(settings.saturation)
        engine.setDithering(settings.dithering)
        engine.setScale(settings.scale)
        engine.setTransitionDuration(settings.transitionDurationMs)
        engine.setTintColor(settings.tintColorR, settings.tintColorG, settings.tintColorB)
        engine.setTintIntensity(settings.tintIntensity)
        engine.setContrast(settings.contrast)
        engine.setBrightness(settings.brightness)
        engine.setAutoDarken(settings.autoDarken)
        engine.setPlaybackReactive(settings.playbackReactive)
    }

    LaunchedEffect(engine, isPlaying) { engine.setPlaying(isPlaying) }

    LaunchedEffect(engine, artworkUrl) {
        if (artworkUrl == null) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context).data(artworkUrl).allowHardware(false).build()
                (loader.execute(request) as? SuccessResult)?.image?.toBitmap()
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap is Bitmap) engine.setCover(bitmap)
    }

    // Kawarp draws itself imperatively into a native Canvas every frame rather than reading
    // Compose State, so something has to keep invalidating the draw phase — a plain frame-clock
    // tick read inside drawBehind, per Kawarp-AGSL's own documented Compose recipe. Ungated, this
    // ran at full framerate forever for as long as the Player screen was simply open, including
    // while paused with nothing left to animate — confirmed live as a real, measurable idle-GPU/
    // battery drain, not just a theoretical one. While actually playing (or while
    // playbackReactive is off, whose whole point is animating regardless of playback state) it
    // keeps ticking unconditionally; once paused with playbackReactive on, it keeps ticking only
    // until the engine's own coast-to-a-stop finishes, then the loop exits and stops requesting
    // frames — resuming automatically next time this LaunchedEffect restarts (isPlaying or
    // playbackReactive changing again).
    //
    // engine.isReady() is a required part of that break condition, not just isAnimating(): a
    // freshly created engine (every time this composable is entered fresh — reopening the Player
    // screen while already paused, most obviously) starts with nothing loaded, so isAnimating()
    // is false from frame one purely because there's nothing to animate YET, not because it
    // finished coasting. Without the isReady() check, playbackReactive+paused on a fresh engine
    // broke out of the loop before setCover's async load (a separate LaunchedEffect below) ever
    // got a chance to land — confirmed live as a real bug, not a theoretical one: the backdrop
    // stayed permanently transparent on first open until something else (starting then pausing
    // playback) happened to restart this LaunchedEffect and give the engine time to become ready
    // before hitting the break condition again.
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(engine, isPlaying, settings.playbackReactive) {
        while (true) {
            withFrameNanos { frame++ }
            if (settings.playbackReactive && !isPlaying && engine.isReady() && !engine.isAnimating()) break
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                frame
                // The engine requires a hardware-accelerated canvas (same as any RuntimeShader
                // draw) — guard rather than let an unexpected software-canvas context (e.g. a
                // screenshot/recording path) crash the whole player screen over a backdrop.
                try {
                    engine.draw(drawContext.canvas.nativeCanvas, size.width, size.height)
                } catch (e: Exception) {
                    // Leave the backdrop blank for this frame; nothing else to do about it here.
                }
            }
    )
}
