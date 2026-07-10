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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bachatas4.android.data.RuntimeProfileStore
import com.bachatas4.android.feature.session.controller.TouchLayout
import com.bachatas4.android.feature.session.controller.TouchLayoutRenderer
import com.bachatas4.android.feature.session.controller.TouchLayoutRepository
import com.bachatas4.android.runtime.settings.ProfileScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TouchLayoutEditorUiState(
    val scope: ProfileScope = ProfileScope.Global,
    val layout: TouchLayout = TouchLayout(),
    val selected: String = "left_stick",
    val saved: Boolean = false,
)

@HiltViewModel
class TouchLayoutEditorViewModel @Inject constructor(
    private val profiles: RuntimeProfileStore,
    private val layouts: TouchLayoutRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TouchLayoutEditorUiState())
    val state: StateFlow<TouchLayoutEditorUiState> = mutableState

    fun load(scope: ProfileScope) {
        viewModelScope.launch {
            val id = profiles.load(scope).touchLayoutId
            val base = layouts.load(id)
            val editId = when (scope) { ProfileScope.Global -> "global"; is ProfileScope.Game -> "game-${scope.gameId}" }
            mutableState.value = TouchLayoutEditorUiState(scope, base.copy(id = editId, name = "Custom $editId"))
        }
    }
    fun select(control: String) { mutableState.value = mutableState.value.copy(selected = control) }
    fun move(dx: Float, dy: Float) {
        val p = mutableState.value.layout.controls.first { it.control == mutableState.value.selected }
        update(TouchLayoutRenderer.move(mutableState.value.layout, p.control, p.centerX + dx, p.centerY + dy))
    }
    fun resize(delta: Float) {
        val p = mutableState.value.layout.controls.first { it.control == mutableState.value.selected }
        update(TouchLayoutRenderer.resize(mutableState.value.layout, p.control, p.width + delta, p.height + delta))
    }
    fun visible(value: Boolean) { update(TouchLayoutRenderer.setVisible(mutableState.value.layout, mutableState.value.selected, value)) }
    fun front() { update(TouchLayoutRenderer.bringToFront(mutableState.value.layout, mutableState.value.selected)) }
    fun opacity(value: Float) { update(mutableState.value.layout.copy(opacity = value.coerceIn(0.05f, 1f))) }
    fun scale(value: Float) { update(mutableState.value.layout.copy(scale = value.coerceIn(0.5f, 2f))) }
    fun vibration(value: Boolean) { update(mutableState.value.layout.copy(vibrationEnabled = value)) }
    fun analogCentering(value: Boolean) { update(mutableState.value.layout.copy(analogCentering = value)) }
    fun reset() { update(TouchLayout(id = mutableState.value.layout.id, name = mutableState.value.layout.name)) }
    fun save() {
        layouts.save(mutableState.value.layout)
        viewModelScope.launch {
            profiles.update(mutableState.value.scope) { it.copy(touchLayoutId = mutableState.value.layout.id) }
            mutableState.value = mutableState.value.copy(saved = true)
        }
    }
    fun inherit() {
        viewModelScope.launch { profiles.update(mutableState.value.scope) { it.copy(touchLayoutId = null) } }
    }
    private fun update(layout: TouchLayout) { mutableState.value = mutableState.value.copy(layout = layout, saved = false) }
}

@Composable
fun TouchLayoutEditorScreen(
    scope: ProfileScope,
    onBack: () -> Unit,
    viewModel: TouchLayoutEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(scope) { viewModel.load(scope) }
    val state by viewModel.state.collectAsState()
    val selected = state.layout.controls.first { it.control == state.selected }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Touch layout", style = MaterialTheme.typography.headlineMedium)
        Row { Button(onClick = onBack) { Text("Back") }; Button(onClick = viewModel::save) { Text("Save") }; Button(onClick = viewModel::reset) { Text("Reset") }; Button(onClick = viewModel::inherit) { Text("Inherit") } }
        Text("Selected: ${selected.control} @ %.2f, %.2f".format(selected.centerX, selected.centerY))
        Row { Button(onClick = { viewModel.move(-0.02f, 0f) }) { Text("←") }; Button(onClick = { viewModel.move(0f, -0.02f) }) { Text("↑") }; Button(onClick = { viewModel.move(0f, 0.02f) }) { Text("↓") }; Button(onClick = { viewModel.move(0.02f, 0f) }) { Text("→") } }
        Row { Button(onClick = { viewModel.resize(-0.02f) }) { Text("Smaller") }; Button(onClick = { viewModel.resize(0.02f) }) { Text("Larger") }; Button(onClick = viewModel::front) { Text("Bring front") }; Switch(selected.visible, viewModel::visible) }
        Row { Text("Vibration"); Switch(state.layout.vibrationEnabled, viewModel::vibration); Text("Center sticks"); Switch(state.layout.analogCentering, viewModel::analogCentering) }
        Row { Button(onClick = { viewModel.opacity(state.layout.opacity - 0.1f) }) { Text("Opacity -") }; Button(onClick = { viewModel.opacity(state.layout.opacity + 0.1f) }) { Text("Opacity +") }; Button(onClick = { viewModel.scale(state.layout.scale - 0.1f) }) { Text("Scale -") }; Button(onClick = { viewModel.scale(state.layout.scale + 0.1f) }) { Text("Scale +") } }
        if (state.saved) Text("Saved")
        LazyColumn { items(state.layout.controls, key = { it.control }) { control -> Button(onClick = { viewModel.select(control.control) }) { Text("${control.control}${if (!control.visible) " (hidden)" else ""}") } } }
    }
}
