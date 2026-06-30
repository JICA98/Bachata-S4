package com.bachatas4.android.runtime.diagnostics

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticExporter(
    private val appRoot: File,
    private val gameRoot: File,
) {
    fun export(output: OutputStream, entries: Map<String, String>) {
        val appPath = appRoot.canonicalPath
        val gamePath = gameRoot.canonicalPath
        ZipOutputStream(output).use { zip ->
            entries.toSortedMap().forEach { (name, content) ->
                require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "Unsafe diagnostic entry name" }
                val sanitized = content.replace(gamePath, "<GAME>").replace(appPath, "<APP_DATA>")
                zip.putNextEntry(ZipEntry(name))
                zip.write(sanitized.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
