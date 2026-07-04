package com.bachatas4.android.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettingsUiState(
    val runtimeRevision: String = "runtime-dev",
    val vulkanUuid: String = "unknown",
    val box64Preset: String = "Stability",
    val gameIds: List<String> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState

    fun setDiagnostics(
        runtimeRevision: String,
        vulkanUuid: String,
        gameIds: List<String>,
    ) {
        mutableState.value = mutableState.value.copy(
            runtimeRevision = runtimeRevision,
            vulkanUuid = vulkanUuid,
            gameIds = gameIds.sorted(),
        )
    }

    fun diagnosticExport(state: SettingsUiState = this.state.value): String =
        buildString {
            appendLine("runtimeRevision=${state.runtimeRevision}")
            appendLine("vulkanUuid=${state.vulkanUuid}")
            appendLine("box64Preset=${state.box64Preset}")
            appendLine("gameIds=${state.gameIds.joinToString(",")}")
        }
}
