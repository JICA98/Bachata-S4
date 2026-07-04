package com.bachatas4.android.runtime.session

import android.view.Surface
import com.bachatas4.android.model.RuntimeErrorCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RuntimeSurface(
    val surface: Surface,
    val width: Int,
    val height: Int,
)

sealed interface ManagedSessionState {
    data object Idle : ManagedSessionState
    data class Preparing(val stage: String) : ManagedSessionState
    data class Running(val gameId: String) : ManagedSessionState
    data class Failed(val code: RuntimeErrorCode, val detail: String) : ManagedSessionState
    data class Stopped(val exitCode: Int?) : ManagedSessionState
}

object ManagedSession {
    const val ACTION_START = "com.bachatas4.android.action.START_EMULATION"
    const val ACTION_STOP = "com.bachatas4.android.action.STOP_EMULATION"
    const val EXTRA_GAME_ID = "game_id"
    const val EXTRA_GAME_PATH = "game_path"
    const val EXTRA_VULKAN_DRIVER = "vulkan_driver"
    const val SERVICE_CLASS = "com.bachatas4.android.service.EmulationService"

    private val mutableSurface = MutableStateFlow<RuntimeSurface?>(null)
    private val mutableState = MutableStateFlow<ManagedSessionState>(ManagedSessionState.Idle)
    val surface: StateFlow<RuntimeSurface?> = mutableSurface
    val state: StateFlow<ManagedSessionState> = mutableState

    fun attachSurface(value: RuntimeSurface) { mutableSurface.value = value }
    fun detachSurface(surface: Surface) {
        if (mutableSurface.value?.surface === surface) mutableSurface.value = null
    }
    fun update(value: ManagedSessionState) { mutableState.value = value }
}
