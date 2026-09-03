package com.personaltool.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class VaultItemEvaluatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val evaluator = DefaultVaultItemEvaluator()

    private fun createValidMp4File(name: String, size: Int = 4096): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(
                0x00, 0x00, 0x00, 0x20,
                'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()
            )
            fos.write(header)
            fos.write(ByteArray(size - header.size) { 0x11 })
        }
        return file
    }

    private fun createValidM4aFile(name: String, size: Int = 4096): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(
                0x00, 0x00, 0x00, 0x20,
                'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()
            )
            fos.write(header)
            fos.write(ByteArray(size - header.size) { 0x22 })
        }
        return file
    }

    // ==========================================
    // CALL FILE STATE EVALUATION TESTS
    // ==========================================

    @Test
    fun call_nullAudioPath_evaluatesToNoLocalFile() {
        val session = CallSession(
            id = "call-1",
            phoneNumber = "+1234567890",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1000L,
            audioFilePath = null
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.NO_LOCAL_FILE)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
        assertThat(vaultCall.availableSizeBytes).isEqualTo(0L)
    }

    @Test
    fun call_missingAudioPath_evaluatesToMissing() {
        val missingPath = File(tempFolder.root, "non_existent_call.m4a").absolutePath
        val session = CallSession(
            id = "call-2",
            phoneNumber = "+1234567890",
            direction = CallDirection.OUTGOING,
            startTimeEpochMs = 1000L,
            audioFilePath = missingPath
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.MISSING)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
        assertThat(vaultCall.availableSizeBytes).isEqualTo(0L)
    }

    @Test
    fun call_missingAudioPathWithTranscript_evaluatesToMissing_withOpenTranscriptAction() {
        val missingPath = File(tempFolder.root, "non_existent_call.m4a").absolutePath
        val session = CallSession(
            id = "call-3",
            phoneNumber = "+1234567890",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1000L,
            audioFilePath = missingPath,
            hasTranscript = true
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.MISSING)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.OPEN_TRANSCRIPT)
    }

    @Test
    fun call_sizeMismatch_evaluatesToSizeMismatch() {
        val file = createValidM4aFile("call_mismatch.m4a", size = 4096)
        val session = CallSession(
            id = "call-4",
            phoneNumber = "+1234567890",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1000L,
            audioFilePath = file.absolutePath,
            fileSizeBytes = 8192L // Expected 8192, actual 4096
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.SIZE_MISMATCH)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun call_tinyFtypFile_evaluatesToInvalidMedia() {
        val tinyFile = tempFolder.newFile("tiny_call.m4a")
        FileOutputStream(tinyFile).use { fos ->
            fos.write(byteArrayOf(
                0x00, 0x00, 0x00, 0x0C,
                'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()
            )) // Only 12 bytes
        }

        val session = CallSession(
            id = "call-tiny",
            phoneNumber = "+1234567890",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1000L,
            audioFilePath = tinyFile.absolutePath,
            fileSizeBytes = 12L
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.INVALID_MEDIA)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun call_partFileWithFtyp_evaluatesToInvalidMedia() {
        val partFile = createValidM4aFile("active_call.m4a.part", size = 4096)
        val session = CallSession(
            id = "call-part",
            phoneNumber = "+1234567890",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1000L,
            audioFilePath = partFile.absolutePath,
            fileSizeBytes = 4096L
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.INVALID_MEDIA)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun call_tmpFileWithFtyp_evaluatesToInvalidMedia() {
        val tmpFile = createValidM4aFile("temp_call.m4a.tmp", size = 4096)
        val session = CallSession(
            id = "call-tmp",
            phoneNumber = "+1234567890",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1000L,
            audioFilePath = tmpFile.absolutePath,
            fileSizeBytes = 4096L
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.INVALID_MEDIA)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun call_invalidMediaContent_evaluatesToInvalidMedia() {
        val badFile = tempFolder.newFile("corrupt_call.m4a")
        FileOutputStream(badFile).use { fos ->
            fos.write(ByteArray(4096) { 0x55 }) // No valid media header
        }

        val session = CallSession(
            id = "call-5",
            phoneNumber = "+1234567890",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1000L,
            audioFilePath = badFile.absolutePath,
            fileSizeBytes = 4096L
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.INVALID_MEDIA)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun call_validM4aFile_evaluatesToAvailable_andPlayAudio() {
        val file = createValidM4aFile("valid_call.m4a", size = 4096)
        val session = CallSession(
            id = "call-6",
            phoneNumber = "+1234567890",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1000L,
            audioFilePath = file.absolutePath,
            fileSizeBytes = 4096L,
            recordingQuality = RecordingQuality.UNSUPPORTED,
            captureTier = CallCaptureTier.UNSUPPORTED_USERSPACE
        )

        val vaultCall = evaluator.evaluateCall(session)
        assertThat(vaultCall.fileState).isEqualTo(VaultFileState.AVAILABLE)
        assertThat(vaultCall.primaryAction).isEqualTo(VaultPrimaryAction.PLAY_AUDIO)
        assertThat(vaultCall.availableSizeBytes).isEqualTo(4096L)
        // P1 Invariant: RecordingQuality remains strictly UNSUPPORTED
        assertThat(vaultCall.session.recordingQuality).isEqualTo(RecordingQuality.UNSUPPORTED)
    }

    // ==========================================
    // MEDIA FILE STATE EVALUATION TESTS
    // ==========================================

    @Test
    fun media_nullLocalPath_evaluatesToNoLocalFile() {
        val item = MediaItem(
            id = "media-1",
            sourceUrl = "https://example.com/video",
            title = "Test Video",
            localFilePath = null,
            downloadStatus = DownloadStatus.IDLE
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.NO_LOCAL_FILE)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
        assertThat(vaultMedia.availableSizeBytes).isEqualTo(0L)
    }

    @Test
    fun media_downloadStatusDownloading_evaluatesToNotReady() {
        val file = createValidMp4File("in_progress.mp4", size = 4096)
        val item = MediaItem(
            id = "media-2",
            sourceUrl = "https://example.com/video",
            title = "In Progress Download",
            localFilePath = file.absolutePath,
            fileSizeBytes = 4096L,
            downloadStatus = DownloadStatus.DOWNLOADING
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.NOT_READY)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
        assertThat(vaultMedia.availableSizeBytes).isEqualTo(0L)
    }

    @Test
    fun media_downloadStatusFailed_evaluatesToNotReady() {
        val file = createValidMp4File("failed_partial.mp4", size = 4096)
        val item = MediaItem(
            id = "media-failed",
            sourceUrl = "https://example.com/video",
            title = "Failed Download",
            localFilePath = file.absolutePath,
            fileSizeBytes = 4096L,
            downloadStatus = DownloadStatus.FAILED
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.NOT_READY)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
        assertThat(vaultMedia.availableSizeBytes).isEqualTo(0L)
    }

    @Test
    fun media_missingFile_evaluatesToMissing() {
        val missingPath = File(tempFolder.root, "missing_video.mp4").absolutePath
        val item = MediaItem(
            id = "media-3",
            sourceUrl = "https://example.com/video",
            title = "Missing Video",
            localFilePath = missingPath,
            downloadStatus = DownloadStatus.COMPLETED
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.MISSING)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun media_partFile_evaluatesToInvalidMedia() {
        val partFile = createValidMp4File("download.mp4.part", size = 4096)
        val item = MediaItem(
            id = "media-part",
            sourceUrl = "https://example.com/video",
            title = "Part Video",
            localFilePath = partFile.absolutePath,
            fileSizeBytes = 4096L,
            downloadStatus = DownloadStatus.COMPLETED
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.INVALID_MEDIA)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun media_tmpFile_evaluatesToInvalidMedia() {
        val tmpFile = createValidMp4File("download.mp4.tmp", size = 4096)
        val item = MediaItem(
            id = "media-tmp",
            sourceUrl = "https://example.com/video",
            title = "Tmp Video",
            localFilePath = tmpFile.absolutePath,
            fileSizeBytes = 4096L,
            downloadStatus = DownloadStatus.COMPLETED
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.INVALID_MEDIA)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun media_sizeMismatch_evaluatesToSizeMismatch() {
        val file = createValidMp4File("media_mismatch.mp4", size = 4096)
        val item = MediaItem(
            id = "media-4",
            sourceUrl = "https://example.com/video",
            title = "Mismatch Video",
            localFilePath = file.absolutePath,
            fileSizeBytes = 8192L,
            downloadStatus = DownloadStatus.COMPLETED
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.SIZE_MISMATCH)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun media_invalidContainer_evaluatesToInvalidMedia() {
        val badFile = tempFolder.newFile("corrupt_video.mp4")
        FileOutputStream(badFile).use { fos ->
            fos.write("This is plain text and not a media container".toByteArray())
            fos.write(ByteArray(2048) { 0x00 })
        }

        val item = MediaItem(
            id = "media-5",
            sourceUrl = "https://example.com/video",
            title = "Corrupt Video",
            localFilePath = badFile.absolutePath,
            fileSizeBytes = badFile.length(),
            downloadStatus = DownloadStatus.COMPLETED
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.INVALID_MEDIA)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.UNAVAILABLE)
    }

    @Test
    fun media_validVideo_evaluatesToAvailable_andPlayVideo() {
        val file = createValidMp4File("valid_video.mp4", size = 4096)
        val item = MediaItem(
            id = "media-6",
            sourceUrl = "https://example.com/video",
            title = "Valid Video",
            localFilePath = file.absolutePath,
            fileSizeBytes = 4096L,
            mediaType = MediaType.VIDEO,
            downloadStatus = DownloadStatus.COMPLETED
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.AVAILABLE)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.PLAY_VIDEO)
        assertThat(vaultMedia.availableSizeBytes).isEqualTo(4096L)
    }

    @Test
    fun media_validAudioOnly_evaluatesToAvailable_andPlayAudio() {
        val file = createValidM4aFile("valid_audio.m4a", size = 4096)
        val item = MediaItem(
            id = "media-7",
            sourceUrl = "https://example.com/audio",
            title = "Valid Audio Stream",
            localFilePath = file.absolutePath,
            fileSizeBytes = 4096L,
            mediaType = MediaType.AUDIO_ONLY,
            downloadStatus = DownloadStatus.COMPLETED
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.AVAILABLE)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.PLAY_AUDIO)
        assertThat(vaultMedia.availableSizeBytes).isEqualTo(4096L)
    }

    @Test
    fun media_availableVideoWithTranscript_retainsPrimaryPlayVideo() {
        val file = createValidMp4File("transcribed_video.mp4", size = 4096)
        val item = MediaItem(
            id = "media-8",
            sourceUrl = "https://example.com/video",
            title = "Transcribed Video",
            localFilePath = file.absolutePath,
            fileSizeBytes = 4096L,
            mediaType = MediaType.VIDEO,
            downloadStatus = DownloadStatus.COMPLETED,
            hasTranscript = true
        )

        val vaultMedia = evaluator.evaluateMedia(item)
        assertThat(vaultMedia.fileState).isEqualTo(VaultFileState.AVAILABLE)
        assertThat(vaultMedia.primaryAction).isEqualTo(VaultPrimaryAction.PLAY_VIDEO)
        assertThat(vaultMedia.hasTranscript).isTrue()
    }
}
