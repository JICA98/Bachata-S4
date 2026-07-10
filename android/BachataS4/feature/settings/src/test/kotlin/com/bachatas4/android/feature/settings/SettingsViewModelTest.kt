package com.bachatas4.android.feature.settings

import com.bachatas4.android.data.RuntimeProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun viewModel() = SettingsViewModel(RuntimeProfileStore(temporaryFolder.root))

    @Test
    fun exposesEveryCatalogEntryAndFiltersByNativeKey() {
        val viewModel = viewModel()
        assertEquals(86, viewModel.state.value.settings.size)
        viewModel.search("BOX64_LOG")
        assertTrue(viewModel.state.value.settings.isEmpty())
        viewModel.selectRuntime(SettingsRuntime.BOX64)
        assertEquals(listOf("BOX64_LOG"), viewModel.state.value.settings.map { it.nativeKey })
    }

    @Test
    fun diagnosticExportHasRevisionsButNoAbsoluteGamePaths() {
        val viewModel = viewModel()
        viewModel.setDiagnostics("runtime-abc123", "vk-uuid", listOf("CUSA00002", "CUSA00001"))
        val export = viewModel.diagnosticExport()
        assertTrue(export.contains("runtimeRevision=runtime-abc123"))
        assertTrue(export.contains("vulkanUuid=vk-uuid"))
        assertTrue(export.contains("gameIds=CUSA00001,CUSA00002"))
        assertFalse(export.contains("/"))
    }
}
