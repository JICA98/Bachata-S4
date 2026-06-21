package com.bachatas4.android.runtime.process

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class RuntimeProcessRequest(
    val nativeLibraryDir: Path,
    val runtimeRoot: Path,
    val shadPs4Executable: Path,
    val socketPath: String,
    val environment: Map<String, String> = emptyMap(),
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
        val box64 = request.nativeLibraryDir.resolve(BOX64_LIBRARY).toRealPath()
        if (box64.parent != nativeLibraryDir) {
            throw SecurityException("Box64 must be owned by nativeLibraryDir")
        }
        require(Files.isRegularFile(box64) && Files.isExecutable(box64)) {
            "Box64 is not an executable APK native library: $box64"
        }

        val runtimeRoot = request.runtimeRoot.toRealPath()
        val shadPs4 = request.shadPs4Executable.toRealPath()
        if (!shadPs4.startsWith(runtimeRoot)) {
            throw SecurityException("Runtime executable escapes runtime root: $shadPs4")
        }
        require(Files.isRegularFile(shadPs4) && Files.isReadable(shadPs4)) {
            "Runtime executable is not a readable file: $shadPs4"
        }
        require(request.socketPath.isNotBlank() && '\u0000' !in request.socketPath) {
            "Invalid runtime socket path"
        }

        return listOf(
            box64.toString(),
            shadPs4.toString(),
            "--bachata-socket",
            request.socketPath,
        )
    }

    fun launch(request: RuntimeProcessRequest): RuntimeProcessHandle {
        val builder = ProcessBuilder(command(request))
        builder.directory(request.runtimeRoot.toRealPath().toFile())
        builder.environment().apply {
            clear()
            request.environment.forEach { (name, value) ->
                if (name in ALLOWED_ENVIRONMENT) put(name, value)
            }
        }
        return starter.start(builder)
    }

    private companion object {
        const val BOX64_LIBRARY = "libbox64.so"
        val ALLOWED_ENVIRONMENT = setOf(
            "HOME",
            "BOX64_PATH",
            "BOX64_LD_LIBRARY_PATH",
            "DISPLAY",
            "TMPDIR",
            "XDG_CACHE_HOME",
            "MESA_SHADER_CACHE_DIR",
            "VK_ICD_FILENAMES",
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
