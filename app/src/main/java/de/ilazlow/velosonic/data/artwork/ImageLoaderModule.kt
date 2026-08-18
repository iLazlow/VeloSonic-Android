package de.ilazlow.velosonic.data.artwork

import android.content.Context
import coil3.disk.DiskCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.Path.Companion.toOkioPath
import java.io.File
import javax.inject.Singleton

private const val ARTWORK_CACHE_MAX_BYTES = 250L * 1024 * 1024

/**
 * Provides the *evictable* artwork disk cache as a Hilt singleton, shared between
 * [de.ilazlow.velosonic.VeloSonicApp] (which hands it to Coil's [coil3.SingletonImageLoader])
 * and [de.ilazlow.velosonic.data.storage.StorageRepository] (which reports its size and clears
 * it from the Storage settings screen) — [DiskCache.Builder]'s own docs warn against two
 * separate `DiskCache` instances pointed at the same directory, so this must be the one and
 * only instance for as long as the process lives, not something either consumer constructs on
 * its own.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    @Provides
    @Singleton
    fun provideArtworkDiskCache(@ApplicationContext context: Context): DiskCache =
        DiskCache.Builder()
            .directory(File(context.cacheDir, "image_cache").toOkioPath())
            .maxSizeBytes(ARTWORK_CACHE_MAX_BYTES)
            .build()
}
