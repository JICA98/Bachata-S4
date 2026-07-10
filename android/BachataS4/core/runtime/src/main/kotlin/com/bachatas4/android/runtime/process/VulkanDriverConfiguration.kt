package com.bachatas4.android.runtime.process

import com.bachatas4.android.runtime.driver.DriverAbi
import com.bachatas4.android.runtime.driver.InstalledDriver
import java.nio.file.Path

enum class RuntimeVulkanDriver {
    SYSTEM,
    CUSTOM,
    TURNIP_25_0_0,
    TURNIP_25_3_0_R11,
    TURNIP_26_1_0,
}

object RuntimeVulkanDriverPreference {
    const val FILE_NAME = "emulator_settings"
    const val KEY = "vulkan_driver"
    val DEFAULT = RuntimeVulkanDriver.SYSTEM

    fun decode(value: String?): RuntimeVulkanDriver =
        if (value == RuntimeVulkanDriver.SYSTEM.name) RuntimeVulkanDriver.SYSTEM else DEFAULT
}

data class VulkanDriverConfiguration(
    val box64Mode: Box64Mode,
    val environment: Map<String, String>,
) {
    companion object {
        fun resolve(driver: InstalledDriver, runtimeRoot: Path): VulkanDriverConfiguration =
            when (driver.metadata.abi) {
                DriverAbi.LINUX_GLIBC -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.HOST_GLIBC,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to runtimeRoot.resolve("host/libvulkan.so.1").toString(),
                        "VK_ICD_FILENAMES" to requireNotNull(driver.icdManifest).toString(),
                    ),
                )
                DriverAbi.ANDROID_BIONIC -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.APK_NATIVE,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to "libvulkan.so.1",
                        "BACHATA_VULKAN_DRIVER_DIR" to driver.root.toString() + "/",
                        "BACHATA_VULKAN_DRIVER_NAME" to driver.library.fileName.toString(),
                        "BACHATA_VULKAN_TMPDIR" to runtimeRoot.resolve("tmp").toString(),
                    ),
                )
            }

        fun resolve(driver: RuntimeVulkanDriver, runtimeRoot: Path, customDriverRoot: Path? = null): VulkanDriverConfiguration =
            when (driver) {
                RuntimeVulkanDriver.SYSTEM -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.APK_NATIVE,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to "libvulkan.so.1",
                    ),
                )
                RuntimeVulkanDriver.CUSTOM -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.HOST_GLIBC,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to runtimeRoot.resolve("host/libvulkan.so.1").toString(),
                        "VK_ICD_FILENAMES" to requireNotNull(customDriverRoot) {
                            "Custom Vulkan driver is not installed"
                        }.resolve("freedreno_icd.aarch64.json").toString(),
                    ),
                )
                RuntimeVulkanDriver.TURNIP_25_0_0 -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.HOST_GLIBC,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to runtimeRoot.resolve("host/libvulkan.so.1").toString(),
                        "VK_ICD_FILENAMES" to runtimeRoot.resolve("host/vulkan/icd.d/turnip-25.0.0.json").toString(),
                    ),
                )
                RuntimeVulkanDriver.TURNIP_25_3_0_R11 -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.APK_NATIVE,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to "libvulkan.so.1",
                        "BACHATA_VULKAN_DRIVER_DIR" to
                            runtimeRoot.resolve("drivers/turnip-25.3.0-r11").toString() + "/",
                        "BACHATA_VULKAN_DRIVER_NAME" to "vulkan.ad07xx.so",
                        "BACHATA_VULKAN_TMPDIR" to runtimeRoot.resolve("tmp").toString(),
                    ),
                )
                RuntimeVulkanDriver.TURNIP_26_1_0 -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.HOST_GLIBC,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to runtimeRoot.resolve("host/libvulkan.so.1").toString(),
                        "VK_ICD_FILENAMES" to runtimeRoot.resolve("host/vulkan/icd.d/turnip-26.1.0.json").toString(),
                    ),
                )
            }
    }
}
