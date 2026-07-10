package com.bachatas4.android.feature.setup

import android.content.ContextWrapper
import com.bachatas4.android.model.DeviceProfile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class SetupViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun unsupportedDeviceShowsDetectedSocAndGpu() {
        val context = object : ContextWrapper(null) {
            override fun getFilesDir(): File = temporaryFolder.root
        }
        val viewModel = SetupViewModel(downloadRuntime = true, context = context)

        viewModel.updateDeviceProfile(DeviceProfile(soc = "SM7325", gpu = "Adreno 642L", supported = false))

        val state = viewModel.state.value
        assertEquals("SM7325", state.deviceProfile.soc)
        assertEquals("Adreno 642L", state.deviceProfile.gpu)
        assertFalse(state.canEnterLibrary)
    }
}
