package de.ilazlow.velosonic.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Routes each request to [uncached] or [cached] based on [isBypassUri] — built specifically so a
 * live internet-radio stream (unbounded, `Accept-Ranges: none`) never touches
 * [de.ilazlow.velosonic.playback.PlaybackEngine]'s disk caches. Confirmed live: once *any* bytes
 * for a station's stream URL had been written into the evictable stream cache, replaying that
 * station became a cache hit with no live HTTP response at all — so Media3's `IcyHeaders.parse
 * (dataSource.getResponseHeaders())` could never see the `icy-metaint` response header again for
 * that URL, permanently breaking the live "now playing" title for that station regardless of
 * request headers. Regular Navidrome track streams are unaffected — [isBypassUri] only ever
 * matches the one URL currently playing as radio, everything else still goes through [cached]
 * exactly as before.
 */
class RadioBypassDataSource(
    private val cached: DataSource,
    private val uncached: DataSource,
    private val isBypassUri: (Uri) -> Boolean
) : DataSource {
    private var active: DataSource = cached

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        uncached.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        active = if (isBypassUri(dataSpec.uri)) uncached else cached
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)

    override fun getUri(): Uri? = active.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders

    override fun close() = active.close()
}
