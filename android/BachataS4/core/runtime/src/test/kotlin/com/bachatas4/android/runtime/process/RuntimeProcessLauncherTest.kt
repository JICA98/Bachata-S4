package com.bachatas4.android.runtime.process

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeProcessLauncherTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun buildsExactShellFreeCommand() {
        val request = validRequest()
        val launcher = RuntimeProcessLauncher { FakeProcessHandle() }

        assertEquals(
            listOf(
                request.nativeLibraryDir.resolve("libbachata_host_loader.so").toRealPath().toString(),
                "--library-path",
                request.runtimeRoot.resolve("host").toRealPath().toString(),
                request.nativeLibraryDir.resolve("libbachata_host_box64.so").toRealPath().toString(),
                request.shadPs4Executable.toRealPath().toString(),
                "--override-root",
                request.overrideRoot.toRealPath().toString(),
                "--bachata-storage-root",
                request.storageRoot.toRealPath().toString(),
                "--bachata-socket",
                request.socketPath,
            ),
            launcher.command(request),
        )
    }

    @Test
    fun clearsInheritedEnvironmentAndCopiesOnlyAllowlist() {
        val request = validRequest(
            environment = mapOf(
                "HOME" to "/data/home",
                "DISPLAY" to ":0",
                "VK_ICD_FILENAMES" to "/data/turnip.json",
                "PATH" to "/system/bin",
                "LD_PRELOAD" to "/evil.so",
            ),
        )
        var captured: ProcessBuilder? = null
        val launcher = RuntimeProcessLauncher { builder ->
            captured = builder
            FakeProcessHandle()
        }

        launcher.launch(request)

        assertEquals(
            mapOf(
                "HOME" to "/data/home",
                "DISPLAY" to ":0",
                "VK_ICD_FILENAMES" to "/data/turnip.json",
            ),
            captured!!.environment(),
        )
        assertFalse(captured!!.environment().containsKey("PATH"))
        assertFalse(captured!!.environment().containsKey("LD_PRELOAD"))
    }

    @Test
    fun redirectsChildStreamsSoFullPipesCannotBlockRuntime() {
        val request = validRequest()
        var captured: ProcessBuilder? = null
        val launcher = RuntimeProcessLauncher { builder ->
            captured = builder
            FakeProcessHandle()
        }

        launcher.launch(request)

        assertEquals(ProcessBuilder.Redirect.Type.WRITE, captured!!.redirectOutput().type())
        assertEquals(ProcessBuilder.Redirect.Type.WRITE, captured!!.redirectError().type())
        assertEquals("/dev/null", captured!!.redirectOutput().file().path)
        assertEquals("/dev/null", captured!!.redirectError().file().path)
    }

    @Test
    fun allowsGameOverrideInsideSeparateStorageRoot() {
        val request = validRequest()
        val gameRoot = Files.createDirectories(request.storageRoot.resolve("games/CUSA00001"))
        val launcher = RuntimeProcessLauncher { FakeProcessHandle() }

        val command = launcher.command(request.copy(overrideRoot = gameRoot))

        assertEquals(gameRoot.toRealPath().toString(), command[6])
    }

    @Test
    fun rejectsBox64WhoseCanonicalParentEscapesNativeLibraryDir() {
        val request = validRequest()
        val outside = temporaryFolder.newFile("outside-box64").toPath()
        outside.toFile().setExecutable(true)
        Files.delete(request.nativeLibraryDir.resolve("libbachata_host_box64.so"))
        Files.createSymbolicLink(request.nativeLibraryDir.resolve("libbachata_host_box64.so"), outside)
        val launcher = RuntimeProcessLauncher { FakeProcessHandle() }

        assertThrows(SecurityException::class.java) { launcher.command(request) }
    }

    @Test
    fun rejectsRuntimeExecutableOutsideRuntimeRoot() {
        val request = validRequest()
        val outside = temporaryFolder.newFile("outside-shadps4").toPath()
        val launcher = RuntimeProcessLauncher { FakeProcessHandle() }

        assertThrows(SecurityException::class.java) {
            launcher.command(request.copy(shadPs4Executable = outside))
        }
    }

    private fun validRequest(environment: Map<String, String> = emptyMap()): RuntimeProcessRequest {
        val base = temporaryFolder.newFolder().toPath()
        val nativeLibraryDir = Files.createDirectories(base.resolve("apk/lib"))
        Files.write(nativeLibraryDir.resolve("libbachata_host_loader.so"), byteArrayOf(1))
        Files.write(nativeLibraryDir.resolve("libbachata_host_box64.so"), byteArrayOf(1))
        val runtimeRoot = Files.createDirectories(base.resolve("runtime"))
        Files.createDirectories(runtimeRoot.resolve("host"))
        val shadPs4 = Files.write(runtimeRoot.resolve("bin/shadps4").also {
            Files.createDirectories(it.parent)
        }, byteArrayOf(2))
        return RuntimeProcessRequest(
            nativeLibraryDir = nativeLibraryDir,
            runtimeRoot = runtimeRoot,
            overrideRoot = base,
            shadPs4Executable = shadPs4,
            socketPath = base.resolve("runtime.sock").toString(),
            environment = environment,
        )
    }
}

private class FakeProcessHandle : RuntimeProcessHandle {
    override val isAlive: Boolean = true
    override val exitCode: Int? = null
    override fun destroy() = Unit
    override fun destroyForcibly() = Unit
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = false
}
