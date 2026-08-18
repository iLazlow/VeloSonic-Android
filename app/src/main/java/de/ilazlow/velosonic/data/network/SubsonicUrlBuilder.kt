package de.ilazlow.velosonic.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Builds Subsonic REST view URLs. Every call is parameterized by an explicit set of
 * credentials rather than an implicit "current server" — mirrors NavidromeManager.getBaseURL,
 * including the multi-server design where no singleton client owns a base URL.
 *
 * Uses HttpUrl.Builder (not raw string concatenation) so non-ASCII query values (CJK titles,
 * diacritics) are percent-encoded correctly — a real gotcha the iOS client also had to work
 * around by switching from string concat to URLComponents for search queries.
 */
object SubsonicUrlBuilder {
    private const val CLIENT_VERSION = "1.16.1"
    private const val CLIENT_NAME = "VeloSonic"

    fun build(
        host: String,
        endpoint: String,
        username: String,
        token: String,
        salt: String,
        useJson: Boolean = true,
        extraParams: Map<String, String> = emptyMap(),
        /** For params Subsonic expects repeated rather than comma-joined (e.g. several
         *  `songIdToAdd` on one `updatePlaylist` call) — a `Map` can't express the same key
         *  more than once. */
        repeatedParams: List<Pair<String, String>> = emptyList()
    ): String {
        val cleanHost = host.trimEnd('/')
        val builder = "$cleanHost/rest/$endpoint.view".toHttpUrl().newBuilder()
            .addQueryParameter("u", username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", CLIENT_VERSION)
            .addQueryParameter("c", CLIENT_NAME)

        if (useJson) {
            builder.addQueryParameter("f", "json")
        }
        extraParams.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        repeatedParams.forEach { (key, value) -> builder.addQueryParameter(key, value) }

        return builder.build().toString()
    }
}
