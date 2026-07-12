package com.bachatas4.android.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.bachatas4.android.designsystem.BachataActionBar
import com.bachatas4.android.designsystem.BachataPanel
import com.bachatas4.android.designsystem.BachataPrimaryButton
import com.bachatas4.android.designsystem.BachataScreenHeader
import com.bachatas4.android.designsystem.theme.BachataPalette

@Composable
fun LibraryScreen(
    onOpenSettings: () -> Unit,
    onOpenGameSettings: (String) -> Unit = {},
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
        onOpenGameSettings = onOpenGameSettings,
        onSelectGame = viewModel::selectGame,
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
    onOpenGameSettings: (String) -> Unit,
    onSelectGame: (String) -> Unit,
    onImport: () -> Unit,
    onLaunch: (String) -> Unit,
) {
    val selected = state.games.firstOrNull { it.id == state.selectedGameId } ?: state.games.firstOrNull()
    Scaffold(
        containerColor = BachataPalette.Canvas,
        bottomBar = {
            BachataActionBar(
                "A  LAUNCH",
                "X  FAVORITE",
                "B  BACK",
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.size(32.dp),
                        factory = { viewContext ->
                            android.widget.ImageView(viewContext).apply {
                                setImageResource(viewContext.applicationInfo.icon)
                                contentDescription = "Bachata S4 logo"
                            }
                        }
                    )
                    Text(
                        text = "BACHATA S4",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = BachataPalette.Primary,
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("⚙", color = BachataPalette.Primary, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            item {
                if (selected == null) {
                    BachataPanel(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("No games yet", style = MaterialTheme.typography.headlineSmall, color = BachataPalette.Primary)
                            Text(
                                "Import a legally owned game folder to start your library.",
                                color = BachataPalette.Secondary,
                            )
                            BachataPrimaryButton(onClick = onImport, enabled = !importing) {
                                Text(if (importing) "Scanning…" else "Import game")
                            }
                        }
                    }
                } else {
                    BachataPanel(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        color = BachataPalette.RaisedSurface,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("SELECTED GAME", style = MaterialTheme.typography.labelMedium, color = BachataPalette.Accent)
                            Text(
                                selected.title,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = BachataPalette.Primary,
                            )
                            Text("${selected.id}  •  Ready to play", color = BachataPalette.Secondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BachataPrimaryButton(onClick = { onLaunch(selected.id) }) { Text("▶  Resume") }
                                TextButton(onClick = { onOpenGameSettings(selected.id) }) { Text("Options") }
                            }
                        }
                    }
                }
            }
            if (importing) {
                item {
                    Text(
                        "Scanning and importing game files…",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = BachataPalette.Secondary,
                    )
                }
            }
            error?.let { message ->
                item {
                    BachataPanel(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        color = Color(0xFF3A1E21),
                    ) {
                        Text("Import failed: $message", modifier = Modifier.padding(16.dp), color = Color(0xFFFFB4AB))
                    }
                }
            }
            if (state.games.isNotEmpty()) {
                item {
                    Text(
                        "Continue Playing",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = BachataPalette.Primary,
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.games.take(5), key = { it.id }) { game ->
                            LibraryGameCard(game, game.id == selected?.id, onSelectGame, onLaunch, onOpenGameSettings)
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "All Games",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = BachataPalette.Primary,
                        )
                        TextButton(onClick = onImport, enabled = !importing) { Text("Import") }
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.games, key = { it.id }) { game ->
                            LibraryGameCard(game, game.id == selected?.id, onSelectGame, onLaunch, onOpenGameSettings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGameCard(
    game: com.bachatas4.android.model.Game,
    selected: Boolean,
    onSelectGame: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onOpenGameSettings: (String) -> Unit,
) {
    BachataPanel(
        modifier = Modifier.width(148.dp).clickable { onSelectGame(game.id) },
        color = if (selected) BachataPalette.RaisedSurface else BachataPalette.Surface,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)
                    .clip(RoundedCornerShape(8.dp)).background(BachataPalette.Canvas),
                contentAlignment = Alignment.Center,
            ) {
                Text("GAME", color = BachataPalette.Secondary, style = MaterialTheme.typography.labelSmall)
            }
            Text(game.title, maxLines = 2, color = BachataPalette.Primary, fontWeight = FontWeight.SemiBold)
            Text(game.id, maxLines = 1, color = BachataPalette.Secondary, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onLaunch(game.id) }) { Text("Launch") }
                TextButton(onClick = { onOpenGameSettings(game.id) }) { Text("⋮") }
            }
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
