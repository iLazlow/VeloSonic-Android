package de.ilazlow.velosonic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.lyrics.LyricLine
import de.ilazlow.velosonic.data.lyrics.LyricWord
import de.ilazlow.velosonic.data.lyrics.LyricsContent
import de.ilazlow.velosonic.data.lyrics.LyricsSourceKind
import de.ilazlow.velosonic.data.lyrics.LyricsUiState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mirrors PlayerLyricsView.swift's Apple-Music-style centered scroller: source/format status
 * pills pinned to the top (each with a small caption above it — "Source"/"Mode" — so the two
 * aren't mistaken for each other), a top/bottom fade mask, uniform-size bold lines whose visual
 * hierarchy comes purely from opacity/scale/blur (never font size, so the layout never reflows as
 * the active line changes), and a per-word left-to-right sweep with a small sparkle accent on the
 * active line. A word-synced line (Radiant, or an OpenSubsonic `songLyrics` v2 response) carries
 * real per-word timestamps and sweeps off those directly; every other source is line-only and the
 * active line just lights up fully instead — an earlier version synthesized a pseudo-sweep
 * proportionally by character count (the way iOS does for its own plain-LRC lines), but the
 * guessed pacing never actually lined up with the words being sung and just looked buggy, so it
 * was dropped. The "Word-by-word"/"Line-by-line" status pill reflects which one actually
 * happened.
 */
@Composable
fun PlayerLyricsView(
    state: LyricsUiState,
    positionMs: Long,
    isPlaying: Boolean,
    onSeek: (Int) -> Unit,
    sparklesEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // The authoritative positionMs only ticks once a second (see PlaybackEngine's ticker) — driving
    // the per-word/per-character sweep straight off it made the sweep visibly jump once a second
    // instead of gliding. This extrapolates smoothly by elapsed frame time between ticks and
    // re-syncs to the authoritative value the instant a fresh one arrives, so drift never
    // accumulates beyond a single second.
    val smoothedPositionMs = rememberSmoothedPositionMs(positionMs, isPlaying)

    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is LyricsUiState.Loading -> CenteredMessage(Modifier.fillMaxSize(), showSpinner = true, text = "Loading lyrics…")
            is LyricsUiState.Empty -> CenteredMessage(Modifier.fillMaxSize(), showSpinner = false, text = "No lyrics available")
            is LyricsUiState.Loaded -> when (val content = state.content) {
                is LyricsContent.Synced -> SyncedLyrics(content, smoothedPositionMs, onSeek, sparklesEnabled, Modifier.fillMaxSize())
                is LyricsContent.Plain -> PlainLyrics(content, Modifier.fillMaxSize())
            }
        }
        if (state is LyricsUiState.Loaded) {
            val content = state.content
            StatusBadges(
                source = when (content) {
                    is LyricsContent.Synced -> content.source
                    is LyricsContent.Plain -> content.source
                },
                isSynced = content is LyricsContent.Synced,
                hasWordTiming = content is LyricsContent.Synced && content.lines.any { !it.words.isNullOrEmpty() },
                isAiSynthesized = content is LyricsContent.Synced && content.isAiSynthesized,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
            )
        }
    }
}

/** Extrapolates a per-frame position between the once-a-second authoritative ticks, restarting
 *  its baseline (and thereby snapping back in sync) every time [positionMs] actually changes —
 *  a seek, a track change, or the next tick, whichever comes first. Frozen while paused so the
 *  sweep doesn't keep gliding on its own. */
@Composable
private fun rememberSmoothedPositionMs(positionMs: Long, isPlaying: Boolean): Long {
    var smoothed by remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying) {
        smoothed = positionMs
        if (!isPlaying) return@LaunchedEffect
        var startFrameNanos = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                if (startFrameNanos < 0) startFrameNanos = frameNanos
                smoothed = positionMs + (frameNanos - startFrameNanos) / 1_000_000
            }
        }
    }
    return smoothed
}

@Composable
private fun StatusBadges(
    source: LyricsSourceKind,
    isSynced: Boolean,
    hasWordTiming: Boolean,
    isAiSynthesized: Boolean,
    modifier: Modifier = Modifier
) {
    // Scrolls instead of wrapping/clipping on a narrow width (e.g. a foldable's cover screen) —
    // up to 3 pills (source + word-timing + AI-synthesized) can appear at once, and without this
    // a too-narrow Row squeezed the last pill's Text down to a width so tight its label wrapped
    // one word per line, making the pill render tall/portrait instead of its normal flat shape.
    // Center-aligned since the source/mode pills (which carry a caption line inside them) are
    // taller than the plain single-line AI-synthesized pill.
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val sourceCaption = stringResource(id = R.string.player_lyrics_pill_label_source)
        if (source == LyricsSourceKind.RADIANT) {
            RadiantBadgePill(caption = sourceCaption)
        } else {
            val (icon, label) = when (source) {
                LyricsSourceKind.NAVIDROME -> Icons.Filled.Dns to stringResource(id = R.string.player_lyrics_source_server)
                LyricsSourceKind.LRCLIB -> Icons.Filled.LibraryMusic to "lrclib"
                LyricsSourceKind.LOCAL -> Icons.Filled.CloudDownload to "Cached"
                LyricsSourceKind.RADIANT -> Icons.Filled.AutoAwesome to "Radiant Lyrics"
            }
            StatusBadgePill(icon = icon, label = label, caption = sourceCaption)
        }
        if (isSynced) {
            val modeCaption = stringResource(id = R.string.player_lyrics_pill_label_mode)
            if (hasWordTiming) {
                StatusBadgePill(icon = Icons.Filled.GraphicEq, label = "Word-by-word", caption = modeCaption)
            } else {
                StatusBadgePill(icon = Icons.Filled.FormatAlignLeft, label = "Line-by-line", caption = modeCaption)
            }
        }
        if (isAiSynthesized) {
            StatusBadgePill(icon = Icons.Filled.AutoFixHigh, label = "AI Synthesize")
        }
    }
}

/** [caption] ("Source"/"Mode"), when given, renders as a tiny line stacked inside the same pill
 *  background above the icon/label — not floating above the pill in its own transparent space —
 *  so a bare "Server" or "Word-by-word" value doesn't read ambiguously as the other pill's kind of
 *  value at a glance. Omitted (plain single-line pill) for values that are unambiguous on their
 *  own, like "AI Synthesize". */
@Composable
private fun StatusBadgePill(icon: ImageVector, label: String, caption: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        if (caption != null) {
            Text(
                text = caption,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(12.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** Mirrors iOS's `radiantBadge` — a purple/indigo gradient capsule instead of the plain
 *  translucent one every other source badge uses, so Radiant results read as the "premium" tier
 *  they are. No animated shimmer (iOS's BadgeShimmer) — a static gradient reads as intentional
 *  branding without needing a continuously-redrawing overlay for something this small. Same
 *  in-pill [caption] treatment as [StatusBadgePill]. */
@Composable
private fun RadiantBadgePill(caption: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF9B59D0).copy(alpha = 0.55f), Color(0xFF5C6BC0).copy(alpha = 0.35f))
                )
            )
            .border(1.dp, Color(0xFF9B59D0).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        if (caption != null) {
            Text(
                text = caption,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(12.dp))
            Text(
                text = "Radiant Lyrics",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier, showSpinner: Boolean, text: String) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showSpinner) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            } else {
                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(36.dp))
            }
            Text(text = text, color = Color.White.copy(alpha = if (showSpinner) 0.6f else 0.4f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Fades content out near the top/bottom edges — the same trick as iOS's `.mask(LinearGradient)`:
 *  render into an offscreen layer, then multiply its alpha by a vertical gradient via DstIn. */
private fun Modifier.verticalFadeMask(topFraction: Float = 0.12f, bottomFraction: Float = 0.85f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                topFraction to Color.Black,
                bottomFraction to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
private fun PlainLyrics(content: LyricsContent.Plain, modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalFadeMask(0.10f, 0.88f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Text(
            text = content.text,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SyncedLyrics(content: LyricsContent.Synced, positionMs: Long, onSeek: (Int) -> Unit, sparklesEnabled: Boolean, modifier: Modifier) {
    val lines = content.lines
    val currentIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    // Mirrors iOS's 3-second post-drag backoff: auto-scroll is suppressed for a few seconds after
    // the user manually scrolls, not just while their finger is still down.
    var userScrollUntilMs by remember { mutableStateOf(0L) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrollUntilMs = System.currentTimeMillis() + 3000
        }
    }

    LaunchedEffect(currentIndex) {
        if (System.currentTimeMillis() >= userScrollUntilMs) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.verticalFadeMask(),
        contentPadding = PaddingValues(top = 80.dp, bottom = 200.dp, start = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            LyricLineRow(
                line = line,
                distance = kotlin.math.abs(index - currentIndex),
                isActive = index == currentIndex,
                positionMs = positionMs,
                sparklesEnabled = sparklesEnabled,
                onClick = { onSeek(line.startMs) }
            )
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    distance: Int,
    isActive: Boolean,
    positionMs: Long,
    sparklesEnabled: Boolean,
    onClick: () -> Unit
) {
    if (line.text.isBlank()) {
        Box(modifier = Modifier.fillMaxWidth())
        return
    }

    val alpha = when (distance) {
        0 -> 1f
        1 -> 0.55f
        2 -> 0.35f
        else -> 0.22f
    }
    val scale = when (distance) {
        0 -> 1f
        1 -> 0.97f
        2 -> 0.93f
        else -> 0.90f
    }
    val blurRadius = when (distance) {
        0, 1 -> 0.dp
        2 -> 0.5.dp
        else -> 1.5.dp
    }
    val animatedAlpha by animateFloatAsState(alpha, label = "lyric-alpha")
    val animatedScale by animateFloatAsState(scale, label = "lyric-scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
            .blur(blurRadius),
        contentAlignment = Alignment.CenterStart
    ) {
        val words = line.words
        if (isActive && !words.isNullOrEmpty()) {
            RealWordSweepLine(words = words, positionMs = positionMs, sparklesEnabled = sparklesEnabled)
        } else {
            // No real per-word timing for this line (every source other than a word-synced
            // Radiant/OpenSubsonic result) — the active line just lights up fully rather than
            // faking a sweep proportionally by character count, which looked buggy in practice
            // (the guessed pacing never actually lines up with the words being sung). Distance-0
            // (i.e. the active line) already resolves alpha/scale/blur to 1f/1f/0dp below, so this
            // is the same plain `Text` every non-active line gets, just fully bright/sharp.
            Text(
                text = line.text,
                color = Color.White.copy(alpha = animatedAlpha),
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp
            )
        }
    }
}

/** Real per-word sweep for a Radiant "Word"-type line — each [LyricWord] carries its own
 *  start/duration straight from the API, so the active word (and its intra-word fraction) comes
 *  directly from [positionMs] rather than the character-proportional guess [SweepLine] makes for
 *  line-only sources. */
@Composable
private fun RealWordSweepLine(words: List<LyricWord>, positionMs: Long, sparklesEnabled: Boolean) {
    val activeIndex = remember(words, positionMs) {
        words.indexOfLast { it.startMs <= positionMs }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        words.forEachIndexed { index, word ->
            when {
                index < activeIndex -> Text(word.text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)
                index > activeIndex -> Text(word.text, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)
                else -> {
                    val fraction = if (word.durationMs > 0) {
                        ((positionMs - word.startMs).toFloat() / word.durationMs).coerceIn(0f, 1f)
                    } else 1f
                    SweepWord(word.text, fraction, sparklesEnabled)
                }
            }
        }
    }
}

private val SPARK_COLOR = Color(1.0f, 0.93f, 0.6f)

@Composable
private fun SweepWord(word: String, fraction: Float, sparklesEnabled: Boolean) {
    Box {
        Text(word, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)
        Text(
            word,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            modifier = Modifier.drawWithContent {
                clipRect(right = size.width * fraction) {
                    this@drawWithContent.drawContent()
                }
                if (sparklesEnabled && fraction in 0.03f..0.97f) {
                    drawSweepSparkle(size.width * fraction, size.height / 2f)
                }
            }
        )
    }
}

/** Small pulsing glow + a couple of outward-drifting dots at the sweep's leading edge —
 *  a simplified stand-in for iOS's particle-canvas sparkle trail, driven by wall-clock time the
 *  same way so it costs nothing extra to attach per word. */
private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawSweepSparkle(x: Float, y: Float) {
    val t = (System.nanoTime() / 1_000_000_000.0)
    val pulse = 0.7f + 0.3f * sin(t * 6).toFloat()
    val glowRadius = 9f * pulse
    drawCircle(color = SPARK_COLOR.copy(alpha = 0.6f), radius = glowRadius, center = Offset(x, y))
    drawCircle(color = Color.White, radius = 2.4f, center = Offset(x, y))
    // Two staggered emission waves at golden-angle spacing (≈2.399 rad) so particles fill in evenly
    // rather than clumping — more of them, and each wave's phase offset keeps the trail dense
    // instead of leaving a visible gap between bursts.
    val particleCount = 14
    for (wave in 0..1) {
        val wavePhase = wave * 0.5
        for (i in 0 until particleCount) {
            val seed = i * 2.399963
            val life = ((t * 1.4 + wavePhase + seed) % 1.0).toFloat()
            val angle = seed * 2.1
            val radius = 4f + life * 16f
            val px = x + (cos(angle) * radius).toFloat()
            val py = y + (sin(angle) * radius * 0.6).toFloat() - life * 16f
            val opacity = (1f - life).coerceIn(0f, 1f) * 0.9f
            val dotSize = (3.2f * (1f - life * 0.55f)).coerceAtLeast(0.5f)
            drawCircle(color = SPARK_COLOR.copy(alpha = opacity), radius = dotSize, center = Offset(px, py))
        }
    }
}
