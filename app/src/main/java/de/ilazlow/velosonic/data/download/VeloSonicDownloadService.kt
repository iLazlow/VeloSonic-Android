package de.ilazlow.velosonic.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import dagger.hilt.android.AndroidEntryPoint
import de.ilazlow.velosonic.R
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val FOREGROUND_NOTIFICATION_ID = 2
private const val CHANNEL_ID = "velosonic_downloads"

/**
 * Media3's own download machinery ([DownloadManager]/[DownloadService]) rather than a hand-rolled
 * download queue — Media3 already tracks per-item state/progress durably (via its download
 * index) and exposes it reactively, which is strictly more than iOS's manual filesystem-scan
 * approach ([StreamingCache]) gets for free, so this isn't a literal port of that mechanism, just
 * the same end-user behavior (per-track downloads, offline playback, standalone/group tracking).
 *
 * Builds its own foreground notification rather than using Media3's default
 * [androidx.media3.exoplayer.offline.DownloadNotificationHelper] — confirmed live, that helper's
 * aggregate-progress notification for several concurrently-downloading tracks (i) never shows
 * which track is playing (`Download`/`DownloadRequest` carry no title, only the opaque
 * [de.ilazlow.velosonic.data.db.TrackEntity.id] used as the request id) and (ii) visibly jumped
 * back and forth as multiple tracks' progress combined into one number. [DownloadCacheProvider]
 * now caps `maxParallelDownloads` at 1 for the same reason [de.ilazlow.velosonic.data.sync.SyncNowWorker]
 * runs multi-server syncs sequentially — one clear "downloading X" state at a time — and the title/
 * artist embedded in [DownloadRequestMetadata] (see [DownloadRepository.downloadTrack]) is decoded
 * here to actually show it.
 */
@AndroidEntryPoint
class VeloSonicDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_notification_channel,
    0
) {
    @Inject lateinit var downloadCacheProvider: DownloadCacheProvider

    override fun getDownloadManager(): DownloadManager = downloadCacheProvider.downloadManager

    override fun getScheduler(): Scheduler = WorkManagerScheduler(this, "velosonic_downloads")

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        ensureChannel()

        val current = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING }
            ?: downloads.firstOrNull { it.state == Download.STATE_QUEUED }
        val remaining = downloads.count { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING }
        val metadata = current?.request?.data
            ?.takeIf { it.isNotEmpty() }
            ?.let { bytes -> runCatching { Json.decodeFromString<DownloadRequestMetadata>(String(bytes, Charsets.UTF_8)) }.getOrNull() }

        val title = metadata?.title ?: getString(R.string.download_notification_channel)
        val contentText = buildString {
            metadata?.artist?.let { append(it) }
            if (notMetRequirements and Requirements.NETWORK_UNMETERED != 0) {
                if (isNotEmpty()) append(" • ")
                append("Waiting for Wi-Fi")
            } else if (remaining > 1) {
                if (isNotEmpty()) append(" • ")
                append("$remaining left")
            }
        }

        val isActivelyDownloading = current?.state == Download.STATE_DOWNLOADING
        val percent = current?.percentDownloaded ?: -1f
        val indeterminate = !isActivelyDownloading || percent.isNaN() || percent < 0f

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (indeterminate) 0 else percent.toInt().coerceIn(0, 100), indeterminate)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.download_notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }
}
