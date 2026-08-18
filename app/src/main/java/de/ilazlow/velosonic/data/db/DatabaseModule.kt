package de.ilazlow.velosonic.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VeloSonicDatabase =
        Room.databaseBuilder(context, VeloSonicDatabase::class.java, "velosonic.db")
            // Pre-release: schema is still evolving phase to phase, no real user data to
            // preserve yet. Revisit with real Migration steps before shipping a release build.
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    @Singleton
    fun provideServerConfigDao(database: VeloSonicDatabase): ServerConfigDao = database.serverConfigDao()

    @Provides
    @Singleton
    fun provideArtistDao(database: VeloSonicDatabase): ArtistDao = database.artistDao()

    @Provides
    @Singleton
    fun provideAlbumDao(database: VeloSonicDatabase): AlbumDao = database.albumDao()

    @Provides
    @Singleton
    fun provideTrackDao(database: VeloSonicDatabase): TrackDao = database.trackDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: VeloSonicDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun provideRadioStationDao(database: VeloSonicDatabase): RadioStationDao = database.radioStationDao()

    @Provides
    @Singleton
    fun provideStandaloneDownloadDao(database: VeloSonicDatabase): StandaloneDownloadDao =
        database.standaloneDownloadDao()

    @Provides
    @Singleton
    fun provideTrackAnalysisDao(database: VeloSonicDatabase): TrackAnalysisDao = database.trackAnalysisDao()

    @Provides
    @Singleton
    fun provideSyncMetadataDao(database: VeloSonicDatabase): SyncMetadataDao = database.syncMetadataDao()

    @Provides
    @Singleton
    fun provideAnalysisSkipDao(database: VeloSonicDatabase): AnalysisSkipDao = database.analysisSkipDao()
}
