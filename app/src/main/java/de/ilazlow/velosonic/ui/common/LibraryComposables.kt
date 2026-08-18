package de.ilazlow.velosonic.ui.common

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.ilazlow.velosonic.data.artwork.StaticArtworkFrameCache
import kotlinx.coroutines.delay

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface StaticArtworkFrameCacheEntryPoint {
    fun staticArtworkFrameCache(): StaticArtworkFrameCache
}

private fun staticArtworkFrameCacheOf(context: Context): StaticArtworkFrameCache =
    EntryPointAccessors.fromApplication(context.applicationContext, StaticArtworkFrameCacheEntryPoint::class.java)
        .staticArtworkFrameCache()

/** Square cover-art tile with a fallback music-note glyph when there's no artwork URL yet
 *  (never-synced cover, or a server that doesn't have one for this item).
 *
 *  [animate] mirrors `ArtworkView`'s `isAnimating`/`shouldAnimate` — off by default (every list/
 *  grid row is `isDetail: false` in iOS terms), explicitly turned on only at the detail-header
 *  call sites and grid cells gated by the `animatedAlbumGridEnabled` setting. The global
 *  [coil3.SingletonImageLoader] never registers [AnimatedImageDecoder] itself (see
 *  [de.ilazlow.velosonic.VeloSonicApp]'s `serviceLoaderEnabled(false)`), so omitting it here is
 *  what makes an animated WebP render as a plain static first frame — no separate static-JPG
 *  generation step needed the way iOS's `ArtworkManager` does it, IF the fetch itself is fast.
 *
 *  It isn't always: Navidrome can serve a genuinely animated (multi-frame) WebP, which is a much
 *  bigger download than a static image of the same size — real, observed regression: a track
 *  with such a cover took 60+ seconds (or timed out outright) to show *any* artwork, on every
 *  non-animated surface that displayed it (list rows, mini player), every single time, because
 *  each one re-fetched the whole animated file just to show its first frame. [StaticArtworkFrameCache]
 *  mirrors `ArtworkManager.swift`'s `generateStaticJPG`: once, in the background, decode that
 *  first frame out and cache it as a small JPEG, then point every non-animated view at that
 *  instead — a plain static cover pays nothing extra (the cache no-ops for it). */
@Composable
fun CoverArtTile(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    animate: Boolean = false
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            val context = LocalContext.current
            val model = if (animate) {
                remember(url) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .decoderFactory(AnimatedImageDecoder.Factory())
                        .build()
                }
            } else {
                val frameCache = remember { staticArtworkFrameCacheOf(context) }
                var resolvedUrl by remember(url) { mutableStateOf(frameCache.localUriFor(url) ?: url) }
                LaunchedEffect(url) {
                    if (frameCache.localUriFor(url) != null) return@LaunchedEffect
                    // Scheduled on the app scope, not this LaunchedEffect's own — a lazy-list row
                    // can be disposed (scrolled away) well before a slow animated cover finishes,
                    // but the generation should still complete and be cached for next time. This
                    // composable separately polls for a bit so that if it's still around when
                    // generation finishes (the common case for anything worth animating, e.g. the
                    // full player), it upgrades to the fast local copy immediately rather than
                    // waiting for some future recomposition to notice.
                    frameCache.scheduleGeneration(url)
                    repeat(20) {
                        delay(500)
                        val cached = frameCache.localUriFor(url)
                        if (cached != null) {
                            resolvedUrl = cached
                            return@LaunchedEffect
                        }
                    }
                }
                resolvedUrl
            }
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Mirrors `AlbumItem.swift` — a nested-tap-target design, not one flat clickable card: the whole
 * card opens the album ([onClick]), while the artist/year sub-region has its own inner clickable
 * to the artist ([onArtistClick]) that shadows the outer one when present, exactly like iOS's
 * inner `NavigationLink` nested inside `AlbumSection`'s outer one. [artist] is nullable to match
 * iOS's `showArtist` toggle: pass null (as [de.ilazlow.velosonic.ui.artist.ArtistDetailScreen]
 * does for an artist's own album grid) to fall back to a bare year with no artist line/tap/badge
 * at all, rather than redundantly repeating the artist you're already viewing.
 */
@Composable
fun AlbumGridItem(
    title: String,
    artist: String?,
    coverArtUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    year: Int? = null,
    onArtistClick: (() -> Unit)? = null,
    serverBadgeLabel: String? = null,
    animate: Boolean = false
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CoverArtTile(url = coverArtUrl, contentDescription = title, animate = animate, modifier = Modifier.fillMaxWidth())
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (artist != null) {
            val artistYearText = if (year != null && year > 0) "$artist • $year" else artist
            Column(
                modifier = if (onArtistClick != null) Modifier.clickable(onClick = onArtistClick) else Modifier,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = artistYearText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (serverBadgeLabel != null) {
                    Text(
                        text = serverBadgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        } else if (year != null && year > 0) {
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ArtistListRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TrackListRow(
    title: String,
    subtitle: String,
    coverArtUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CoverArtTile(url = coverArtUrl, contentDescription = title, modifier = Modifier.size(48.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
