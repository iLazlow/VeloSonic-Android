package de.ilazlow.velosonic.data.sync

import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.ArtistEntity
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.dto.AlbumDetailDto
import de.ilazlow.velosonic.data.network.dto.AlbumDto
import de.ilazlow.velosonic.data.network.dto.RadioStationDto
import de.ilazlow.velosonic.data.network.dto.SubsonicArtistDto
import de.ilazlow.velosonic.data.network.dto.TrackDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** "{serverHost}_{subsonicId}" — mirrors SyncManager.compositeId, used as every entity's PK. */
fun compositeId(serverHost: String, subsonicId: String): String = "${serverHost}_$subsonicId"

/** [AlbumEntity.created] is sorted with a plain SQL `ORDER BY created DESC` (see
 *  `AlbumDao.observeNewest`) rather than a parsed-date comparison, which only produces
 *  chronological order when every row is the same lexicographically-sortable format. Most
 *  Subsonic servers (Navidrome included) send ISO-8601, but not all do — a non-ISO server mixed
 *  into a multi-server library silently corrupts "Recently Added" for everyone: its dates don't
 *  compare correctly against the ISO ones (confirmed live — a "01 Jul 2026 04:35:33 GMT"-style
 *  value sorts before every "2026-…" ISO value regardless of actual date, since '0' < '2'
 *  lexicographically, pushing that server's albums to the very end and off the LIMIT-20 cutoff).
 *  Normalizing every value to the same canonical ISO-8601 UTC string (via [Instant.toString],
 *  which always renders as "…Z") at sync time keeps the simple string-sort correct without
 *  needing a schema change or a parsed-date column. */
fun normalizeCreatedDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
    return instant?.toString() ?: raw
}

fun SubsonicArtistDto.toEntity(host: String): ArtistEntity = ArtistEntity(
    id = compositeId(host, id),
    subsonicId = id,
    serverHost = host,
    name = name
)

/** Base fields only, from the lightweight Album returned by getArtist/getAlbumList2 — the
 *  extended fields (sortName, playCount, genresList, ...) only come from getAlbum and are
 *  merged in afterward via [mergedWithDetail]. */
fun AlbumDto.toLightweightEntity(host: String): AlbumEntity = AlbumEntity(
    id = compositeId(host, id),
    subsonicId = id,
    serverHost = host,
    name = albumTitle,
    artistName = artist,
    artistId = artistId,
    artistCompositeId = artistId?.let { compositeId(host, it) },
    coverArt = coverArt,
    genre = genre,
    year = year,
    date = date
)

/** Merges getAlbum's extended metadata onto an already-built AlbumEntity, preserving whatever
 *  base/starred/isStarred fields it already had. `genre` only overwrites when the detail
 *  response actually has one — getArtist's lightweight genre is kept otherwise. */
fun AlbumEntity.mergedWithDetail(detail: AlbumDetailDto): AlbumEntity = copy(
    genre = detail.genre?.takeIf { it.isNotEmpty() } ?: genre,
    sortName = detail.sortName,
    musicBrainzId = detail.musicBrainzId,
    displayArtist = detail.displayArtist,
    played = normalizeCreatedDate(detail.played),
    playCount = detail.playCount,
    created = normalizeCreatedDate(detail.created),
    userRating = detail.userRating,
    starredAt = detail.starred,
    songCount = detail.songCount ?: detail.song?.size,
    albumDuration = detail.duration ?: detail.song?.sumOf { it.duration },
    compilation = detail.compilation,
    moods = detail.moods,
    releaseTypes = detail.releaseTypes,
    genresList = detail.genres?.map { it.name },
    artistsList = detail.artists?.map { it.name }
)

/**
 * `album` is the lightweight Album this track's parent album resolved to (for coverArt/albumId
 * fallback) — matches SDTrack's constructor call sites, which always pass the parent Album
 * alongside the song DTO rather than deriving everything from the song alone.
 */
fun TrackDto.toEntity(host: String, album: AlbumDto): TrackEntity {
    val rawArtistEntries = participants?.artist ?: artists
    return TrackEntity(
        id = compositeId(host, id),
        subsonicId = id,
        serverHost = host,
        title = title,
        artistName = artist,
        duration = duration,
        trackNumber = track,
        albumId = album.id,
        albumCompositeId = compositeId(host, album.id),
        albumName = album.albumTitle,
        artistId = artistId ?: album.artistId,
        coverArt = album.coverArt,
        suffix = suffix,
        isrc = isrc,
        explicitStatus = explicitStatus,
        parent = parent,
        genre = genre,
        genresList = genres?.map { it.name },
        year = year,
        contentType = contentType,
        bitRate = bitRate,
        bitDepth = bitDepth,
        fileSize = size,
        filePath = path,
        created = created,
        played = played,
        playCount = playCount,
        userRating = userRating,
        samplingRate = samplingRate,
        channelCount = channelCount,
        comment = comment,
        sortName = sortName,
        musicBrainzId = musicBrainzId,
        bpm = bpm,
        discNumber = discNumber,
        displayArtist = displayArtist,
        displayAlbumArtist = displayAlbumArtist,
        displayComposer = displayComposer,
        artistsList = rawArtistEntries?.map { it.name },
        artistIdsList = rawArtistEntries?.map { it.id }
    )
}

/**
 * Standalone conversion for songs that arrive without a separate parent Album object (search
 * results, similar-songs, random-songs) — uses the song DTO's own albumId/coverArt fields
 * directly instead of a passed-in Album. If the track is already synced, callers should prefer
 * looking it up in Room by composite id over this, since Room has the richer, sync-merged row.
 */
fun TrackDto.toStandaloneEntity(host: String): TrackEntity {
    val rawArtistEntries = participants?.artist ?: artists
    return TrackEntity(
        id = compositeId(host, id),
        subsonicId = id,
        serverHost = host,
        title = title,
        artistName = artist,
        duration = duration,
        trackNumber = track,
        albumId = albumId ?: "",
        albumCompositeId = albumId?.let { compositeId(host, it) },
        albumName = album,
        artistId = artistId,
        coverArt = coverArt,
        suffix = suffix,
        isrc = isrc,
        explicitStatus = explicitStatus,
        parent = parent,
        genre = genre,
        genresList = genres?.map { it.name },
        year = year,
        contentType = contentType,
        bitRate = bitRate,
        bitDepth = bitDepth,
        fileSize = size,
        filePath = path,
        created = created,
        played = played,
        playCount = playCount,
        userRating = userRating,
        samplingRate = samplingRate,
        channelCount = channelCount,
        comment = comment,
        sortName = sortName,
        musicBrainzId = musicBrainzId,
        bpm = bpm,
        discNumber = discNumber,
        displayArtist = displayArtist,
        displayAlbumArtist = displayAlbumArtist,
        displayComposer = displayComposer,
        artistsList = rawArtistEntries?.map { it.name },
        artistIdsList = rawArtistEntries?.map { it.id }
    )
}

fun RadioStationDto.toEntity(host: String): RadioStationEntity = RadioStationEntity(
    id = compositeId(host, id),
    subsonicId = id,
    serverHost = host,
    name = name,
    streamUrl = streamUrl,
    homePageUrl = homePageUrl,
    coverArt = coverArt
)
