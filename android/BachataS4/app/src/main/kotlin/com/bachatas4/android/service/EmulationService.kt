package com.bachatas4.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bachatas4.android.MainActivity
import com.bachatas4.android.model.RuntimeErrorCode
import com.bachatas4.android.runtime.display.WinlatorEmbeddedXServer
import com.bachatas4.android.runtime.install.RuntimeInstaller
import com.bachatas4.android.runtime.install.RuntimeManifest
import com.bachatas4.android.runtime.process.RuntimeProcessHandle
import com.bachatas4.android.runtime.process.RuntimeProcessLauncher
import com.bachatas4.android.runtime.process.RuntimeProcessRequest
import com.bachatas4.android.runtime.process.RuntimeVulkanDriver
import com.bachatas4.android.runtime.process.VulkanDriverConfiguration
import com.bachatas4.android.runtime.session.ManagedSession
import com.bachatas4.android.runtime.session.ManagedSessionState
import com.winlator.xconnector.UnixSocketConfig
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

class EmulationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    @Volatile private var process: RuntimeProcessHandle? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ManagedSession.ACTION_STOP -> stopSession()
            ManagedSession.ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                if (sessionJob?.isActive == true) {
                    return START_NOT_STICKY
                } else {
                    val gameId = intent.getStringExtra(ManagedSession.EXTRA_GAME_ID).orEmpty()
                    val gamePath = intent.getStringExtra(ManagedSession.EXTRA_GAME_PATH).orEmpty()
                    val driverName = intent.getStringExtra(ManagedSession.EXTRA_VULKAN_DRIVER)
                        ?: RuntimeVulkanDriver.TURNIP_26_1_0.name
                    sessionJob = scope.launch { runSession(gameId, gamePath, RuntimeVulkanDriver.valueOf(driverName)) }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopSession()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runSession(gameId: String, relativePath: String, vulkanDriver: RuntimeVulkanDriver) {
        var xServer: WinlatorEmbeddedXServer? = null
        var boundSocket: LocalSocket? = null
        var serverSocket: LocalServerSocket? = null
        var clientSocket: LocalSocket? = null
        val acceptExecutor = Executors.newSingleThreadExecutor()
        val controlFile = File(filesDir, "runtime-control.sock").apply { delete() }
        val outputFile = File(filesDir, "runtime-session.log").apply { delete() }
        try {
            require(gameId.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid game id" }
            val gamesRoot = File(filesDir, "games").canonicalFile
            val gameRoot = File(filesDir, relativePath).canonicalFile
            require(gameRoot.toPath().startsWith(gamesRoot.toPath())) { "Game path escapes app storage" }
            val eboot = File(gameRoot, "eboot.bin")
            require(eboot.isFile) { "Imported eboot.bin is missing" }

            ManagedSession.update(ManagedSessionState.Preparing("runtime"))
            val runtimeRoot = installRuntime()
            runtimeRoot.resolve(".local/share").toFile().mkdirs()
            runtimeRoot.resolve(".config").toFile().mkdirs()
            ManagedSession.update(ManagedSessionState.Preparing("display"))
            val target = withTimeout(SURFACE_TIMEOUT_MS) { ManagedSession.surface.filterNotNull().first() }
            val socketRoot = File(filesDir, "x").apply { mkdirs() }
            xServer = WinlatorEmbeddedXServer(
                this,
                socketRoot,
                useAbstractXSocket = false,
                xSocketPath = "/X0",
                useSharedMemoryAudio = false,
            )
            xServer.start(target.surface, target.width, target.height)

            boundSocket = LocalSocket().also {
                it.bind(LocalSocketAddress(controlFile.path, LocalSocketAddress.Namespace.FILESYSTEM))
            }
            serverSocket = LocalServerSocket(boundSocket.fileDescriptor)
            val nativeLibraryDir = Paths.get(applicationInfo.nativeLibraryDir)
            val driverConfiguration = VulkanDriverConfiguration.resolve(vulkanDriver, runtimeRoot)
            val environment = runtimeEnvironment(runtimeRoot, socketRoot, xServer.display) + driverConfiguration.environment
            process = RuntimeProcessLauncher().launch(
                RuntimeProcessRequest(
                    nativeLibraryDir = nativeLibraryDir,
                    runtimeRoot = runtimeRoot,
                    overrideRoot = gameRoot.toPath(),
                    storageRoot = filesDir.toPath(),
                    shadPs4Executable = runtimeRoot.resolve("bin/shadps4"),
                    socketPath = controlFile.path,
                    environment = environment,
                    arguments = listOf("-g", eboot.path),
                    outputPath = outputFile.toPath(),
                    box64Mode = driverConfiguration.box64Mode,
                ),
            )
            val acceptFuture = acceptExecutor.submit<LocalSocket> { serverSocket.accept() }
            while (clientSocket == null) {
                clientSocket = try {
                    acceptFuture.get(ACCEPT_POLL_MILLIS, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    check(process?.isAlive == true) {
                        "shadPS4 exited before socket connect: ${process?.exitCode}"
                    }
                    null
                }
            }
            clientSocket.inputStream.bufferedReader().forEachLine { frame ->
                when {
                    frame == "BACHATA/1 EVENT Running" -> ManagedSession.update(ManagedSessionState.Running(gameId))
                    frame.startsWith("BACHATA/1 ERROR code=") -> ManagedSession.update(
                        ManagedSessionState.Failed(RuntimeErrorCode.CONTENT_INVALID, frame.substringAfter("code=")),
                    )
                }
            }
            process?.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            ManagedSession.update(ManagedSessionState.Stopped(process?.exitCode))
        } catch (_: CancellationException) {
            ManagedSession.update(ManagedSessionState.Stopped(process?.exitCode))
        } catch (error: Exception) {
            val childOutput = runCatching { outputFile.readLines().takeLast(MAX_ERROR_LOG_LINES).joinToString(" | ") }
                .getOrDefault("")
            val detail = listOfNotNull(error.message, childOutput.ifBlank { null }).joinToString(": ")
            ManagedSession.update(
                ManagedSessionState.Failed(RuntimeErrorCode.BACKEND_CRASHED, detail.ifBlank { error.javaClass.simpleName }),
            )
        } finally {
            process?.destroyForcibly()
            process = null
            runCatching { clientSocket?.close() }
            runCatching { serverSocket?.close() }
            runCatching { boundSocket?.close() }
            acceptExecutor.shutdownNow()
            runCatching { xServer?.let { runBlocking { it.stop() } } }
            controlFile.delete()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun installRuntime(): Path {
        val manifest = assets.open("runtime/manifest.json").bufferedReader().use {
            Json { ignoreUnknownKeys = true }.decodeFromString<RuntimeManifest>(it.readText())
        }
        val installRoot = File(filesDir, "runtime").toPath()
        val target = installRoot.resolve(manifest.runtimeVersion)
        if (target.toFile().isDirectory) return target
        return assets.open("runtime/runtime.zip").use { bundle ->
            RuntimeInstaller(installRoot).install(bundle, manifest).getOrElse { error ->
                if (error is FileAlreadyExistsException && target.toFile().isDirectory) target else throw error
            }
        }
    }

    private fun runtimeEnvironment(runtimeRoot: Path, socketRoot: File, display: String) = mapOf(
        "HOME" to runtimeRoot.toString(),
        "BOX64_PATH" to runtimeRoot.resolve("bin").toString(),
        "BOX64_LOG" to "1",
        "BOX64_LOAD_ADDR" to "0x6000000000",
        "BOX64_PREFER_WRAPPED" to "1",
        "BOX64_LD_LIBRARY_PATH" to "${runtimeRoot.resolve("lib/x86_64-linux-gnu")}:${runtimeRoot.resolve("lib64")}",
        "BOX64_EMULATED_LIBS" to EMULATED_LIBRARIES,
        "BACHATA_ALSA_SOCKET" to File(socketRoot, UnixSocketConfig.ALSA_SERVER_PATH).path,
        "DISPLAY" to display,
        "SDL_VIDEODRIVER" to "x11",
        "TMPDIR" to cacheDir.path,
        "XDG_CACHE_HOME" to File(cacheDir, "xdg").apply { mkdirs() }.path,
        "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
    )

    private fun stopSession() {
        process?.destroy()
        sessionJob?.cancel()
        sessionJob = null
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Emulation", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(ManagedSession.ACTION_STOP).setClassName(packageName, ManagedSession.SERVICE_CLASS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Bachata S4 emulation")
            .setContentText("Game session running")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "emulation"
        const val NOTIFICATION_ID = 41
        const val SURFACE_TIMEOUT_MS = 30_000L
        const val PROCESS_EXIT_TIMEOUT_SECONDS = 5L
        const val ACCEPT_POLL_MILLIS = 250L
        const val MAX_ERROR_LOG_LINES = 20
        const val EMULATED_LIBRARIES = "libSDL2-2.0.so.0:libudev.so.1:libuuid.so.1"
    }
}
