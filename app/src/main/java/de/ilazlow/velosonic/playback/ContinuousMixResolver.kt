package de.ilazlow.velosonic.playback

import de.ilazlow.velosonic.analysis.TrackStyleFeatures
import de.ilazlow.velosonic.data.artist.ArtistSubsonicClient
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.db.TrackAnalysisDao
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.sync.toStandaloneEntity
import de.ilazlow.velosonic.domain.supportsSonicSimilarity
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors iOS's `ContinuousPlaybackMode` exactly — four modes, not a simplified two. */
enum class ContinuousMixMode { SIMILAR, ARTIST, GENRE, GLOBAL }

private const val FETCH_COUNT = 20

/**
 * Ports the "continuous mix" tiered fallback from AudioPlayerManager.doFetchContinuousTracks —
 * four modes matching iOS's `ContinuousPlaybackMode` exactly: Similar (sonic/metadata similarity,
 * then on-device analysis, then global random), Artist (the seed track's own top songs via
 * Subsonic's getTopSongs), Genre (Subsonic's getRandomSongs filtered to the seed's genre), and
 * Global (unfiltered getRandomSongs). Every mode falls through to plain global random if its own
 * fetch comes back empty, so the queue never stalls regardless of which mode is picked.
 */
@Singleton
class ContinuousMixResolver @Inject constructor(
    private val client: PlaybackSubsonicClient,
    private val artistClient: ArtistSubsonicClient,
    private val trackAnalysisDao: TrackAnalysisDao,
    private val trackDao: TrackDao
) {
    suspend fun fetchNextBatch(
        mode: ContinuousMixMode,
        config: ServerConfigEntity,
        currentTrack: TrackEntity,
        excludeIds: Set<String>,
        sonicSimilarSongsEnabled: Boolean = true
    ): List<TrackEntity> {
        val host = config.host
        val fetched = when (mode) {
            ContinuousMixMode.SIMILAR -> fetchSimilar(config, currentTrack, sonicSimilarSongsEnabled)
            ContinuousMixMode.ARTIST -> artistClient.fetchTopSongs(config, currentTrack.artistName, FETCH_COUNT)
            ContinuousMixMode.GENRE -> currentTrack.genre?.takeIf(String::isNotEmpty)
                ?.let { client.randomSongs(config, FETCH_COUNT, genre = it) }
                ?: emptyList()
            ContinuousMixMode.GLOBAL -> client.randomSongs(config, FETCH_COUNT)
        }
        val deduped = fetched
            .map { it.toStandaloneEntity(host) }
            .distinctBy { it.id }
            .filter { it.id !in excludeIds }
        if (deduped.isNotEmpty()) return deduped

        if (mode == ContinuousMixMode.SIMILAR) {
            val local = fetchLocalSimilar(host, currentTrack, excludeIds)
            if (local.isNotEmpty()) return local
        }

        // Whichever mode's own fetch (server + local) came back empty — fall back to global
        // random, same as iOS's fetchGlobalMixFallback, so the queue never stalls.
        val fallback = client.randomSongs(config, FETCH_COUNT)
            .map { it.toStandaloneEntity(host) }
            .distinctBy { it.id }
            .filter { it.id !in excludeIds }
        return fallback
    }

    private suspend fun fetchSimilar(config: ServerConfigEntity, track: TrackEntity, sonicSimilarSongsEnabled: Boolean) =
        if (sonicSimilarSongsEnabled && config.supportsSonicSimilarity) {
            client.sonicSimilarTracks(config, track.subsonicId, FETCH_COUNT)
                .ifEmpty { client.similarSongs(config, track.subsonicId, FETCH_COUNT) }
        } else {
            client.similarSongs(config, track.subsonicId, FETCH_COUNT)
        }

    /** Cosine/feature similarity over [TrackAnalysisEntity] rows already on-device — no network
     *  call, so this fires instantly once there's analysis data to score against. */
    private suspend fun fetchLocalSimilar(
        serverHost: String,
        currentTrack: TrackEntity,
        excludeIds: Set<String>
    ): List<TrackEntity> {
        val currentAnalysis = trackAnalysisDao.getById(currentTrack.id) ?: return emptyList()
        val currentStyle = TrackStyleFeatures.from(currentAnalysis)

        val candidates = trackAnalysisDao.getAllForServer(serverHost)
            .filter { it.id != currentTrack.id && it.id !in excludeIds }
        if (candidates.isEmpty()) return emptyList()

        val bestIds = candidates
            .map { it.id to currentStyle.similarity(TrackStyleFeatures.from(it)) }
            .sortedByDescending { it.second }
            .take(FETCH_COUNT)
            .map { it.first }

        return trackDao.getByIds(bestIds)
    }
}
