package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream

class MediaFileValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun validMp4File_passesCanonicalValidation_withUnknownMimeAndKind() {
        val file = tempFolder.newFile("sample.mp4")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte())
            fos.write(header)
            fos.write(ByteArray(2048) { 0x55 })
        }

        val result = MediaFileValidator.validateFile(file, context = ValidationContext.CANONICAL_MEDIA)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)
        val valid = result as FileValidationResult.Valid
        assertThat(valid.containerType).isEqualTo(DetectedContainer.MP4_ISO_BMFF)
        // P2-TRUTH-LOCK-01 Invariant: Track type unknown -> DO NOT claim video/mp4 MIME or VIDEO media kind
        assertThat(valid.mediaKind).isEqualTo(DetectedMediaKind.UNKNOWN)
        assertThat(valid.detectedMimeType).isNull()
        assertThat(valid.fileSizeBytes).isEqualTo(2060L)
        assertThat(valid.sha256Hex).isNotEmpty()
    }

    @Test
    fun m4aAudioMajorBrand_isDetectedAsAudioMp4() {
        val file = tempFolder.newFile("song.m4a")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte())
            fos.write(header)
            fos.write(ByteArray(2048) { 0x44 })
        }

        val result = MediaFileValidator.validateFile(file, context = ValidationContext.CANONICAL_MEDIA)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)
        val valid = result as FileValidationResult.Valid
        assertThat(valid.containerType).isEqualTo(DetectedContainer.MP4_ISO_BMFF)
        assertThat(valid.mediaKind).isEqualTo(DetectedMediaKind.AUDIO)
        assertThat(valid.detectedMimeType).isEqualTo("audio/mp4")
    }

    @Test
    fun stagingPartFile_passesStagingValidation_butFailsCanonicalValidation() {
        val file = tempFolder.newFile("download_stream.mp4.part")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte())
            fos.write(header)
            fos.write(ByteArray(2048) { 0x55 })
        }

        // 1. In STAGING_PAYLOAD phase: Must PASS
        val stagingResult = MediaFileValidator.validateFile(file, context = ValidationContext.STAGING_PAYLOAD)
        assertThat(stagingResult).isInstanceOf(FileValidationResult.Valid::class.java)

        // 2. In CANONICAL_MEDIA phase: Must FAIL (P2-DIRECT-FIX-01 Invariant)
        val canonicalResult = MediaFileValidator.validateFile(file, context = ValidationContext.CANONICAL_MEDIA)
        assertThat(canonicalResult).isInstanceOf(FileValidationResult.Invalid::class.java)
        val invalid = canonicalResult as FileValidationResult.Invalid
        assertThat(invalid.reason).contains("uncommitted temporary or *.part artifact")
    }

    @Test
    fun validWebmFile_passesValidation_withUnknownMimeAndKind() {
        val file = tempFolder.newFile("sample.webm")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
            fos.write(header)
            fos.write("...webm...".toByteArray(Charsets.ISO_8859_1))
            fos.write(ByteArray(2048) { 0x33 })
        }

        val result = MediaFileValidator.validateFile(file, context = ValidationContext.CANONICAL_MEDIA)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)
        val valid = result as FileValidationResult.Valid
        assertThat(valid.containerType).isEqualTo(DetectedContainer.MATROSKA_WEBM)
        assertThat(valid.mediaKind).isEqualTo(DetectedMediaKind.UNKNOWN)
        assertThat(valid.detectedMimeType).isNull()
    }

    @Test
    fun validMp3WithId3_passesValidation() {
        val file = tempFolder.newFile("sample.mp3")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00)
            fos.write(header)
            fos.write(ByteArray(2048) { 0x11 })
        }

        val result = MediaFileValidator.validateFile(file, context = ValidationContext.CANONICAL_MEDIA)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)
        val valid = result as FileValidationResult.Valid
        assertThat(valid.containerType).isEqualTo(DetectedContainer.MP3)
        assertThat(valid.mediaKind).isEqualTo(DetectedMediaKind.AUDIO)
        assertThat(valid.detectedMimeType).isEqualTo("audio/mpeg")
    }

    @Test
    fun validMpegTs_withMultiPacketSync_passesValidation_withUnknownMimeAndKind() {
        val file = tempFolder.newFile("stream.ts")
        val data = ByteArray(188 * 12) { 0x00 } // > 1024 bytes
        // Set sync byte 0x47 every 188 bytes
        for (i in 0 until 12) {
            data[i * 188] = 0x47
        }

        FileOutputStream(file).use { fos -> fos.write(data) }

        val result = MediaFileValidator.validateFile(file, context = ValidationContext.CANONICAL_MEDIA)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)
        val valid = result as FileValidationResult.Valid
        assertThat(valid.containerType).isEqualTo(DetectedContainer.MPEG_TS)
        assertThat(valid.mediaKind).isEqualTo(DetectedMediaKind.UNKNOWN)
        assertThat(valid.detectedMimeType).isNull()
    }

    @Test
    fun invalidMpegTs_withSingleSyncByteOnly_isRejected() {
        val file = tempFolder.newFile("random.ts")
        val data = ByteArray(2048) { 0x00 }
        data[0] = 0x47 // only first byte is 0x47
        FileOutputStream(file).use { fos -> fos.write(data) }

        val result = MediaFileValidator.validateFile(file, context = ValidationContext.CANONICAL_MEDIA)
        assertThat(result).isInstanceOf(FileValidationResult.Invalid::class.java)
    }

    @Test
    fun fileUnderMinimumThreshold_isRejected() {
        val file = tempFolder.newFile("tiny.mp4")
        FileOutputStream(file).use { fos ->
            fos.write(ByteArray(500) { 0x00 })
        }

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Invalid::class.java)
        val invalid = result as FileValidationResult.Invalid
        assertThat(invalid.reason).contains("below minimum valid media threshold")
    }

    @Test
    fun htmlPageDisguisedAsMedia_isRejected() {
        val file = tempFolder.newFile("video_error.mp4")
        FileOutputStream(file).use { fos ->
            fos.write("<!DOCTYPE html><html><head><title>Access Denied</title></head><body><h1>403 Forbidden</h1></body></html>".toByteArray())
            fos.write(ByteArray(2048) { ' '.code.toByte() })
        }

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Invalid::class.java)
        val invalid = result as FileValidationResult.Invalid
        assertThat(invalid.reason).contains("HTML markup")
    }

    @Test
    fun jsonErrorPayloadDisguisedAsMedia_isRejected() {
        val file = tempFolder.newFile("api_error.mp4")
        FileOutputStream(file).use { fos ->
            fos.write("""{"status": 401, "error": "Unauthorized", "message": "Authentication required"}""".toByteArray())
            fos.write(ByteArray(2048) { ' '.code.toByte() })
        }

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Invalid::class.java)
        val invalid = result as FileValidationResult.Invalid
        assertThat(invalid.reason).contains("JSON error")
    }
}
