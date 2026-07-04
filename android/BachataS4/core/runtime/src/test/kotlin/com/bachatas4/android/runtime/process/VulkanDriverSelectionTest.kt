package com.bachatas4.android.runtime.process

import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VulkanDriverSelectionTest {
    private val runtimeRoot = Paths.get("/data/user/0/com.bachatas4.android/files/runtime/current")

    @Test
    fun selectsGlibcLoaderAndLatestWinlatorTurnip() {
        val configuration = VulkanDriverConfiguration.resolve(
            RuntimeVulkanDriver.TURNIP_26_1_0,
            runtimeRoot,
        )

        assertEquals(Box64Mode.HOST_GLIBC, configuration.box64Mode)
        assertEquals(runtimeRoot.resolve("host/libvulkan.so.1").toString(), configuration.environment["SDL_VULKAN_LIBRARY"])
        assertEquals(runtimeRoot.resolve("host/vulkan/icd.d/turnip-26.1.0.json").toString(), configuration.environment["VK_ICD_FILENAMES"])
        assertFalse(configuration.environment.containsKey("BACHATA_VULKAN_BRIDGE"))
    }

    @Test
    fun retainsGlibcLoaderAndIcdForTurnipFallback() {
        val configuration = VulkanDriverConfiguration.resolve(
            RuntimeVulkanDriver.TURNIP_25_0_0,
            runtimeRoot,
        )

        assertEquals(Box64Mode.HOST_GLIBC, configuration.box64Mode)
        assertEquals(runtimeRoot.resolve("host/libvulkan.so.1").toString(), configuration.environment["SDL_VULKAN_LIBRARY"])
        assertEquals(runtimeRoot.resolve("host/vulkan/icd.d/turnip-25.0.0.json").toString(), configuration.environment["VK_ICD_FILENAMES"])
        assertFalse(configuration.environment.containsKey("BACHATA_VULKAN_BRIDGE"))
    }
}
