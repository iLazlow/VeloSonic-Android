package de.ilazlow.velosonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.datastore.AppearanceSettings
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tiny, separate from [SettingsViewModel] because [de.ilazlow.velosonic.MainActivity] needs
 *  this one value before it can even compose the theme — pulling in the whole settings surface
 *  (playback knobs, server list) just for that would be a much heavier dependency to satisfy at
 *  the activity root. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val store: AppearanceSettingsStore
) : ViewModel() {
    val settings: StateFlow<AppearanceSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    fun setDynamicColorEnabled(enabled: Boolean) = viewModelScope.launch { store.setDynamicColorEnabled(enabled) }
}
