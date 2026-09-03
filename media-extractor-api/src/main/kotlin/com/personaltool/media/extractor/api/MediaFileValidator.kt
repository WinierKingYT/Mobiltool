package com.personaltool.media.extractor.api

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

enum class ValidationContext {
    STAGING_PAYLOAD,
    CANONICAL_MEDIA
}

enum class DetectedContainer {
    MP4_ISO_BMFF,
    MATROSKA_WEBM,
    MP3,
    OGG,
    WAV,
    FLAC,
    MPEG_TS,
    UNKNOWN
}

sealed interface FileValidationResult {
    data class Valid(
        val fileSizeBytes: Long,
        val extension: String,
        val sha256Hex: String,
        val containerType: DetectedContainer,
        val detectedMimeType: String
    ) : FileValidationResult

    data class Invalid(val reason: String) : FileValidationResult
}

object MediaFileValidator {

    const val MIN_MEDIA_SIZE_BYTES = 1024L

    fun validateFile(
        file: File,
        context: ValidationContext = ValidationContext.CANONICAL_MEDIA
    ): FileValidationResult {
        if (!file.exists() || !file.canRead()) {
            return FileValidationResult.Invalid("File does not exist or cannot be read: ${file.absolutePath}")
        }

        val size = file.length()
        if (size < MIN_MEDIA_SIZE_BYTES) {
            return FileValidationResult.Invalid("File size ($size bytes) is below minimum valid media threshold of $MIN_MEDIA_SIZE_BYTES bytes")
        }

        val ext = file.extension.lowercase()
        val isPartArtifact = ext == "part" || ext == "tmp" || file.name.endsWith(".part")

        // Lifecycle Invariant (P2-DIRECT-FIX-01):
        // STAGING_PAYLOAD allows *.part files for pre-commit content validation.
        // CANONICAL_MEDIA strictly forbids *.part files from being exposed as completed media.
        if (context == ValidationContext.CANONICAL_MEDIA && isPartArtifact) {
            return FileValidationResult.Invalid("Canonical media cannot be an uncommitted temporary or *.part artifact")
        }

        // Read initial header bytes (first 768 bytes for multi-packet inspection)
        val header = ByteArray(768)
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

        // 3. Container Magic Bytes Verification & Media Type Truth (P2-DIRECT-FIX-05)
        val (container, mimeType) = detectContainerAndMime(header, bytesRead)
            ?: return FileValidationResult.Invalid("Unrecognized binary container header; not a valid MP4/WebM/MKV/MP3/AAC/OGG/WAV/FLAC/TS media stream")

        val sha256 = calculateSha256(file)

        return FileValidationResult.Valid(
            fileSizeBytes = size,
            extension = ext,
            sha256Hex = sha256,
            containerType = container,
            detectedMimeType = mimeType
        )
    }

    private fun detectContainerAndMime(header: ByteArray, length: Int): Pair<DetectedContainer, String>? {
        if (length < 4) return null

        // 1. ISO BMFF / MP4 / M4A / MOV (ftyp box at offset 4)
        if (length >= 12 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        ) {
            val majorBrand = String(header, 8, 4, Charsets.ISO_8859_1).trim().lowercase()
            val mime = when {
                majorBrand in listOf("m4a", "m4b", "m4p", "f4a", "f4b") -> "audio/mp4"
                majorBrand in listOf("qt", "moov") -> "video/quicktime"
                else -> "video/mp4"
            }
            return Pair(DetectedContainer.MP4_ISO_BMFF, mime)
        }

        // 2. Matroska / WebM: 0x1A 0x45 0xDF 0xA3
        if (header[0] == 0x1A.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() &&
            header[3] == 0xA3.toByte()
        ) {
            val headerAscii = String(header, 0, length, Charsets.ISO_8859_1).lowercase()
            val mime = if (headerAscii.contains("webm")) "video/webm" else "video/x-matroska"
            return Pair(DetectedContainer.MATROSKA_WEBM, mime)
        }

        // 3. MP3 with ID3v2 tag: "ID3" (0x49 0x44 0x33)
        if (header[0] == 0x49.toByte() &&
            header[1] == 0x44.toByte() &&
            header[2] == 0x33.toByte()
        ) {
            return Pair(DetectedContainer.MP3, "audio/mpeg")
        }

        // 4. MP3 without ID3 tag (MPEG audio sync word: 0xFF 0xFB, 0xFF 0xF3, 0xFF 0xF2)
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0 && (b1 and 0x06) != 0x00) {
            return Pair(DetectedContainer.MP3, "audio/mpeg")
        }

        // 5. Ogg Container: "OggS" (0x4F 0x67 0x67 0x53)
        if (header[0] == 0x4F.toByte() &&
            header[1] == 0x67.toByte() &&
            header[2] == 0x67.toByte() &&
            header[3] == 0x53.toByte()
        ) {
            return Pair(DetectedContainer.OGG, "audio/ogg")
        }

        // 6. RIFF WAV Container: "RIFF" .... "WAVE"
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
            return Pair(DetectedContainer.WAV, "audio/wav")
        }

        // 7. FLAC: "fLaC" (0x66 0x4C 0x61 0x43)
        if (header[0] == 0x66.toByte() &&
            header[1] == 0x4C.toByte() &&
            header[2] == 0x61.toByte() &&
            header[3] == 0x43.toByte()
        ) {
            return Pair(DetectedContainer.FLAC, "audio/flac")
        }

        // 8. MPEG-TS (P2-DIRECT-FIX-05): Check consecutive 188-byte packet sync markers (0x47)
        if (length >= 565 &&
            header[0] == 0x47.toByte() &&
            header[188] == 0x47.toByte() &&
            header[376] == 0x47.toByte() &&
            header[564] == 0x47.toByte()
        ) {
            return Pair(DetectedContainer.MPEG_TS, "video/mp2t")
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
