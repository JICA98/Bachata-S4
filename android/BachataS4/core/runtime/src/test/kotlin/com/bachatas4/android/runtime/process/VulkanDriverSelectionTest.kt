package com.bachatas4.android.runtime.process

import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VulkanDriverSelectionTest {
    private val runtimeRoot = Paths.get("/data/user/0/com.bachatas4.android/files/runtime/current")

    @Test
    fun selectsImportedGlibcDriverManifest() {
        val customRoot = Paths.get("/data/user/0/com.bachatas4.android/files/vulkan-drivers/custom")

        val configuration = VulkanDriverConfiguration.resolve(
            RuntimeVulkanDriver.CUSTOM,
            runtimeRoot,
            customRoot,
        )

        assertEquals(Box64Mode.HOST_GLIBC, configuration.box64Mode)
        assertEquals(runtimeRoot.resolve("host/libvulkan.so.1").toString(), configuration.environment["SDL_VULKAN_LIBRARY"])
        assertEquals(customRoot.resolve("freedreno_icd.aarch64.json").toString(), configuration.environment["VK_ICD_FILENAMES"])
    }

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

    @Test
    fun selectsApkNativeBox64AndAdrenoToolsForTurnipR11() {
        val configuration = VulkanDriverConfiguration.resolve(
            RuntimeVulkanDriver.TURNIP_25_3_0_R11,
            runtimeRoot,
        )

        assertEquals(Box64Mode.APK_NATIVE, configuration.box64Mode)
        assertEquals("libvulkan.so.1", configuration.environment["SDL_VULKAN_LIBRARY"])
        assertEquals(
            runtimeRoot.resolve("drivers/turnip-25.3.0-r11").toString() + "/",
            configuration.environment["BACHATA_VULKAN_DRIVER_DIR"],
        )
        assertEquals("vulkan.ad07xx.so", configuration.environment["BACHATA_VULKAN_DRIVER_NAME"])
        assertEquals(runtimeRoot.resolve("tmp").toString(), configuration.environment["BACHATA_VULKAN_TMPDIR"])
        assertFalse(configuration.environment.containsKey("VK_ICD_FILENAMES"))
    }
}
