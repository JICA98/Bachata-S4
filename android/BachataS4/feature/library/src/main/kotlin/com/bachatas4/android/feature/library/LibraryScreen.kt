package com.bachatas4.android.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.data.ContentImportRequest
import com.bachatas4.android.data.ContentImporter
import com.bachatas4.android.data.GameRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onOpenSettings: () -> Unit,
    onLaunch: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dependencies = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, LibraryDependencies::class.java)
    }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dependencies) {
        dependencies.gameRepository().observeGames().collectLatest(viewModel::setGames)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val id = "GAME-${UUID.randomUUID()}"
                val title = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: id
                val result = dependencies.contentImporter().importGame(
                    ContentImportRequest(id = id, title = title, sourceUri = uri.toString()),
                )
                dependencies.gameRepository().addImportedGame(result, uri.toString(), System.currentTimeMillis())
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }
    val state by viewModel.state.collectAsState()
    LibraryContent(
        state = state,
        error = error,
        onOpenSettings = onOpenSettings,
        onImport = { picker.launch(arrayOf("application/octet-stream", "application/x-executable")) },
        onLaunch = onLaunch,
    )
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    error: String?,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onLaunch: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onOpenSettings) {
            Text("Settings")
        }
        Button(onClick = onImport) {
            Text("Import extracted eboot.bin")
        }
        error?.let { Text("Import failed: $it") }
        state.games.forEach { game ->
            Text("${game.title} (${game.id})")
            Button(onClick = { onLaunch(game.id) }) { Text("Launch") }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryDependencies {
    fun gameRepository(): GameRepository
    fun contentImporter(): ContentImporter
}
