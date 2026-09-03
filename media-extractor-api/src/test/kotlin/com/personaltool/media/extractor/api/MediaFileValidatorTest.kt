package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class MediaFileValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun validMp4File_passesValidation_andReturnsCorrectMimeAndHash() {
        val file = tempFolder.newFile("sample.mp4")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
            fos.write(header)
            fos.write(ByteArray(2048) { 0x55 })
        }

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)
        val valid = result as FileValidationResult.Valid
        assertThat(valid.detectedMimeType).isEqualTo("video/mp4")
        assertThat(valid.fileSizeBytes).isEqualTo(2056L)
        assertThat(valid.sha256Hex).isNotEmpty()
    }

    @Test
    fun validWebmFile_passesValidation() {
        val file = tempFolder.newFile("sample.webm")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
            fos.write(header)
            fos.write(ByteArray(2048) { 0x33 })
        }

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)
        val valid = result as FileValidationResult.Valid
        assertThat(valid.detectedMimeType).isEqualTo("video/webm")
    }

    @Test
    fun validMp3WithId3_passesValidation() {
        val file = tempFolder.newFile("sample.mp3")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00)
            fos.write(header)
            fos.write(ByteArray(2048) { 0x11 })
        }

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)
        val valid = result as FileValidationResult.Valid
        assertThat(valid.detectedMimeType).isEqualTo("audio/mpeg")
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

    @Test
    fun partTemporaryFile_isRejected() {
        val file = tempFolder.newFile("sample.mp4.part")
        FileOutputStream(file).use { fos ->
            fos.write(ByteArray(2048) { 0x22 })
        }

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Invalid::class.java)
        val invalid = result as FileValidationResult.Invalid
        assertThat(invalid.reason).contains("temporary/part artifact")
    }
}
