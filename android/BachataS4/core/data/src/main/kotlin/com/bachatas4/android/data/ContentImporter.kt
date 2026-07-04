package com.bachatas4.android.data

import android.content.ContentResolver
import android.net.Uri
import com.bachatas4.android.model.Game
import com.bachatas4.android.model.RuntimeErrorCode
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ContentImportRequest(
    val id: String,
    val title: String,
    val sourceUri: String,
    val expectedBytes: Long? = null,
    val expectedSha256: String? = null,
)

data class ContentImportResult(
    val game: Game,
    val bytesCopied: Long,
    val sha256: String,
)

data class ContentTreeEntry(
    val relativePath: String,
    val sourceUri: String,
)

class ContentImportException(
    val code: RuntimeErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

fun interface ImportSource {
    fun open(uri: String): InputStream
}

class ContentResolverImportSource(
    private val contentResolver: ContentResolver,
) : ImportSource {
    override fun open(uri: String): InputStream =
        contentResolver.openInputStream(Uri.parse(uri))
            ?: throw ContentImportException(RuntimeErrorCode.CONTENT_PERMISSION_LOST, "Cannot open $uri")
}

class ContentImporter(
    private val filesDir: File,
    private val source: ImportSource,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun importGameTree(
        request: ContentImportRequest,
        entries: List<ContentTreeEntry>,
    ): ContentImportResult = withContext(Dispatchers.IO) {
        validateGameId(request.id)
        val gamesDir = File(filesDir, "games").canonicalFile
        gamesDir.mkdirs()
        val destination = File(gamesDir, request.id).canonicalFile
        requireInside(gamesDir, destination)
        if (destination.exists()) {
            throw ContentImportException(RuntimeErrorCode.CONTENT_INVALID, "Game already imported")
        }
        if (entries.none { it.relativePath == "eboot.bin" }) {
            throw ContentImportException(RuntimeErrorCode.CONTENT_INVALID, "Selected folder has no eboot.bin")
        }

        val staging = File(gamesDir, ".import-${idFactory()}").canonicalFile
        requireInside(gamesDir, staging)
        var completed = false
        try {
            staging.deleteRecursively()
            staging.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesCopied = 0L
            entries.sortedBy(ContentTreeEntry::relativePath).forEach { entry ->
                val payload = File(staging, entry.relativePath).canonicalFile
                requireInside(staging, payload)
                payload.parentFile?.mkdirs()
                bytesCopied += copyUri(entry.sourceUri, payload, digest)
            }
            validateBytes(request, bytesCopied)
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            validateDigest(request, sha256)
            moveAtomically(staging, destination)
            completed = true
            ContentImportResult(
                Game(request.id, request.title, "games/${request.id}"),
                bytesCopied,
                sha256,
            )
        } catch (security: SecurityException) {
            throw ContentImportException(
                RuntimeErrorCode.CONTENT_PERMISSION_LOST,
                "Source permission lost",
                security,
            )
        } finally {
            if (!completed) staging.deleteRecursively()
        }
    }

    suspend fun importGame(request: ContentImportRequest): ContentImportResult =
        withContext(Dispatchers.IO) {
            validateGameId(request.id)
            val gamesDir = File(filesDir, "games").canonicalFile
            gamesDir.mkdirs()
            val destination = File(gamesDir, request.id).canonicalFile
            requireInside(gamesDir, destination)
            if (destination.exists()) {
                throw ContentImportException(RuntimeErrorCode.CONTENT_INVALID, "Game already imported")
            }

            val staging = File(gamesDir, ".import-${idFactory()}").canonicalFile
            requireInside(gamesDir, staging)
            var completed = false
            try {
                if (staging.exists()) {
                    staging.deleteRecursively()
                }
                staging.mkdirs()
                val payload = File(staging, "eboot.bin")
                val digest = MessageDigest.getInstance("SHA-256")
                val bytesCopied = copySource(request, payload, digest)
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                validateBytes(request, bytesCopied)
                validateDigest(request, sha256)
                moveAtomically(staging, destination)
                completed = true
                ContentImportResult(
                    game = Game(
                        id = request.id,
                        title = request.title,
                        relativePath = "games/${request.id}",
                    ),
                    bytesCopied = bytesCopied,
                    sha256 = sha256,
                )
            } catch (security: SecurityException) {
                throw ContentImportException(
                    RuntimeErrorCode.CONTENT_PERMISSION_LOST,
                    "Source permission lost",
                    security,
                )
            } finally {
                if (!completed) {
                    staging.deleteRecursively()
                }
            }
        }

    private suspend fun copySource(
        request: ContentImportRequest,
        payload: File,
        digest: MessageDigest,
    ): Long {
        return copyUri(request.sourceUri, payload, digest)
    }

    private suspend fun copyUri(
        sourceUri: String,
        payload: File,
        digest: MessageDigest,
    ): Long {
        var total = 0L
        source.open(sourceUri).use { input ->
            FileOutputStream(payload).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    total += read
                }
                output.fd.sync()
            }
        }
        return total
    }

    private fun validateBytes(
        request: ContentImportRequest,
        actualBytes: Long,
    ) {
        if (request.expectedBytes != null && request.expectedBytes != actualBytes) {
            throw ContentImportException(
                RuntimeErrorCode.CONTENT_INVALID,
                "Byte count mismatch: expected ${request.expectedBytes}, got $actualBytes",
            )
        }
    }

    private fun validateDigest(
        request: ContentImportRequest,
        actualSha256: String,
    ) {
        if (request.expectedSha256 != null &&
            !request.expectedSha256.equals(actualSha256, ignoreCase = true)
        ) {
            throw ContentImportException(RuntimeErrorCode.CONTENT_INVALID, "SHA-256 mismatch")
        }
    }

    private fun moveAtomically(
        staging: File,
        destination: File,
    ) {
        try {
            Files.move(staging.toPath(), destination.toPath(), ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            throw ContentImportException(RuntimeErrorCode.CONTENT_INVALID, "Atomic rename unavailable", e)
        }
    }

    private fun validateGameId(id: String) {
        if (!id.matches(Regex("[A-Za-z0-9._-]+"))) {
            throw ContentImportException(RuntimeErrorCode.CONTENT_INVALID, "Invalid game id")
        }
    }

    private fun requireInside(
        root: File,
        child: File,
    ) {
        val rootPath = root.toPath()
        val childPath = child.toPath()
        if (!childPath.startsWith(rootPath)) {
            throw ContentImportException(RuntimeErrorCode.CONTENT_INVALID, "Import path escapes games dir")
        }
    }
}
