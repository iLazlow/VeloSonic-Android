package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.domain.mightSupportRadio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the Library landing menu's conditional Radio row — mirrors `LibraryView.swift`'s own
 *  `config.mightSupportRadio` check across every configured server. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    serverRepository: ServerRepository
) : ViewModel() {
    val showRadio: StateFlow<Boolean> = serverRepository.observeServers()
        .map { servers -> servers.any { it.mightSupportRadio } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
