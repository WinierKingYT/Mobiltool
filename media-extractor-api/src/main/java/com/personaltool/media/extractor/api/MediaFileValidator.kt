package com.personaltool.media.extractor.api

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object MediaFileValidator {

    private const val MIN_FILE_SIZE_BYTES = 4096L // 4 KB

    fun validateFile(file: File): FileValidationResult {
        if (!file.exists()) {
            return FileValidationResult.Invalid("File does not exist at ${file.absolutePath}")
        }
        if (!file.isFile) {
            return FileValidationResult.Invalid("Path is not a regular file")
        }
        val size = file.length()
        if (size < MIN_FILE_SIZE_BYTES) {
            return FileValidationResult.Invalid("File size ($size bytes) is below minimum valid threshold ($MIN_FILE_SIZE_BYTES bytes)")
        }
        if (file.name.endsWith(".tmp") || file.name.endsWith(".part")) {
            return FileValidationResult.Invalid("File has incomplete download extension (.tmp/.part)")
        }

        val sha256 = calculateSha256(file)

        return FileValidationResult.Valid(
            fileSizeBytes = size,
            sha256Hex = sha256,
            extension = file.extension
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

sealed interface FileValidationResult {
    data class Valid(
        val fileSizeBytes: Long,
        val sha256Hex: String,
        val extension: String
    ) : FileValidationResult

    data class Invalid(val reason: String) : FileValidationResult
}
