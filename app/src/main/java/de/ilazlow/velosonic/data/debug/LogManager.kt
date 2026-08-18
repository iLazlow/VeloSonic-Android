package de.ilazlow.velosonic.data.debug

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.datastore.DebugSettingsStore
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counterpart to iOS's `LogManager` — an in-memory ring buffer (capped, cheap to read for the
 * settings tail viewer) plus one append-only file per app process ("session"), written to
 * app-internal storage. [write] no-ops while logging is disabled (matches iOS's `guard
 * nlIsLoggingEnabled else { return }`) — confirmed live: the Debug screen's log viewer showed
 * nothing both because most call sites across the app didn't call [write] yet, and because
 * whatever few did weren't actually gated on the user's toggle at all, so enabling/disabling it
 * had no effect either way. Both are fixed here; call sites are filled in across the sync,
 * playback, and download subsystems.
 */
@Singleton
class LogManager @Inject constructor(
    @ApplicationContext private val context: Context,
    debugSettingsStore: DebugSettingsStore,
    @ApplicationScope appScope: CoroutineScope
) {
    private val maxLines = 2000
    private val lines = ArrayDeque<String>()
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile private var loggingEnabled = false

    private val _tail = MutableStateFlow<List<String>>(emptyList())
    val tail: StateFlow<List<String>> = _tail.asStateFlow()

    private val sessionFile: File by lazy {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val name = "session_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.log"
        File(dir, name)
    }

    init {
        debugSettingsStore.settings.onEach { loggingEnabled = it.loggingEnabled }.launchIn(appScope)
    }

    /** No-ops while the user hasn't enabled logging — same guard iOS's `write()` has, and for the
     *  same reason: this fires from hot paths (per-track sync progress, playback ticks), so it
     *  must be cheap to skip, not just cheap to call. */
    @Synchronized
    fun write(message: String) {
        if (!loggingEnabled) return
        val line = "[${timestampFormat.format(Date())}] $message"
        lines.addLast(line)
        while (lines.size > maxLines) lines.removeFirst()
        _tail.value = lines.toList()
        runCatching { sessionFile.appendText(line + "\n") }
    }

    fun currentSessionFile(): File = sessionFile

    fun allLogFiles(): List<File> {
        val dir = File(context.filesDir, "logs")
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    @Synchronized
    fun clearAll() {
        lines.clear()
        _tail.value = emptyList()
        allLogFiles().forEach { it.delete() }
    }
}
