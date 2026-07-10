package com.bachatas4.android.feature.settings.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.runtime.input.ControllerProfile
import com.bachatas4.android.runtime.input.PhysicalBinding
import com.bachatas4.android.runtime.input.PhysicalBindingKind
import com.bachatas4.android.runtime.settings.ProfileScope

@Composable
fun ControllerMappingScreen(
    scope: ProfileScope,
    onBack: () -> Unit,
    viewModel: ControllerMappingViewModel = hiltViewModel(),
) {
    LaunchedEffect(scope) { viewModel.load(scope) }
    val state by viewModel.state.collectAsState()
    val profile = state.profiles[state.slot]
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Controller mappings", style = MaterialTheme.typography.headlineMedium)
        Row { Button(onClick = onBack) { Text("Back") }; (0..3).forEach { slot -> Button(onClick = { viewModel.selectSlot(slot) }) { Text("P${slot + 1}") } } }
        Text("Device: ${profile.device?.fallbackName ?: "Any controller"}")
        Row { Button(onClick = { viewModel.autoMap() }) { Text("Auto-map") }; Button(onClick = viewModel::clear) { Text("Clear") }; Button(onClick = viewModel::captureSequential) { Text("Capture all") } }
        Row { Text("Vibration"); Switch(profile.vibrationEnabled, viewModel::setVibration); Text("Motion"); Switch(profile.motionEnabled, viewModel::setMotion) }
        state.captureQueue.firstOrNull()?.let { target ->
            Text("Press input for $target")
            Row {
                Button(onClick = { viewModel.accept(PhysicalBinding(PhysicalBindingKind.BUTTON, 96)) }) { Text("Capture Cross sample") }
                Button(onClick = { viewModel.accept(PhysicalBinding(PhysicalBindingKind.AXIS, 0)) }) { Text("Capture Axis sample") }
            }
        }
        state.conflict?.let { conflict ->
            Text("Input already maps ${conflict.existing}. Replace?")
            Row { Button(onClick = viewModel::replaceConflict) { Text("Replace") }; Button(onClick = viewModel::cancelConflict) { Text("Cancel") } }
        }
        LazyColumn {
            items(ControllerProfile.LOGICAL_CONTROLS.toList().sorted()) { control ->
                val binding = profile.bindings[control]
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$control: ${binding?.kind ?: "unmapped"} ${binding?.code ?: ""}")
                    Button(onClick = { viewModel.capture(control) }) { Text("Remap") }
                }
            }
        }
    }
}
