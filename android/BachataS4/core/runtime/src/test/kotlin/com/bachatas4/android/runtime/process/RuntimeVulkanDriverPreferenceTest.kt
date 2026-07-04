package com.bachatas4.android.runtime.process

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeVulkanDriverPreferenceTest {
    @Test
    fun defaultsToTurnip261WhenPreferenceIsMissingOrInvalid() {
        assertEquals(RuntimeVulkanDriver.TURNIP_26_1_0, RuntimeVulkanDriverPreference.decode(null))
        assertEquals(RuntimeVulkanDriver.TURNIP_26_1_0, RuntimeVulkanDriverPreference.decode("unknown"))
    }

    @Test
    fun restoresSupportedDriver() {
        assertEquals(
            RuntimeVulkanDriver.TURNIP_25_0_0,
            RuntimeVulkanDriverPreference.decode(RuntimeVulkanDriver.TURNIP_25_0_0.name),
        )
    }
}
