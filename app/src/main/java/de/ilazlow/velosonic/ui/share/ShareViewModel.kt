package de.ilazlow.velosonic.ui.share

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.share.ShareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What's being shared — a track/album/playlist's raw Subsonic id + the server it lives on, not
 *  this app's composite id (Subsonic's `createShare` needs the id it itself issued). */
data class ShareTarget(val serverHost: String, val entityId: String, val title: String)

enum class ShareExpiryOption(@StringRes val labelRes: Int, val days: Int?) {
    NEVER(R.string.share_sheet_expiry_never, null),
    ONE_DAY(R.string.share_sheet_expiry_one_day, 1),
    SEVEN_DAYS(R.string.share_sheet_expiry_seven_days, 7),
    THIRTY_DAYS(R.string.share_sheet_expiry_thirty_days, 30);

    fun toExpiresAtEpochMs(): Long? = days?.let { System.currentTimeMillis() + it * 24L * 60 * 60 * 1000 }
}

sealed interface ShareUiState {
    data object Idle : ShareUiState
    data object Creating : ShareUiState
    data class Created(val url: String) : ShareUiState
    data object Error : ShareUiState
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val shareRepository: ShareRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    fun createShare(target: ShareTarget, description: String, expiry: ShareExpiryOption, downloadAllowed: Boolean) {
        _state.value = ShareUiState.Creating
        viewModelScope.launch {
            val share = shareRepository.createShare(
                serverHost = target.serverHost,
                entityId = target.entityId,
                description = description.ifBlank { null },
                expiresAtEpochMs = expiry.toExpiresAtEpochMs(),
                downloadAllowed = downloadAllowed
            )
            _state.value = if (share != null) ShareUiState.Created(share.url) else ShareUiState.Error
        }
    }

    fun reset() {
        _state.value = ShareUiState.Idle
    }
}
