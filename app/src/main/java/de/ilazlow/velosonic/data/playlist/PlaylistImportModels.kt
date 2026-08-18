package de.ilazlow.velosonic.data.playlist

/** One track from either a Spotify playlist or an Exportify CSV export — the common shape the
 *  rest of the import pipeline (matching, preview UI) works with regardless of source. */
data class ImportSourceTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Int
)

data class ImportPlaylistInfo(
    val id: String,
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val totalTracks: Int,
    val ownerName: String?
)

enum class PlaylistImportSource(val displayName: String) {
    SPOTIFY("Spotify"),
    CSV("CSV")
}

/** Mirrors iOS's `SpotifyError`/`CSVImportError` — one sealed hierarchy since both ultimately
 *  surface the same way (a message in the import screen's error banner). */
sealed class PlaylistImportException(message: String) : Exception(message) {
    class MissingCredentials : PlaylistImportException("No Spotify credentials set. Go to Settings → API to add them.")
    class InvalidUrl : PlaylistImportException("Could not read a playlist ID from this URL.")
    class AuthFailed : PlaylistImportException("Spotify authentication failed. Check your Client ID and Secret.")
    class PlaylistNotAccessible : PlaylistImportException(
        "This playlist cannot be imported. Spotify-generated playlists (Daily Mixes, Discover Weekly, editorial playlists, etc.) are not accessible without a user login."
    )
    class FetchFailed(detail: String) : PlaylistImportException("Failed to fetch playlist: $detail")
    class InvalidEncoding : PlaylistImportException("CSV file has invalid encoding (UTF-8 expected)")
    class EmptyFile : PlaylistImportException("CSV file is empty or contains no tracks")
    class InvalidCsvFormat : PlaylistImportException(
        "Not an Exportify-format CSV — expected Track Name, Artist Name(s), Album Name and Duration (ms) columns"
    )
    class ImportFailed : PlaylistImportException("Import failed. Please try again.")
}
