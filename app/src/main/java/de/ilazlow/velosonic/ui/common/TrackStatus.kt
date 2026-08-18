package de.ilazlow.velosonic.ui.common

/** "Download"/"Cached"/"Streaming" — mirrors iOS's `PlayerQualityIndicator` prefix exactly
 *  (including reusing the action label "Download" as the completed-download status word). Shared
 *  by every screen's "Show Info" track menu item, not just the Player screen it originated in. */
fun trackStatusLabel(isDownloaded: Boolean, isCached: Boolean): String = when {
    isDownloaded -> "Download"
    isCached -> "Cached"
    else -> "Streaming"
}
