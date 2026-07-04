package com.bachatas4.android.feature.setup

import androidx.lifecycle.ViewModel
import com.bachatas4.android.model.DeviceProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SetupUiState(
    val deviceProfile: DeviceProfile,
    val runtimeInstalled: Boolean,
    val integrityVerified: Boolean,
    val legalNotice: String,
) {
    val canEnterLibrary: Boolean
        get() = deviceProfile.supported && runtimeInstalled && integrityVerified
}

@HiltViewModel
class SetupViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(
        SetupUiState(
            deviceProfile = DeviceProfile(soc = "unknown", gpu = "unverified", supported = false),
            runtimeInstalled = false,
            integrityVerified = false,
            legalNotice = "Import only games and firmware content you legally own.",
        ),
    )

    val state: StateFlow<SetupUiState> = mutableState

    fun updateDeviceProfile(profile: DeviceProfile) {
        mutableState.value = mutableState.value.copy(deviceProfile = profile)
    }
}
