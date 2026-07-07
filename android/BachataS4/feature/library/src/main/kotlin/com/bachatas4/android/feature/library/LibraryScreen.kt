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
import androidx.documentfile.provider.DocumentFile
import com.bachatas4.android.data.ContentImportRequest
import com.bachatas4.android.data.ContentImporter
import com.bachatas4.android.data.ContentTreeEntry
import com.bachatas4.android.data.GameRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var importing by remember { mutableStateOf(false) }
    LaunchedEffect(dependencies) {
        dependencies.gameRepository().observeGames().collectLatest(viewModel::setGames)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            error = null
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val (title, entries) = withContext(Dispatchers.IO) {
                    val root = requireNotNull(DocumentFile.fromTreeUri(context, uri)) {
                        "Cannot read selected folder"
                    }
                    (root.name?.ifBlank { null } ?: "Imported game") to root.toImportEntries()
                }
                val id = Regex("CUSA\\d{5}", RegexOption.IGNORE_CASE)
                    .find(title)?.value?.uppercase() ?: "GAME-${UUID.randomUUID()}"
                val result = dependencies.contentImporter().importGameTree(
                    ContentImportRequest(id = id, title = title, sourceUri = uri.toString()),
                    entries,
                )
                dependencies.gameRepository().addImportedGame(result, uri.toString(), System.currentTimeMillis())
            } catch (failure: Throwable) {
                error = failure.message ?: failure.javaClass.simpleName
            } finally {
                importing = false
            }
        }
    }
    val state by viewModel.state.collectAsState()
    LibraryContent(
        state = state,
        error = error,
        importing = importing,
        onOpenSettings = onOpenSettings,
        onImport = { picker.launch(null) },
        onLaunch = onLaunch,
    )
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    error: String?,
    importing: Boolean,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onLaunch: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onOpenSettings) {
            Text("Settings")
        }
        Button(onClick = onImport, enabled = !importing) {
            Text("Import extracted game folder")
        }
        if (importing) Text("Scanning and importing game files...")
        error?.let { Text("Import failed: $it") }
        state.games.forEach { game ->
            Text("${game.title} (${game.id})")
            Button(onClick = { onLaunch(game.id) }) { Text("Launch") }
        }
    }
}

private fun DocumentFile.toImportEntries(): List<ContentTreeEntry> {
    val entries = mutableListOf<ContentTreeEntry>()
    val pending = ArrayDeque<Pair<DocumentFile, String>>()
    pending.add(this to "")
    while (pending.isNotEmpty()) {
        val (directory, prefix) = pending.removeLast()
        directory.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val relativePath = if (prefix.isEmpty()) name else "$prefix/$name"
            when {
                child.isDirectory -> pending.add(child to relativePath)
                child.isFile -> entries.add(
                    ContentTreeEntry(relativePath, child.uri.toString(), child.length().coerceAtLeast(0)),
                )
            }
        }
    }
    return entries
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryDependencies {
    fun gameRepository(): GameRepository
    fun contentImporter(): ContentImporter
}
