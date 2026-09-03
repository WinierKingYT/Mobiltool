package com.personaltool.media.extractor.api

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

sealed interface FileValidationResult {
    data class Valid(
        val fileSizeBytes: Long,
        val extension: String,
        val sha256Hex: String,
        val detectedMimeType: String
    ) : FileValidationResult

    data class Invalid(val reason: String) : FileValidationResult
}

object MediaFileValidator {

    const val MIN_MEDIA_SIZE_BYTES = 1024L

    fun validateFile(file: File): FileValidationResult {
        if (!file.exists() || !file.canRead()) {
            return FileValidationResult.Invalid("File does not exist or cannot be read: ${file.absolutePath}")
        }

        val size = file.length()
        if (size < MIN_MEDIA_SIZE_BYTES) {
            return FileValidationResult.Invalid("File size ($size bytes) is below minimum valid media threshold of $MIN_MEDIA_SIZE_BYTES bytes")
        }

        val ext = file.extension.lowercase()
        if (ext == "part" || ext == "tmp" || file.name.endsWith(".part")) {
            return FileValidationResult.Invalid("File appears to be an uncommitted temporary/part artifact")
        }

        // Read initial header bytes (first 512 bytes)
        val header = ByteArray(512)
        val bytesRead = try {
            FileInputStream(file).use { it.read(header) }
        } catch (e: Exception) {
            return FileValidationResult.Invalid("Failed reading file header: ${e.message}")
        }

        if (bytesRead < 8) {
            return FileValidationResult.Invalid("File header is too short ($bytesRead bytes) to verify container structure")
        }

        val headerString = String(header, 0, bytesRead, Charsets.ISO_8859_1).lowercase()

        // 1. Explicit HTML Rejection
        if (headerString.contains("<!doctype html") ||
            headerString.contains("<html") ||
            headerString.contains("<head") ||
            headerString.contains("<body")
        ) {
            return FileValidationResult.Invalid("File contains HTML markup rather than binary media payload (e.g. error or login page)")
        }

        // 2. Explicit JSON Error / Challenge Rejection
        val trimmedHeader = headerString.trimStart()
        if (trimmedHeader.startsWith("{") || trimmedHeader.startsWith("[")) {
            if (trimmedHeader.contains("\"error\"") ||
                trimmedHeader.contains("\"message\"") ||
                trimmedHeader.contains("\"challenge\"") ||
                trimmedHeader.contains("\"captcha\"") ||
                trimmedHeader.contains("\"status\"")
            ) {
                return FileValidationResult.Invalid("File contains JSON error or challenge payload rather than media stream")
            }
        }

        // 3. Container Magic Bytes Verification
        val detectedMime = detectContainerMimeType(header, bytesRead)
            ?: return FileValidationResult.Invalid("Unrecognized binary container header; not a valid MP4/WebM/MKV/MP3/AAC/OGG/WAV/FLAC media stream")

        val sha256 = calculateSha256(file)

        return FileValidationResult.Valid(
            fileSizeBytes = size,
            extension = ext,
            sha256Hex = sha256,
            detectedMimeType = detectedMime
        )
    }

    private fun detectContainerMimeType(header: ByteArray, length: Int): String? {
        if (length < 4) return null

        // ISO BMFF / MP4 / M4A / MOV (ftyp or moov box at offset 4)
        if (length >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        ) {
            return "video/mp4"
        }

        // Matroska / WebM: 0x1A 0x45 0xDF 0xA3
        if (header[0] == 0x1A.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() &&
            header[3] == 0xA3.toByte()
        ) {
            return "video/webm"
        }

        // MP3 with ID3v2 tag: "ID3" (0x49 0x44 0x33)
        if (header[0] == 0x49.toByte() &&
            header[1] == 0x44.toByte() &&
            header[2] == 0x33.toByte()
        ) {
            return "audio/mpeg"
        }

        // MP3 without ID3 tag (MPEG audio sync word: 0xFF 0xFB, 0xFF 0xF3, 0xFF 0xF2)
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0 && (b1 and 0x06) != 0x00) {
            return "audio/mpeg"
        }

        // Ogg Container: "OggS" (0x4F 0x67 0x67 0x53)
        if (header[0] == 0x4F.toByte() &&
            header[1] == 0x67.toByte() &&
            header[2] == 0x67.toByte() &&
            header[3] == 0x53.toByte()
        ) {
            return "audio/ogg"
        }

        // RIFF WAV Container: "RIFF" .... "WAVE"
        if (length >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'A'.code.toByte() &&
            header[10] == 'V'.code.toByte() &&
            header[11] == 'E'.code.toByte()
        ) {
            return "audio/wav"
        }

        // FLAC: "fLaC" (0x66 0x4C 0x61 0x43)
        if (header[0] == 0x66.toByte() &&
            header[1] == 0x4C.toByte() &&
            header[2] == 0x61.toByte() &&
            header[3] == 0x43.toByte()
        ) {
            return "audio/flac"
        }

        // MPEG-TS sync byte: 0x47
        if (header[0] == 0x47.toByte()) {
            return "video/mp2t"
        }

        return null
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(16384)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
