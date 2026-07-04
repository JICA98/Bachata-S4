package com.bachatas4.android.feature.setup

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
fun SetupScreen(
    onContinue: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SetupContent(state = state, onContinue = onContinue)
}

@Composable
fun SetupContent(
    state: SetupUiState,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Setup", style = MaterialTheme.typography.headlineMedium)
        Text(state.legalNotice)
        Text("SoC: ${state.deviceProfile.soc}")
        Text("GPU: ${state.deviceProfile.gpu}")
        Text("Supported: ${state.deviceProfile.supported}")
        Text("Runtime installed: ${state.runtimeInstalled}")
        Text("Integrity verified: ${state.integrityVerified}")
        Button(enabled = state.canEnterLibrary, onClick = onContinue) {
            Text("Continue")
        }
    }
}
