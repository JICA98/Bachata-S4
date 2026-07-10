package com.bachatas4.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityRouteTest {
    @Test
    fun qualifiedSnapdragonStartsInLibrary() {
        assertEquals(BachataRoutes.Library, initialRouteForSoc("SM8650"))
        assertEquals(BachataRoutes.Library, initialRouteForSoc("sm8750"))
    }

    @Test
    fun unknownHardwareStartsInSetup() {
        assertEquals(BachataRoutes.Setup, initialRouteForSoc("Tensor G5"))
    }

    @Test
    fun exposesGlobalGameDriverAndEditorRoutes() {
        assertEquals("settings", BachataRoutes.Settings)
        assertEquals("settings/game/CUSA00001", BachataRoutes.gameSettings("CUSA00001"))
        assertEquals("drivers", BachataRoutes.Drivers)
    }
}
