package de.ilazlow.velosonic.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ServerConfigEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        PlaylistEntity::class,
        StandaloneDownloadEntity::class,
        RadioStationEntity::class,
        TrackAnalysisEntity::class,
        SyncMetadataEntity::class,
        AnalysisSkipEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VeloSonicDatabase : RoomDatabase() {
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun radioStationDao(): RadioStationDao
    abstract fun standaloneDownloadDao(): StandaloneDownloadDao
    abstract fun trackAnalysisDao(): TrackAnalysisDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun analysisSkipDao(): AnalysisSkipDao
}
