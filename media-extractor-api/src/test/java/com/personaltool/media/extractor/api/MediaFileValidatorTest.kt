package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaFileValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun validFile_passesValidation_withCalculatedSha256() {
        val file = tempFolder.newFile("sample_video.mp4")
        file.writeBytes(ByteArray(8192) { 0x55 })

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Valid::class.java)

        val valid = result as FileValidationResult.Valid
        assertThat(valid.fileSizeBytes).isEqualTo(8192L)
        assertThat(valid.extension).isEqualTo("mp4")
        assertThat(valid.sha256Hex).isNotEmpty()
    }

    @Test
    fun smallFile_failsValidationThreshold() {
        val file = tempFolder.newFile("empty.mp4")
        file.writeBytes(ByteArray(50)) // less than 4KB

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Invalid::class.java)
    }

    @Test
    fun incompleteExtension_failsValidation() {
        val file = tempFolder.newFile("stream.mp4.part")
        file.writeBytes(ByteArray(8192))

        val result = MediaFileValidator.validateFile(file)
        assertThat(result).isInstanceOf(FileValidationResult.Invalid::class.java)
    }
}
