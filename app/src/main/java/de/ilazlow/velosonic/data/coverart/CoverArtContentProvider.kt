package de.ilazlow.velosonic.data.coverart

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Android Auto (and any other MediaBrowser client) flatly refuses plain `https://` URLs for
 * browse-tree/queue artwork — only local `content://`/`android.resource://` URIs are accepted.
 * This provider bridges that gap for [de.ilazlow.velosonic.playback.AutoLibrarySessionCallback]:
 * URIs shaped `content://{AUTHORITY}/{serverHost}/{coverArtId}/{size}` resolve to the real
 * Navidrome `getCoverArt` URL via [CoverArtUrlResolver] and are fetched + cached to local disk on
 * first request, exactly the pattern Google's own Media3 UAMP sample uses.
 *
 * Not a `@AndroidEntryPoint` — Hilt doesn't support that annotation on [ContentProvider] (unlike
 * Activity/Service/etc.), since a provider's `onCreate()` can run before the rest of Hilt's setup
 * is guaranteed ready (see the manifest's own comment on `WorkManagerInitializer` for a real
 * instance of this exact hazard elsewhere in this app). [EntryPointAccessors.fromApplication] is
 * the documented-safe way to reach the Hilt graph from here instead.
 */
class CoverArtContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "de.ilazlow.velosonic.coverart"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoverArtProviderEntryPoint {
        fun coverArtUrlResolver(): CoverArtUrlResolver
        fun okHttpClient(): OkHttpClient
    }

    private lateinit var coverArtUrlResolver: CoverArtUrlResolver
    private lateinit var okHttpClient: OkHttpClient

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(appContext, CoverArtProviderEntryPoint::class.java)
        coverArtUrlResolver = entryPoint.coverArtUrlResolver()
        okHttpClient = entryPoint.okHttpClient()
        return true
    }

    /** Runs on the calling process's Binder thread, which is expected to block on I/O for exactly
     *  this "hand back a file" contract (same pattern Google's own sample uses) — not something
     *  that needs its own coroutine dispatch. Returns null (Auto falls back to a generic
     *  placeholder icon) on any failure rather than throwing, since a transient network blip or an
     *  already-removed server config shouldn't crash the calling (system/Auto-host) process. */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = try {
        val segments = uri.pathSegments
        if (segments.size < 3) return null
        val host = Uri.decode(segments[0])
        val coverArtId = Uri.decode(segments[1])
        val size = segments[2].toIntOrNull() ?: 300
        val cacheFile = cacheFileFor(host, coverArtId, size)
        if (!cacheFile.exists()) {
            val remoteUrl = coverArtUrlResolver.remoteUrlFor(host, coverArtId, size) ?: return null
            fetchToFile(remoteUrl, cacheFile)
        }
        if (cacheFile.exists()) ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY) else null
    } catch (e: Exception) {
        null
    }

    private fun fetchToFile(remoteUrl: String, cacheFile: File) {
        val request = Request.Builder().url(remoteUrl).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val body = response.body ?: return
            cacheFile.parentFile?.mkdirs()
            val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tempFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            tempFile.renameTo(cacheFile)
        }
    }

    private fun cacheFileFor(host: String, coverArtId: String, size: Int): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$host|$coverArtId|$size".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheDir = File(context!!.cacheDir, "auto_coverart_cache")
        return File(cacheDir, "$digest.img")
    }

    override fun getType(uri: Uri): String = "image/*"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
