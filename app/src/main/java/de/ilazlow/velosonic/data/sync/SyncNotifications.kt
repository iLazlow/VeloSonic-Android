package de.ilazlow.velosonic.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import de.ilazlow.velosonic.R

private const val CHANNEL_ID = "velosonic_sync"
private const val NOTIFICATION_ID_BASE = 4000

/**
 * Ongoing, low-priority progress notification for a manually-triggered sync (initial sync, Full
 * Resync, forced Resync) — the visible half of making [SyncNowWorker] run as a genuine Android
 * foreground service. Without it, backgrounding the app mid-sync lets the OS freeze/kill the
 * process (no foreground priority == no CPU guarantee), which is exactly the "sync fails, I have
 * to manually retry" bug this exists to fix. Mirrors iOS's Live Activity for the same operation,
 * minus the Dynamic Island chrome (an explicit won't-port gap, see the port plan).
 */
object SyncNotifications {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    /** Stable per-host id so multiple servers can sync (and show progress) concurrently without
     *  clobbering each other's notification. */
    fun notificationIdFor(host: String): Int = NOTIFICATION_ID_BASE + host.hashCode()

    fun build(
        context: Context,
        title: String,
        message: String,
        progress: Double,
        indeterminate: Boolean
    ): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt().coerceIn(0, 100), indeterminate)
            .build()
}
