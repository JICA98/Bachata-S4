package com.bachatas4.android.runtime

import com.bachatas4.android.model.LaunchRequest
import com.bachatas4.android.model.RuntimeState
import com.bachatas4.android.runtime.process.RuntimeProcessHandle
import com.bachatas4.android.runtime.process.RuntimeProcessRequest
import com.bachatas4.android.runtime.protocol.RuntimeMessage
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Box64EmulatorRuntimeTest {
    @Test
    fun launchTransitionsPreparingToRunningAndRejectsSecondSession() = runTest {
        val enteredLauncher = CompletableDeferred<Unit>()
        val releaseLauncher = CompletableDeferred<Unit>()
        val process = FakeOwnedProcess()
        var launchCount = 0
        val runtime = runtime(
            processLauncher = {
                launchCount++
                enteredLauncher.complete(Unit)
                releaseLauncher.await()
                process
            },
        )

        assertEquals(RuntimeState.Idle, runtime.state.value)
        val first = async { runtime.launch(LAUNCH_REQUEST) }
        enteredLauncher.await()
        assertEquals(RuntimeState.Preparing("launch"), runtime.state.value)
        releaseLauncher.complete(Unit)
        val sessionId = first.await().getOrThrow()

        assertEquals("session-1", sessionId)
        assertEquals(RuntimeState.Running("session-1"), runtime.state.value)
        assertTrue(runtime.launch(LAUNCH_REQUEST).isFailure)
        assertEquals(1, launchCount)
    }

    @Test
    fun stopSendsProtocolAndEscalatesOwnedProcessAtExactTimeouts() = runTest {
        val process = FakeOwnedProcess()
        val messages = mutableListOf<RuntimeMessage>()
        val waits = mutableListOf<Long>()
        val runtime = runtime(
            processLauncher = { process },
            controlChannel = { messages += it },
            processWaiter = { owned, timeout ->
                assertTrue(owned === process)
                waits += timeout
                false
            },
        )
        runtime.launch(LAUNCH_REQUEST).getOrThrow()

        runtime.stop().getOrThrow()

        assertEquals(listOf(RuntimeMessage.Stop), messages)
        assertEquals(listOf(5_000L, 2_000L), waits)
        assertTrue(process.destroyed)
        assertTrue(process.destroyedForcibly)
        assertEquals(RuntimeState.Stopped(-1), runtime.state.value)
    }

    @Test
    fun gracefulStopDoesNotDestroyProcess() = runTest {
        val process = FakeOwnedProcess(exitCode = 0)
        val runtime = runtime(
            processLauncher = { process },
            processWaiter = { _, _ -> true },
        )
        runtime.launch(LAUNCH_REQUEST).getOrThrow()

        runtime.stop().getOrThrow()

        assertFalse(process.destroyed)
        assertFalse(process.destroyedForcibly)
        assertEquals(RuntimeState.Stopped(0), runtime.state.value)
    }

    @Test
    fun cancelledStopStillFinishesOwnedProcessCleanup() = runTest {
        val waitStarted = CompletableDeferred<Unit>()
        val releaseWait = CompletableDeferred<Unit>()
        var launchCount = 0
        val runtime = runtime(
            processLauncher = {
                launchCount++
                FakeOwnedProcess(exitCode = 0)
            },
            processWaiter = { _, _ ->
                waitStarted.complete(Unit)
                releaseWait.await()
                true
            },
        )
        runtime.launch(LAUNCH_REQUEST).getOrThrow()
        val stop = launch { runtime.stop() }
        waitStarted.await()

        stop.cancel()
        releaseWait.complete(Unit)
        stop.join()

        assertEquals(RuntimeState.Stopped(0), runtime.state.value)
        assertTrue(runtime.launch(LAUNCH_REQUEST).isSuccess)
        assertEquals(2, launchCount)
    }

    private fun runtime(
        processLauncher: RuntimeProcessFactory,
        controlChannel: RuntimeControlChannel = RuntimeControlChannel { },
        processWaiter: RuntimeProcessWaiter = RuntimeProcessWaiter { _, _ -> true },
    ) = Box64EmulatorRuntime(
        installationVerifier = RuntimeInstallationVerifier { },
        processLauncher = processLauncher,
        requestFactory = RuntimeProcessRequestFactory { _, _ -> PROCESS_REQUEST },
        controlChannel = controlChannel,
        processWaiter = processWaiter,
        sessionIdProvider = SessionIdProvider { "session-1" },
    )

    private companion object {
        val LAUNCH_REQUEST = LaunchRequest("CUSA00001", "game/eboot.bin")
        val PROCESS_REQUEST = RuntimeProcessRequest(
            nativeLibraryDir = Path.of("/apk/lib"),
            runtimeRoot = Path.of("/data/runtime"),
            shadPs4Executable = Path.of("/data/runtime/bin/shadps4"),
            socketPath = "/data/runtime/session.sock",
        )
    }
}

private class FakeOwnedProcess(
    override val exitCode: Int? = null,
) : RuntimeProcessHandle {
    override val isAlive: Boolean
        get() = !destroyed && !destroyedForcibly && exitCode == null

    var destroyed = false
    var destroyedForcibly = false

    override fun destroy() {
        destroyed = true
    }

    override fun destroyForcibly() {
        destroyedForcibly = true
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = false
}
