package com.bachatas4.android.feature.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.data.GameIconPaths
import com.bachatas4.android.data.GameRepository
import com.bachatas4.android.data.ImportManager
import com.bachatas4.android.data.ImportProgress
import com.bachatas4.android.designsystem.BachataActionBar
import com.bachatas4.android.designsystem.BachataPanel
import com.bachatas4.android.designsystem.BachataPrimaryButton
import com.bachatas4.android.designsystem.theme.BachataPalette
import com.bachatas4.android.runtime.input.GamepadInputManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    val importProgress by ImportManager.progress.collectAsState()
    var gameToDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dependencies) {
        runCatching { dependencies.gameRepository().syncOrphanedFolders() }
        runCatching { dependencies.gameRepository().backfillTitlesFromSfo() }
        dependencies.gameRepository().observeGames().collectLatest(viewModel::setGames)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* nothing, just needed for import service foreground notification */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    LaunchedEffect(dependencies) {
        runCatching { dependencies.gameRepository().syncOrphanedFolders() }
        runCatching { dependencies.gameRepository().backfillTitlesFromSfo() }
        dependencies.gameRepository().observeGames().collectLatest(viewModel::setGames)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val intent = Intent(ImportManager.ACTION_IMPORT).apply {
            setClassName(context.packageName, ImportManager.SERVICE_CLASS)
            putExtra(ImportManager.EXTRA_URI, uri.toString())
        }
        context.startService(intent)
    }
    val onLaunchWithTracking: (String) -> Unit = { id ->
        scope.launch {
            runCatching { dependencies.gameRepository().updateLastLaunched(id) }
            onLaunch(id)
        }
    }
    DisposableEffect(viewModel) {
        viewModel.attachNavListener()
        onDispose { GamepadInputManager.unregisterNavListener() }
    }
    LaunchedEffect(viewModel) {
        viewModel.launch.collect { id ->
            runCatching { dependencies.gameRepository().updateLastLaunched(id) }
            onLaunch(id)
        }
    }
    val state by viewModel.state.collectAsState()
    LibraryContent(
        state = state,
        importProgress = importProgress,
        gameToDelete = gameToDelete,
        onOpenSettings = onOpenSettings,
        onOpenGameSettings = onOpenGameSettings,
        onSelectGame = viewModel::selectGame,
        onImport = { picker.launch(null) },
        onLaunch = onLaunchWithTracking,
        onRequestDelete = { gameToDelete = it },
        onConfirmDelete = { id ->
            scope.launch {
                runCatching { dependencies.gameRepository().deleteGame(id) }
                gameToDelete = null
            }
        },
        onDismissDelete = { gameToDelete = null },
    )
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    importProgress: ImportProgress,
    gameToDelete: String?,
    onOpenSettings: () -> Unit,
    onOpenGameSettings: (String) -> Unit,
    onSelectGame: (String) -> Unit,
    onImport: () -> Unit,
    onLaunch: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
    onDismissDelete: () -> Unit,
) {
    val selected = state.games.firstOrNull { it.id == state.selectedGameId } ?: state.games.firstOrNull()
    val context = LocalContext.current
    val isImporting = importProgress !is ImportProgress.Idle && importProgress !is ImportProgress.Failed
    val selectedCoverBitmap = remember(selected?.relativePath) {
        if (selected == null) null else {
            val file = GameIconPaths.icon0(context.filesDir, selected.relativePath)
            if (!file.isFile) null else {
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            }
        }
    }
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
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedCoverBitmap != null) {
                Image(
                    bitmap = selectedCoverBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        BachataPalette.Canvas
                                    ),
                                    startY = size.height * 0.30f,
                                    endY = size.height * 0.65f
                                )
                            )
                            drawRect(
                                color = BachataPalette.Canvas,
                                topLeft = Offset(0f, size.height * 0.65f),
                                size = Size(size.width, size.height * 0.35f)
                            )
                        },
                    alpha = 0.22f,
                    contentScale = ContentScale.Crop,
                )
            }
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
                        text = "Library",
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
                    GlassMorphicPanel(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("No games yet", style = MaterialTheme.typography.headlineSmall, color = BachataPalette.Primary)
                            Text(
                                "Import a legally owned game folder to start your library.",
                                color = BachataPalette.Secondary,
                            )
                            BachataPrimaryButton(onClick = onImport, enabled = !isImporting) {
                                Text(if (isImporting) "Scanning…" else "Import game")
                            }
                        }
                    }
                } else {
                    GlassMorphicPanel(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        selected = true,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            GameCover(
                                relativePath = selected.relativePath,
                                modifier = Modifier.width(120.dp).aspectRatio(0.75f),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("SELECTED GAME", style = MaterialTheme.typography.labelMedium, color = BachataPalette.Accent)
                                Text(
                                    selected.title,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BachataPalette.Primary,
                                )
                                val subtitle = selected.subtitle
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = BachataPalette.Primary.copy(alpha = 0.8f),
                                    )
                                }
                                val detail = selected.detail
                                if (!detail.isNullOrBlank()) {
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = BachataPalette.Secondary,
                                    )
                                }
                                Text("Ready to play", color = BachataPalette.Secondary, style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BachataPrimaryButton(onClick = { onLaunch(selected.id) }) { Text("▶  Resume") }
                                    TextButton(onClick = { onOpenGameSettings(selected.id) }) { Text("Options") }
                                    TextButton(onClick = { onRequestDelete(selected.id) }) {
                                        Text("Remove", color = Color(0xFFFFB4AB))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            when (val progress = importProgress) {
                is ImportProgress.Scanning -> {
                    item {
                        Text(
                            "Identifying ${progress.folderName}…",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = BachataPalette.Secondary,
                        )
                    }
                }
                is ImportProgress.Copying -> {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Importing ${progress.gameTitle}",
                                color = BachataPalette.Primary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val fraction = if (progress.totalBytes > 0) {
                                progress.bytesCopied.toFloat() / progress.totalBytes.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { fraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = BachataPalette.Accent,
                            )
                            Text(
                                "${formatBytes(progress.bytesCopied)} / ${formatBytes(progress.totalBytes)}",
                                color = BachataPalette.Secondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                progress.currentFile,
                                color = BachataPalette.Secondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                is ImportProgress.Success -> {
                    item {
                        Text(
                            "${progress.title} imported",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = BachataPalette.Accent,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                is ImportProgress.Failed -> {
                    item {
                        Surface(
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                            color = Color(0xFF3A1E21).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB4AB).copy(alpha = 0.3f)),
                        ) {
                            Text(
                                "Import failed: ${progress.message}",
                                modifier = Modifier.padding(16.dp),
                                color = Color(0xFFFFB4AB),
                            )
                        }
                    }
                }
                is ImportProgress.Idle -> { /* nothing */ }
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
                    val continuePlayingGames = remember(state.games) {
                        state.games.sortedByDescending { it.lastLaunchedAtMs }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continuePlayingGames.take(5), key = { it.id }) { game ->
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
                        TextButton(onClick = onImport, enabled = !isImporting) { Text("Import") }
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
    gameToDelete?.let { id ->
        val game = state.games.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Remove game") },
            text = { Text("Delete \"${game?.title ?: id}\" and all of its files? This cannot be undone.") },
            confirmButton = {
                Button(onClick = { onConfirmDelete(id) }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text("Cancel")
                }
            },
        )
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
    GlassMorphicPanel(
        modifier = Modifier.width(148.dp).clickable { onSelectGame(game.id) },
        selected = selected,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameCover(
                relativePath = game.relativePath,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
            )
            Text(game.title, maxLines = 2, color = BachataPalette.Primary, fontWeight = FontWeight.SemiBold)
            val subtitle = game.subtitle
            if (!subtitle.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BachataPalette.Accent.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = BachataPalette.Accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onLaunch(game.id) }) { Text("Launch") }
                TextButton(onClick = { onOpenGameSettings(game.id) }) { Text("⋮") }
            }
        }
    }
}

@Composable
private fun GlassMorphicPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = if (selected) BachataPalette.RaisedSurface.copy(alpha = 0.65f) else BachataPalette.Surface.copy(alpha = 0.40f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = if (selected) 0.30f else 0.12f)
        ),
        content = content,
    )
}

@Composable
private fun GameCover(
    relativePath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(relativePath) {
        val file = GameIconPaths.icon0(context.filesDir, relativePath)
        if (!file.isFile) {
            null
        } else {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BachataPalette.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("GAME", color = BachataPalette.Secondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return "%.1f KB".format(kib)
    val mib = kib / 1024.0
    if (mib < 1024) return "%.1f MB".format(mib)
    return "%.2f GB".format(mib / 1024.0)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryDependencies {
    fun gameRepository(): GameRepository
}
