package com.personaltool.transcription.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultTranscriptionEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun checkModelStatus_reportsTruthfulUnlinkedState() = runTest {
        val engine = DefaultTranscriptionEngine()
        val status = engine.checkModelStatus()

        assertThat(status.isReady).isFalse()
    }

    @Test
    fun transcribe_whenFileDoesNotExist_returnsFileNotFoundError() = runTest {
        val engine = DefaultTranscriptionEngine()
        val request = TranscriptionRequest(
            targetId = "target-123",
            audioFilePath = "/non/existent/path/audio.m4a"
        )

        val result = engine.transcribe(request) {}

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("does not exist")
    }

    @Test
    fun transcribe_whenAudioFileExists_truthfullyRefusesToFabricateText() = runTest {
        val realAudioFile = tempFolder.newFile("sample.m4a")
        realAudioFile.writeBytes(ByteArray(4096) { 0x11 })

        val engine = DefaultTranscriptionEngine()
        val request = TranscriptionRequest(
            targetId = "target-456",
            audioFilePath = realAudioFile.absolutePath
        )

        val result = engine.transcribe(request) {}

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        val error = result as AppResult.Error
        assertThat(error.message).contains("STT_RUNTIME_UNAVAILABLE")
    }
}
