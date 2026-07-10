package com.bachatas4.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.runtime.settings.ProfileScope

@Composable
fun RawConfigScreen(
    scope: ProfileScope,
    onBack: () -> Unit,
    viewModel: RawConfigViewModel = hiltViewModel(),
) {
    LaunchedEffect(scope) { viewModel.load(scope) }
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Raw runtime configuration", style = MaterialTheme.typography.headlineMedium)
        Text("Changes stay as drafts until Validate and Save succeeds.")
        OutlinedTextField(
            state.shadPs4Json,
            viewModel::editShadPs4,
            label = { Text("shadPS4 JSON") },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        OutlinedTextField(
            state.box64Environment,
            viewModel::editBox64,
            label = { Text("Box64 BOX64_*=value") },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        state.validation?.let { Text(it, color = if (state.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = { viewModel.validate() }) { Text("Validate") }
            Button(onClick = viewModel::save, enabled = state.valid) { Text("Save") }
        }
    }
}
