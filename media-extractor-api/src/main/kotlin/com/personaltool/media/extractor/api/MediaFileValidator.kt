package com.personaltool.media.extractor.api

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

sealed interface FileValidationResult {
    data class Valid(
        val fileSizeBytes: Long,
        val extension: String,
        val sha256Hex: String
    ) : FileValidationResult

    data class Invalid(val reason: String) : FileValidationResult
}

object MediaFileValidator {

    private const val MIN_MEDIA_SIZE_BYTES = 4096L

    fun validateFile(file: File): FileValidationResult {
        if (!file.exists()) {
            return FileValidationResult.Invalid("File does not exist: ${file.absolutePath}")
        }

        val size = file.length()
        if (size < MIN_MEDIA_SIZE_BYTES) {
            return FileValidationResult.Invalid("File size ($size bytes) is below minimum threshold of $MIN_MEDIA_SIZE_BYTES bytes")
        }

        val ext = file.extension.lowercase()
        if (ext.endsWith(".part") || ext == "tmp" || ext == "part") {
            return FileValidationResult.Invalid("File appears to be an incomplete download ($ext)")
        }

        val sha256 = calculateSha256(file)
        return FileValidationResult.Valid(
            fileSizeBytes = size,
            extension = ext,
            sha256Hex = sha256
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
