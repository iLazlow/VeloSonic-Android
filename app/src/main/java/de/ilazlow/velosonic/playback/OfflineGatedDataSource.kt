package de.ilazlow.velosonic.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import de.ilazlow.velosonic.data.network.OfflineModeGate
import java.io.IOException

/**
 * Wraps ExoPlayer's raw upstream [DataSource] (a [androidx.media3.datasource.DefaultHttpDataSource],
 * which talks HTTP directly rather than through the shared, already-gated OkHttpClient — see
 * [de.ilazlow.velosonic.data.network.OfflineModeInterceptor]'s doc comment for why that OkHttp-based
 * gate alone doesn't cover playback streaming) so Offline Mode blocks live network playback the same
 * way it blocks every other request, while [CacheDataSource] layers upstream of this still serve
 * already-downloaded/cached bytes straight from disk without ever reaching here.
 */
class OfflineGatedDataSource(
    private val inner: DataSource,
    private val offlineModeGate: OfflineModeGate
) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) =
        inner.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        if (offlineModeGate.isOffline()) {
            throw IOException("Offline Mode is enabled — streaming blocked: ${dataSpec.uri}")
        }
        return inner.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = inner.read(buffer, offset, length)

    override fun getUri(): Uri? = inner.uri

    override fun getResponseHeaders(): Map<String, List<String>> = inner.responseHeaders

    override fun close() = inner.close()
}

class OfflineGatedDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val offlineModeGate: OfflineModeGate
) : DataSource.Factory {
    override fun createDataSource(): DataSource = OfflineGatedDataSource(upstreamFactory.createDataSource(), offlineModeGate)
}
