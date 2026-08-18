package de.ilazlow.velosonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.datastore.KawarpSettings
import de.ilazlow.velosonic.data.datastore.KawarpSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KawarpSettingsViewModel @Inject constructor(
    private val store: KawarpSettingsStore
) : ViewModel() {
    val settings: StateFlow<KawarpSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KawarpSettings())

    fun setEnabled(value: Boolean) = viewModelScope.launch { store.setEnabled(value) }
    fun setWarpIntensity(value: Float) = viewModelScope.launch { store.setWarpIntensity(value) }
    fun setBlurPasses(value: Int) = viewModelScope.launch { store.setBlurPasses(value) }
    fun setAnimationSpeed(value: Float) = viewModelScope.launch { store.setAnimationSpeed(value) }
    fun setSaturation(value: Float) = viewModelScope.launch { store.setSaturation(value) }
    fun setDithering(value: Float) = viewModelScope.launch { store.setDithering(value) }
    fun setScale(value: Float) = viewModelScope.launch { store.setScale(value) }
    fun setTransitionDurationMs(value: Int) = viewModelScope.launch { store.setTransitionDurationMs(value) }
    fun setTintColor(r: Float, g: Float, b: Float) = viewModelScope.launch { store.setTintColor(r, g, b) }
    fun setTintIntensity(value: Float) = viewModelScope.launch { store.setTintIntensity(value) }
    fun setContrast(value: Float) = viewModelScope.launch { store.setContrast(value) }
    fun setBrightness(value: Float) = viewModelScope.launch { store.setBrightness(value) }
    fun setAutoDarken(value: Float) = viewModelScope.launch { store.setAutoDarken(value) }
    fun setPlaybackReactive(value: Boolean) = viewModelScope.launch { store.setPlaybackReactive(value) }
    fun resetToDefaults() = viewModelScope.launch { store.resetToDefaults() }
}
