package com.bachatas4.android.runtime.process

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeVulkanDriverPreferenceTest {
    @Test
    fun defaultsToSystemWhenPreferenceIsMissingOrInvalid() {
        assertEquals(RuntimeVulkanDriver.SYSTEM, RuntimeVulkanDriverPreference.decode(null))
        assertEquals(RuntimeVulkanDriver.SYSTEM, RuntimeVulkanDriverPreference.decode("unknown"))
    }

    @Test
    fun migratesRemovedBundledDriverToSystem() {
        assertEquals(
            RuntimeVulkanDriver.SYSTEM,
            RuntimeVulkanDriverPreference.decode(RuntimeVulkanDriver.TURNIP_25_0_0.name),
        )
    }
}
