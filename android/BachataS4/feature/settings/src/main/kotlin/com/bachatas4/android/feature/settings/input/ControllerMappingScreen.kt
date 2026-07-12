package com.bachatas4.android.feature.settings.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.designsystem.BachataPanel
import com.bachatas4.android.designsystem.BachataPrimaryButton
import com.bachatas4.android.designsystem.theme.BachataPalette
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
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            (0..3).forEach { slot ->
                item {
                    SubTab(
                        label = "Player ${slot + 1}",
                        selected = state.slot == slot,
                        onClick = { viewModel.selectSlot(slot) }
                    )
                }
            }
        }

        BachataPanel(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = BachataPalette.RaisedSurface
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Controller Mapping",
                    style = MaterialTheme.typography.titleMedium,
                    color = BachataPalette.Primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Device: ${profile.device?.fallbackName ?: "Any connected controller"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BachataPalette.Secondary
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                BachataPrimaryButton(onClick = { viewModel.autoMap() }) {
                    Text("Auto-Map")
                }
            }
            item {
                BachataPrimaryButton(onClick = viewModel::captureSequential) {
                    Text("Capture All")
                }
            }
            item {
                TextButton(onClick = viewModel::clear) {
                    Text("Clear Mappings")
                }
            }
        }

        BachataPanel(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = BachataPalette.Surface
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Vibration", color = BachataPalette.Primary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(profile.vibrationEnabled, viewModel::setVibration)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Motion Controls", color = BachataPalette.Primary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(profile.motionEnabled, viewModel::setMotion)
                }
            }
        }

        state.captureQueue.firstOrNull()?.let { target ->
            BachataPanel(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = Color(0xFF1E3A5F)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Press input for: $target",
                        color = Color(0xFFD2E8FF),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BachataPrimaryButton(onClick = { viewModel.accept(PhysicalBinding(PhysicalBindingKind.BUTTON, 96)) }) {
                            Text("Simulate Cross button")
                        }
                        BachataPrimaryButton(onClick = { viewModel.accept(PhysicalBinding(PhysicalBindingKind.AXIS, 0)) }) {
                            Text("Simulate Axis")
                        }
                    }
                }
            }
        }

        state.conflict?.let { conflict ->
            BachataPanel(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = Color(0xFF3A2B18)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Input already maps ${conflict.existing}. Replace?",
                        color = Color(0xFFFFDDB5),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BachataPrimaryButton(onClick = viewModel::replaceConflict) {
                            Text("Replace")
                        }
                        TextButton(onClick = viewModel::cancelConflict) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Button Assignments",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BachataPalette.Primary
                )
            }
            items(ControllerProfile.LOGICAL_CONTROLS.toList().sorted()) { control ->
                val binding = profile.bindings[control]
                BachataPanel(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    color = BachataPalette.Surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = control,
                                style = MaterialTheme.typography.bodyLarge,
                                color = BachataPalette.Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (binding != null) "${binding.kind.name} ${binding.code}" else "Unmapped",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (binding != null) BachataPalette.Accent else BachataPalette.Secondary
                            )
                        }
                        BachataPrimaryButton(
                            onClick = { viewModel.capture(control) }
                        ) {
                            Text("Remap")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun SubTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) BachataPalette.Primary else BachataPalette.Secondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(if (selected) BachataPalette.Accent else Color.Transparent)
        )
    }
}
