package de.ilazlow.velosonic.data.artwork

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AnimatedArtwork"

/**
 * Resolves a locally-playable animated-artwork MP4 for a track, mirroring the combined effect of
 * `AudioPlayerManager.fetchAppleAnimatedURLs` + `AudioPlayerManager.exportHLS` +
 * `ArtworkManager.fetchAndPersistAnimatedArtwork` — but unified into one call, since Android has
 * no lock-screen surface pulling this data on its own separate path the way iOS's Now Playing
 * integration does (see the port research on what's iOS-only there).
 *
 * Resolution order: permanent (downloaded-track) copy → already-exported evictable-cache copy →
 * fetch the API + export HLS→MP4 fresh (gated by settings, silently returns null on any failure
 * — this hits an unofficial third-party service with no uptime guarantee).
 */
@Singleton
class AnimatedArtworkRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val appearanceSettingsStore: AppearanceSettingsStore,
    private val apiClient: AnimatedArtworkApiClient,
    private val appleArtworkCache: AppleArtworkCache,
    private val hlsExporter: HlsToMp4Exporter,
    private val permanentArtworkStore: PermanentArtworkStore
) {
    private val cacheDir = File(context.cacheDir, "animated_artwork").apply { mkdirs() }

    private fun cacheKey(serverHost: String, coverArtId: String): String {
        val safeHost = serverHost.replace(Regex("[^A-Za-z0-9]"), "_").trim('_')
        return "${safeHost}_${sanitizedArtworkId(coverArtId)}"
    }

    private fun cacheFileFor(serverHost: String, coverArtId: String): File =
        File(cacheDir, "${cacheKey(serverHost, coverArtId)}_1x1_animated.mp4")

    fun cacheSizeBytes(): Long = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Returns a `file://` URI for a playable looping MP4, or null if none is available/enabled
     *  — every caller must treat null as "just show the static/animated-WebP artwork instead",
     *  never as an error. */
    suspend fun resolve(
        serverHost: String,
        coverArtId: String?,
        trackId: String,
        artist: String,
        album: String,
        title: String
    ): String? {
        val id = coverArtId ?: run {
            Log.d(TAG, "resolve: no coverArtId for trackId=$trackId ('$artist' - '$title'), skipping")
            return null
        }

        permanentArtworkStore.localAnimatedUriFor(serverHost, id)?.let {
            Log.d(TAG, "resolve: permanent hit for trackId=$trackId coverArtId=$id -> $it")
            return it
        }

        val cacheFile = cacheFileFor(serverHost, id)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "resolve: cache-file hit for trackId=$trackId coverArtId=$id -> ${cacheFile.path}")
            return Uri.fromFile(cacheFile).toString()
        }

        val settings = appearanceSettingsStore.settings.first()
        if (!settings.animatedArtworksEnabled || !settings.applyMissingAnimatedArtworks) {
            Log.d(TAG, "resolve: disabled by settings (animatedArtworksEnabled=${settings.animatedArtworksEnabled}, applyMissingAnimatedArtworks=${settings.applyMissingAnimatedArtworks})")
            return null
        }

        val hlsUrl = resolveHlsUrl(trackId, artist, album, title, settings.animatedArtworkApiUrl)
        if (hlsUrl == null) {
            Log.d(TAG, "resolve: no HLS URL for trackId=$trackId ('$artist' - '$album' - '$title')")
            return null
        }
        Log.d(TAG, "resolve: exporting trackId=$trackId hlsUrl=$hlsUrl -> ${cacheFile.path}")
        val exported = hlsExporter.export(hlsUrl, cacheFile)
        if (exported == null) {
            Log.w(TAG, "resolve: export FAILED for trackId=$trackId hlsUrl=$hlsUrl")
            return null
        }
        Log.d(TAG, "resolve: export succeeded for trackId=$trackId -> ${exported.path} (${exported.length()} bytes)")
        return Uri.fromFile(exported).toString()
    }

    private suspend fun resolveHlsUrl(trackId: String, artist: String, album: String, title: String, apiUrlTemplate: String): String? {
        val cached = appleArtworkCache.get(trackId)
        if (cached != null) {
            Log.d(TAG, "resolveHlsUrl: AppleArtworkCache hit for trackId=$trackId -> $cached")
            return cached.takeUnless { it == "none" }
        }

        val primaryArtist = primaryArtist(artist)
        val response = apiClient.fetch(apiUrlTemplate, primaryArtist, album, title)
        Log.d(TAG, "resolveHlsUrl: API response for trackId=$trackId ('$primaryArtist' - '$album' - '$title') -> url=${response?.url}")
        appleArtworkCache.set(trackId, response?.url, response?.urlTall)
        return response?.url
    }

    /** Navidrome's raw `artist` field joins multiple artists with " • " (e.g.
     *  "KITSCHKRIEG • Blumengarten • Shirin David") — mirrors iOS's
     *  `track.artist.components(separatedBy: " • ").first ?? track.artist`: the third-party API
     *  is keyed on a single artist name, so searching with the full joined string would almost
     *  never match anything. */
    private fun primaryArtist(artist: String): String =
        artist.split(" • ").firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: artist

    /** Called once a download completes (see [de.ilazlow.velosonic.data.download.DownloadRepository])
     *  — exports (if needed/enabled) and copies into the permanent store so it survives eviction
     *  of the regular evictable cache and works fully offline. No-ops if already persisted. */
    suspend fun persistForDownload(serverHost: String, coverArtId: String?, trackId: String, artist: String, album: String, title: String) {
        val id = coverArtId ?: return
        if (permanentArtworkStore.localAnimatedUriFor(serverHost, id) != null) return

        val settings = appearanceSettingsStore.settings.first()
        if (!settings.animatedArtworksEnabled || !settings.applyMissingAnimatedArtworks) return

        val cacheFile = cacheFileFor(serverHost, id)
        val exported = if (cacheFile.exists() && cacheFile.length() > 0) {
            cacheFile
        } else {
            val hlsUrl = resolveHlsUrl(trackId, artist, album, title, settings.animatedArtworkApiUrl) ?: return
            hlsExporter.export(hlsUrl, cacheFile) ?: return
        }
        permanentArtworkStore.persistAnimatedIfNeeded(serverHost, id, exported)
    }
}
