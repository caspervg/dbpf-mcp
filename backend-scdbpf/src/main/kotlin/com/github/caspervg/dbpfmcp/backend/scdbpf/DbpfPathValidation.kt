package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.PackageError
import java.io.File

internal fun requireDbpfPackageFile(path: String): File {
    val file = File(path)
    if (!file.exists()) {
        throw PackageError("File not found: ${file.absolutePath}")
    }
    if (!file.isFile) {
        throw InputError(
            "Expected one DBPF package file, not a directory: ${file.absolutePath}. " +
                "Use index_plugins for Plugins folders.",
        )
    }
    return file
}
