package de.ilazlow.velosonic.data.datastore

/** Mirrors iOS's `StreamFormat` enum exactly — `RAW` means "no transcoding" (iOS's `.raw` /
 *  "common.status.off"), `CUSTOM` reveals a free-text field on the [TranscodingDetail screen]. */
enum class StreamFormat(val label: String) {
    RAW("Off"), OPUS("OPUS"), MP3("MP3"), AAC("AAC"), M4A("M4A"), CUSTOM("Custom")
}

/** Mirrors iOS's `StreamBitrate` enum — `ORIGINAL` means "unlimited" (no bitrate cap sent),
 *  `CUSTOM` reveals a free-text numeric field. */
enum class StreamBitrate(val kbps: Int?) {
    ORIGINAL(null), KBPS_320(320), KBPS_256(256), KBPS_192(192), KBPS_128(128), KBPS_64(64), CUSTOM(-1)
}

/** Mirrors iOS's `DownloadNetworkPolicy` enum. */
enum class DownloadNetworkPolicy(val label: String) {
    WIFI_ONLY("WiFi Only"), WIFI_AND_CELLULAR("WiFi + Cellular")
}

/** Mirrors iOS's `formatTranscodingSummary` — used as the trailing summary text on every
 *  transcoding nav row. [offLabel] differs per call site: Playback's rows say "Off" for `.raw`,
 *  Storage's Download row says "Original" instead (matches iOS's two separate helpers). */
fun formatTranscodingSummary(
    format: StreamFormat,
    bitrate: StreamBitrate,
    customFormat: String = "",
    customBitrate: String = "",
    offLabel: String = "Off"
): String {
    if (format == StreamFormat.RAW) return offLabel
    val name = if (format == StreamFormat.CUSTOM) {
        customFormat.ifEmpty { "Custom" }.uppercase()
    } else {
        format.label.uppercase()
    }
    if (bitrate == StreamBitrate.ORIGINAL) return name
    if (bitrate == StreamBitrate.CUSTOM) {
        val kbpsLabel = customBitrate.ifEmpty { "Custom" }.let { if (it == "Custom") it else "$it kbps" }
        return "$name @ $kbpsLabel"
    }
    return "$name @ ${bitrate.kbps} kbps"
}

/** Resolves the actual Subsonic `format=` query value — null means "omit the param entirely"
 *  (RAW/no transcoding, or a blank custom value), matching iOS's `resolvedDownloadFormat`/
 *  `dlFormat != "raw"` check in `DownloadManager.swift`. Subsonic expects lowercase codec names. */
fun StreamFormat.resolvedQueryValue(customFormat: String): String? = when (this) {
    StreamFormat.RAW -> null
    StreamFormat.CUSTOM -> customFormat.trim().takeIf { it.isNotEmpty() }
    else -> name.lowercase()
}

/** Resolves the actual Subsonic `maxBitRate=` query value — null means "omit the param entirely"
 *  (Original/unlimited, or an invalid/blank custom value), matching iOS's `resolvedDownloadBitrate`/
 *  `dlBitrate > 0` check. */
fun StreamBitrate.resolvedQueryValue(customBitrate: String): Int? = when (this) {
    StreamBitrate.ORIGINAL -> null
    StreamBitrate.CUSTOM -> customBitrate.trim().toIntOrNull()?.takeIf { it > 0 }
    else -> kbps
}
