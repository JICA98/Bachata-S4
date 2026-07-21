package com.bachatas4.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.bachatas4.android.MainActivity
import com.bachatas4.android.data.ContentImportRequest
import com.bachatas4.android.data.ContentImporter
import com.bachatas4.android.data.ContentTreeEntry
import com.bachatas4.android.data.GameMetadataResolver
import com.bachatas4.android.data.GameRepository
import com.bachatas4.android.data.ImportManager
import com.bachatas4.android.data.ImportProgress
import com.bachatas4.android.data.ParamSfoReader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Copies a user-selected game tree into app storage.
 *
 * Runs as a normal (non-foreground) service so the app does not need
 * FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE. Progress still updates
 * [ImportManager] for in-app UI; optional status-bar notifications use
 * [NotificationManager] only (no startForeground).
 *
 * Trade-off: if the user backgrounds the app during a long import, the system
 * may kill this service more readily than a foreground service would.
 */
@AndroidEntryPoint
class ImportService : Service() {
    @Inject lateinit var contentImporter: ContentImporter
    @Inject lateinit var gameRepository: GameRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var importJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ImportManager.ACTION_CANCEL -> {
                importJob?.cancel()
                ImportManager.reset()
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                stopSelf()
                return START_NOT_STICKY
            }
            ImportManager.ACTION_IMPORT -> {
                val uriString = intent.getStringExtra(ImportManager.EXTRA_URI) ?: run {
                    ImportManager.update(ImportProgress.Failed("Missing source URI"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (importJob?.isActive == true) {
                    return START_NOT_STICKY
                }
                importJob = scope.launch { runImport(uriString) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        importJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runImport(uriString: String) {
        try {
            updateNotification("Preparing import…", 0, 0, true)

            val uri = Uri.parse(uriString)
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val (folderName, entries) = withContext(Dispatchers.IO) {
                val root = requireNotNull(
                    DocumentFile.fromTreeUri(this@ImportService, uri),
                ) { "Cannot read selected folder" }
                (root.name?.ifBlank { null } ?: "Imported game") to root.toImportEntries()
            }

            ImportManager.update(ImportProgress.Scanning(folderName))
            updateNotification("Identifying $folderName…", 0, 0, true)

            val sfoEntry = entries.firstOrNull { it.relativePath == "sce_sys/param.sfo" }
            val sfoBytes = sfoEntry?.let { entry ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openInputStream(Uri.parse(entry.sourceUri))
                            ?.use { it.readBytes() }
                    }.getOrNull()
                }
            }
            val sfo = sfoBytes?.let { ParamSfoReader.parse(it) }
            val resolved = GameMetadataResolver.resolve(folderName = folderName, sfo = sfo)

            val result = contentImporter.importGameTree(
                ContentImportRequest(
                    id = resolved.id,
                    title = resolved.title,
                    sourceUri = uriString,
                    subtitle = resolved.subtitle,
                    detail = resolved.detail,
                ),
                entries,
                onProgress = { bytesCopied, totalBytes, currentFile ->
                    ImportManager.update(
                        ImportProgress.Copying(
                            bytesCopied = bytesCopied,
                            totalBytes = totalBytes,
                            currentFile = currentFile,
                            gameTitle = resolved.title,
                        ),
                    )
                    updateNotification(
                        "Importing ${resolved.title}",
                        totalBytes.toInt(),
                        bytesCopied.toInt(),
                        true,
                    )
                },
            )

            gameRepository.addImportedGame(result, uriString, System.currentTimeMillis())
            ImportManager.update(ImportProgress.Success(resolved.id, resolved.title))

            val doneNotification = buildNotification(
                "${resolved.title} imported",
                0,
                0,
                false,
            )
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, doneNotification)
        } catch (failure: Throwable) {
            if (failure is CancellationException) {
                ImportManager.reset()
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            } else {
                val message = failure.message ?: failure.javaClass.simpleName
                ImportManager.update(ImportProgress.Failed(message))
                val errorNotification = buildNotification(
                    "Import failed: $message",
                    0,
                    0,
                    false,
                )
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, errorNotification)
            }
        } finally {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Game Imports", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(
        text: String,
        maxProgress: Int,
        progress: Int,
        ongoing: Boolean,
    ): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(ImportManager.ACTION_CANCEL).setClassName(packageName, ImportManager.SERVICE_CLASS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Bachata S4")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(ongoing)
        if (ongoing) {
            builder.addAction(0, "Cancel", cancel)
            if (maxProgress > 0) {
                builder.setProgress(maxProgress, progress, false)
            } else {
                builder.setProgress(0, 0, true)
            }
        }
        return builder.build()
    }

    private fun updateNotification(
        text: String,
        maxProgress: Int,
        progress: Int,
        ongoing: Boolean,
    ) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, maxProgress, progress, ongoing))
    }

    private companion object {
        const val CHANNEL_ID = "import"
        const val NOTIFICATION_ID = 42
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
                    ContentTreeEntry(relativePath, child.uri.toString(), child.length().coerceAtLeast(0L)),
                )
            }
        }
    }
    return entries
}
