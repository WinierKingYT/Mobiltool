package com.personaltool.core.storage.cleanup

import java.io.File

object StagingCleaner {

    fun cleanStagingDirectory(stagingDir: File, maxAgeMs: Long = 3600000L): CleanupResult {
        if (!stagingDir.exists() || !stagingDir.isDirectory) {
            return CleanupResult(deletedFilesCount = 0, reclaimedBytes = 0L)
        }

        var deletedCount = 0
        var reclaimedBytes = 0L
        val now = System.currentTimeMillis()

        stagingDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val isStale = (now - file.lastModified()) > maxAgeMs
                val isTempExt = file.name.endsWith(".tmp") || file.name.endsWith(".part")
                if (isStale || isTempExt) {
                    val size = file.length()
                    if (file.delete()) {
                        deletedCount++
                        reclaimedBytes += size
                    }
                }
            }
        }

        return CleanupResult(
            deletedFilesCount = deletedCount,
            reclaimedBytes = reclaimedBytes
        )
    }

    fun purgeAllTempFiles(tempDir: File): CleanupResult {
        if (!tempDir.exists() || !tempDir.isDirectory) {
            return CleanupResult(deletedFilesCount = 0, reclaimedBytes = 0L)
        }

        var deletedCount = 0
        var reclaimedBytes = 0L

        tempDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val size = file.length()
                if (file.delete()) {
                    deletedCount++
                    reclaimedBytes += size
                }
            }
        }

        return CleanupResult(
            deletedFilesCount = deletedCount,
            reclaimedBytes = reclaimedBytes
        )
    }
}

data class CleanupResult(
    val deletedFilesCount: Int,
    val reclaimedBytes: Long
)
