package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.call.RecordingQuality
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class AudioFileInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun nonExistentFile_returnsInvalidCorrupt() {
        val nonExistentPath = File(tempFolder.root, "does_not_exist.m4a").absolutePath
        val result = AudioFileInspector.inspectRecordedFile(nonExistentPath, RecordingQuality.VERIFIED_BIDIRECTIONAL)

        assertThat(result.isValid).isFalse()
        assertThat(result.determinedQuality).isEqualTo(RecordingQuality.CORRUPT)
        assertThat(result.rejectionReason).contains("does not exist")
    }

    @Test
    fun fileUnder2048Bytes_returnsInvalidCorrupt() {
        val smallFile = tempFolder.newFile("small.m4a")
        FileOutputStream(smallFile).use { fos ->
            fos.write(ByteArray(512) { 0 })
        }

        val result = AudioFileInspector.inspectRecordedFile(smallFile.absolutePath, RecordingQuality.VERIFIED_BIDIRECTIONAL)

        assertThat(result.isValid).isFalse()
        assertThat(result.determinedQuality).isEqualTo(RecordingQuality.CORRUPT)
        assertThat(result.rejectionReason).contains("below minimum threshold")
    }

    @Test
    fun fileWithoutFtypAtom_returnsInvalidCorrupt() {
        val dummyFile = tempFolder.newFile("corrupt.m4a")
        FileOutputStream(dummyFile).use { fos ->
            fos.write("RIFFxxxxWAVEfmt ".toByteArray())
            fos.write(ByteArray(3000) { 0 })
        }

        val result = AudioFileInspector.inspectRecordedFile(dummyFile.absolutePath, RecordingQuality.VERIFIED_BIDIRECTIONAL)

        assertThat(result.isValid).isFalse()
        assertThat(result.determinedQuality).isEqualTo(RecordingQuality.CORRUPT)
        assertThat(result.rejectionReason).contains("ftyp header signature")
    }

    @Test
    fun validFtypHeader_passesContainerCheck() {
        val validM4a = tempFolder.newFile("header_test.m4a")
        FileOutputStream(validM4a).use { fos ->
            val header = byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
            fos.write(header)
            fos.write(ByteArray(4096) { 0x11 })
        }

        val isHeaderValid = AudioFileInspector.isValidM4AContainerHeader(validM4a)
        assertThat(isHeaderValid).isTrue()
    }

    @Test
    fun unreadableFile_failsGracefully() {
        val badPath = "C:\\invalid\\path\\that\\does\\not\\exist\\call.m4a"
        val result = AudioFileInspector.inspectRecordedFile(
            filePath = badPath,
            defaultQuality = RecordingQuality.VERIFIED_BIDIRECTIONAL,
            captureTier = com.personaltool.core.model.call.CallCaptureTier.PRIVILEGED_DIRECT
        )
        assertThat(result.isValid).isFalse()
        assertThat(result.determinedQuality).isEqualTo(RecordingQuality.CORRUPT)
    }
}
