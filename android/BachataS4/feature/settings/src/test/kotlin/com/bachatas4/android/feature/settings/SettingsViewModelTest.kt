package com.bachatas4.android.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun diagnosticExportHasRevisionsButNoAbsoluteGamePaths() {
        val viewModel = SettingsViewModel()

        viewModel.setDiagnostics(
            runtimeRevision = "runtime-abc123",
            vulkanUuid = "vk-uuid",
            gameIds = listOf("CUSA00002", "CUSA00001"),
        )

        val export = viewModel.diagnosticExport()
        assertTrue(export.contains("runtimeRevision=runtime-abc123"))
        assertTrue(export.contains("vulkanUuid=vk-uuid"))
        assertTrue(export.contains("gameIds=CUSA00001,CUSA00002"))
        assertFalse(export.contains("/"))
    }
}
