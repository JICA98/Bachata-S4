package com.bachatas4.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SettingsContent(state = state, onBack = onBack)
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Runtime revision: ${state.runtimeRevision}")
        Text("Vulkan UUID: ${state.vulkanUuid}")
        Text("Box64 preset: ${state.box64Preset}")
        Text("Driver profile: device default")
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
