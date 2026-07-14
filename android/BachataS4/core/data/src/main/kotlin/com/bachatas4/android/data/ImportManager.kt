package com.bachatas4.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface ImportProgress {
    data object Idle : ImportProgress
    data class Scanning(val folderName: String) : ImportProgress
    data class Copying(
        val bytesCopied: Long,
        val totalBytes: Long,
        val currentFile: String,
        val gameTitle: String,
    ) : ImportProgress
    data class Success(val gameId: String, val title: String) : ImportProgress
    data class Failed(val message: String) : ImportProgress
}

object ImportManager {
    const val ACTION_IMPORT = "com.bachatas4.android.action.IMPORT_GAME"
    const val ACTION_CANCEL = "com.bachatas4.android.action.CANCEL_IMPORT"
    const val EXTRA_URI = "source_uri"
    const val SERVICE_CLASS = "com.bachatas4.android.service.ImportService"

    private val _progress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val progress: StateFlow<ImportProgress> = _progress

    fun update(state: ImportProgress) {
        _progress.value = state
    }

    fun reset() {
        _progress.value = ImportProgress.Idle
    }
}
