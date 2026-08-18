package de.ilazlow.velosonic.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges a deep link arriving in [de.ilazlow.velosonic.MainActivity] (cold start or
 * `onNewIntent`) to [de.ilazlow.velosonic.ui.AppShell] once it's actually composed — necessary
 * because [de.ilazlow.velosonic.ui.AppRoot] gates `AppShell` behind login+initial-sync
 * completing, so a link tapped before that finishes can't be handled by a Compose Navigation
 * `navDeepLink` on a `NavHost` that may not exist yet. A [StateFlow] naturally retains the
 * latest pending target until something actually collects and consumes it, so arrival order
 * relative to `AppShell`'s composition doesn't matter.
 */
@Singleton
class DeepLinkHolder @Inject constructor() {
    private val _pending = MutableStateFlow<DeepLinkTarget?>(null)
    val pending: StateFlow<DeepLinkTarget?> = _pending.asStateFlow()

    fun set(target: DeepLinkTarget) {
        _pending.value = target
    }

    fun consume() {
        _pending.value = null
    }
}
