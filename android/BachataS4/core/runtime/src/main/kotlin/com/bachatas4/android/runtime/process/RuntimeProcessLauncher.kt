package com.bachatas4.android.runtime.process

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

data class RuntimeProcessRequest(
    val nativeLibraryDir: Path,
    val runtimeRoot: Path,
    val overrideRoot: Path,
    val shadPs4Executable: Path,
    val socketPath: String,
    val environment: Map<String, String> = emptyMap(),
    val arguments: List<String> = emptyList(),
)

interface RuntimeProcessHandle {
    val isAlive: Boolean
    val exitCode: Int?

    fun destroy()

    fun destroyForcibly()

    fun waitFor(timeout: Long, unit: TimeUnit): Boolean
}

fun interface RuntimeProcessStarter {
    fun start(processBuilder: ProcessBuilder): RuntimeProcessHandle
}

class RuntimeProcessLauncher(
    private val starter: RuntimeProcessStarter = RuntimeProcessStarter { builder ->
        JavaRuntimeProcessHandle(builder.start())
    },
) {
    fun command(request: RuntimeProcessRequest): List<String> {
        val nativeLibraryDir = request.nativeLibraryDir.toRealPath()
        val loader = request.nativeLibraryDir.resolve(HOST_LOADER_LIBRARY).toRealPath()
        val box64 = request.nativeLibraryDir.resolve(HOST_BOX64_LIBRARY).toRealPath()
        validateNativeFile(nativeLibraryDir, loader, "Host glibc loader")
        validateNativeFile(nativeLibraryDir, box64, "Host Box64")

        val runtimeRoot = request.runtimeRoot.toRealPath()
        val hostDirectory = runtimeRoot.resolve(HOST_DIRECTORY).toRealPath()
        require(hostDirectory.startsWith(runtimeRoot) && Files.isDirectory(hostDirectory)) {
            "Host runtime directory escapes runtime root: $hostDirectory"
        }
        val overrideRoot = request.overrideRoot.toRealPath()
        val shadPs4 = request.shadPs4Executable.toRealPath()
        if (!shadPs4.startsWith(runtimeRoot)) {
            throw SecurityException("Runtime executable escapes runtime root: $shadPs4")
        }
        require(Files.isRegularFile(shadPs4) && Files.isReadable(shadPs4)) {
            "Runtime executable is not a readable file: $shadPs4"
        }
        val socketPath = Paths.get(request.socketPath).toAbsolutePath().normalize()
        require(request.socketPath.isNotBlank() && '\u0000' !in request.socketPath && socketPath.startsWith(overrideRoot)) {
            "Invalid runtime socket path"
        }
        request.arguments.forEach { require('\u0000' !in it) { "Runtime argument contains NUL" } }

        return listOf(
            loader.toString(),
            "--library-path",
            hostDirectory.toString(),
            box64.toString(),
            shadPs4.toString(),
            "--override-root",
            overrideRoot.toString(),
            "--bachata-socket",
            request.socketPath,
        ) + request.arguments
    }

    private fun validateNativeFile(nativeLibraryDir: Path, path: Path, label: String) {
        if (path.parent != nativeLibraryDir) throw SecurityException("$label must be owned by nativeLibraryDir")
        require(Files.isRegularFile(path) && Files.isReadable(path)) { "$label is not readable: $path" }
    }

    fun launch(request: RuntimeProcessRequest): RuntimeProcessHandle {
        val builder = ProcessBuilder(command(request))
        builder.directory(request.runtimeRoot.toRealPath().toFile())
        builder.redirectOutput(NULL_DEVICE)
        builder.redirectError(NULL_DEVICE)
        builder.environment().apply {
            clear()
            request.environment.forEach { (name, value) ->
                if (name in ALLOWED_ENVIRONMENT) put(name, value)
            }
        }
        return starter.start(builder)
    }

    private companion object {
        const val HOST_DIRECTORY = "host"
        const val HOST_LOADER_LIBRARY = "libbachata_host_loader.so"
        const val HOST_BOX64_LIBRARY = "libbachata_host_box64.so"
        val NULL_DEVICE = File("/dev/null")
        val ALLOWED_ENVIRONMENT = setOf(
            "HOME",
            "LD_LIBRARY_PATH",
            "BOX64_PATH",
            "BOX64_LD_LIBRARY_PATH",
            "BOX64_EMULATED_LIBS",
            "BACHATA_ALSA_SOCKET",
            "DISPLAY",
            "SDL_VIDEODRIVER",
            "TMPDIR",
            "XDG_CACHE_HOME",
            "MESA_SHADER_CACHE_DIR",
            "VK_ICD_FILENAMES",
            "GLIBC_TUNABLES",
        )
    }
}

private class JavaRuntimeProcessHandle(
    private val process: Process,
) : RuntimeProcessHandle {
    override val isAlive: Boolean
        get() = process.isAlive

    override val exitCode: Int?
        get() = runCatching(process::exitValue).getOrNull()

    override fun destroy() {
        process.destroy()
    }

    override fun destroyForcibly() {
        process.destroyForcibly()
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = process.waitFor(timeout, unit)
}
