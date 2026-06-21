package com.bachatas4.android.runtime.device

import android.os.Build
import com.bachatas4.android.model.DeviceProfile

fun interface SocModelProvider {
    fun socModel(): String
}

fun interface GpuCapabilityProvider {
    fun capability(): GpuCapability
}

sealed interface GpuCapability {
    data object Unverified : GpuCapability

    data class Verified(val model: String) : GpuCapability
}

class DeviceCapabilityProbe(
    private val socModelProvider: SocModelProvider = AndroidSocModelProvider,
    private val gpuCapabilityProvider: GpuCapabilityProvider =
        GpuCapabilityProvider { GpuCapability.Unverified },
) {
    fun probe(): DeviceProfile {
        val soc = socModelProvider.socModel()
        return when (val gpu = gpuCapabilityProvider.capability()) {
            GpuCapability.Unverified -> DeviceProfile(
                soc = soc,
                gpu = UNVERIFIED_GPU,
                supported = false,
            )
            is GpuCapability.Verified -> classify(soc, gpu.model)
        }
    }
}

internal fun classify(soc: String, gpu: String): DeviceProfile = when {
    soc.equals("SM8650", ignoreCase = true) &&
        gpu.contains("Adreno 750", ignoreCase = true) -> DeviceProfile(soc, gpu, true)
    soc.equals("SM8750", ignoreCase = true) &&
        gpu.contains("Adreno 830", ignoreCase = true) -> DeviceProfile(soc, gpu, true)
    else -> DeviceProfile(soc, gpu, false)
}

private data object AndroidSocModelProvider : SocModelProvider {
    override fun socModel(): String = Build.SOC_MODEL
}

private const val UNVERIFIED_GPU = "unverified"
