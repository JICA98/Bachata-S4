package com.bachatas4.android.runtime.device

import com.bachatas4.android.model.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityProbeTest {
    @Test
    fun acceptsSnapdragon8Gen3() {
        assertTrue(classify("SM8650", "Adreno 750").supported)
    }

    @Test
    fun acceptsSnapdragon8Elite() {
        assertTrue(classify("SM8750", "Adreno 830").supported)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertTrue(classify("sm8650", "qualcomm ADRENO 750").supported)
        assertTrue(classify("sm8750", "adreno 830").supported)
    }

    @Test
    fun rejectsWrongSocGpuPairings() {
        assertFalse(classify("SM8650", "Adreno 830").supported)
        assertFalse(classify("SM8750", "Adreno 750").supported)
    }

    @Test
    fun rejectsUnknownHardware() {
        assertFalse(classify("Tensor G5", "Mali").supported)
        assertFalse(classify("SM8850", "Adreno 840").supported)
    }

    @Test
    fun unverifiedGpuKeepsLaunchBlocked() {
        val probe = DeviceCapabilityProbe(
            socModelProvider = SocModelProvider { "SM8650" },
            gpuCapabilityProvider = GpuCapabilityProvider { GpuCapability.Unverified },
        )

        assertEquals(
            DeviceProfile(soc = "SM8650", gpu = "unverified", supported = false),
            probe.probe(),
        )
    }

    @Test
    fun verifiedGpuUsesClassifierWithoutAndroidRuntime() {
        val probe = DeviceCapabilityProbe(
            socModelProvider = SocModelProvider { "SM8750" },
            gpuCapabilityProvider = GpuCapabilityProvider {
                GpuCapability.Verified(model = "Adreno 830")
            },
        )

        assertEquals(
            DeviceProfile(soc = "SM8750", gpu = "Adreno 830", supported = true),
            probe.probe(),
        )
    }
}
