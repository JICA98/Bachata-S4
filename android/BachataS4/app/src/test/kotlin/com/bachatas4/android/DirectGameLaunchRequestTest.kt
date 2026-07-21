package com.bachatas4.android

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectGameLaunchRequestTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun resolvesInstalledTitleId() {
        val filesDir = temporaryFolder.newFolder("files")
        File(filesDir, "games/CUSA07023").mkdirs()
        File(filesDir, "games/CUSA07023/eboot.bin").createNewFile()

        assertEquals(
            DirectGameLaunchRequest.Resolution.Ready("CUSA07023"),
            DirectGameLaunchRequest.resolve(filesDir, "CUSA07023"),
        )
    }

    @Test
    fun rejectsMissingMalformedAndUninstalledIds() {
        val filesDir = temporaryFolder.newFolder("files")
        val rejected = listOf(null, "", "cusa07023", "CUSA0702", "../CUSA07023", "CUSA99999")

        rejected.forEach { gameId ->
            assertTrue(
                "$gameId must be rejected",
                DirectGameLaunchRequest.resolve(filesDir, gameId) is
                    DirectGameLaunchRequest.Resolution.Rejected,
            )
        }
    }

    @Test
    fun rejectsGameDirectorySymlinkEscapingAppStorage() {
        val filesDir = temporaryFolder.newFolder("files")
        val gamesDir = File(filesDir, "games").apply { mkdirs() }
        val outside = temporaryFolder.newFolder("outside")
        File(outside, "eboot.bin").createNewFile()
        Files.createSymbolicLink(File(gamesDir, "CUSA07023").toPath(), outside.toPath())

        assertTrue(
            DirectGameLaunchRequest.resolve(filesDir, "CUSA07023") is
                DirectGameLaunchRequest.Resolution.Rejected,
        )
    }
}
