package com.bachatas4.android.feature.setup

import com.bachatas4.android.model.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SetupViewModelTest {
    @Test
    fun unsupportedDeviceShowsDetectedSocAndGpu() {
        val viewModel = SetupViewModel()

        viewModel.updateDeviceProfile(DeviceProfile(soc = "SM7325", gpu = "Adreno 642L", supported = false))

        val state = viewModel.state.value
        assertEquals("SM7325", state.deviceProfile.soc)
        assertEquals("Adreno 642L", state.deviceProfile.gpu)
        assertFalse(state.canEnterLibrary)
    }
}
