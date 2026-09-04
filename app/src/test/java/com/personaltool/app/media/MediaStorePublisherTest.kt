package com.personaltool.app.media

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.media.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

private class DummyContext(private val baseDir: File) : ContextWrapper(null) {
    override fun getFilesDir(): File = baseDir
    override fun getApplicationContext(): Context = this
}

class MediaStorePublisherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val publisher by lazy {
        AndroidMediaStorePublisher(
            context = DummyContext(tempFolder.root)
        )
    }

    private fun createValidFile(name: String, sizeBytes: Int = 1024): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { fos ->
            fos.write(ByteArray(sizeBytes) { 0x42 })
        }
        return file
    }

    @Test
    fun sanitizeDisplayName_normalTitle_appendsExtension() {
        val result = publisher.sanitizeDisplayName("Big Buck Bunny", "mp4")
        assertThat(result).isEqualTo("Big Buck Bunny.mp4")
    }

    @Test
    fun sanitizeDisplayName_illegalCharacters_replacedWithUnderscore() {
        val result = publisher.sanitizeDisplayName("Video: Test/Sample*Name? <Cool> | Done", "mp4")
        assertThat(result).isEqualTo("Video_ Test_Sample_Name_ _Cool_ _ Done.mp4")
    }

    @Test
    fun sanitizeDisplayName_blankTitle_fallsBackToGeneratedName() {
        val result = publisher.sanitizeDisplayName("   ", "mp4")
        assertThat(result).startsWith("Mobiltool_Media_")
        assertThat(result).endsWith(".mp4")
    }

    @Test
    fun sanitizeDisplayName_alreadyHasExtension_doesNotDuplicate() {
        val result = publisher.sanitizeDisplayName("MyVideo.mp4", "mp4")
        assertThat(result).isEqualTo("MyVideo.mp4")
    }

    @Test
    fun sanitizeDisplayName_excessiveLength_truncatedSafely() {
        val longTitle = "A".repeat(300)
        val result = publisher.sanitizeDisplayName(longTitle, "mp4")
        assertThat(result.length).isAtMost(185)
        assertThat(result).endsWith(".mp4")
    }

    @Test
    fun resolveMimeType_videoFormats() {
        assertThat(publisher.resolveMimeType(MediaType.VIDEO, "mp4", null)).isEqualTo("video/mp4")
        assertThat(publisher.resolveMimeType(MediaType.VIDEO, "webm", null)).isEqualTo("video/webm")
        assertThat(publisher.resolveMimeType(MediaType.VIDEO, "mkv", null)).isEqualTo("video/x-matroska")
        assertThat(publisher.resolveMimeType(MediaType.VIDEO, "ts", null)).isEqualTo("video/mp2t")
    }

    @Test
    fun resolveMimeType_audioFormats() {
        assertThat(publisher.resolveMimeType(MediaType.AUDIO_ONLY, "mp3", null)).isEqualTo("audio/mpeg")
        assertThat(publisher.resolveMimeType(MediaType.AUDIO_ONLY, "mp4", null)).isEqualTo("audio/mp4")
        assertThat(publisher.resolveMimeType(MediaType.AUDIO_ONLY, "m4a", null)).isEqualTo("audio/mp4")
        assertThat(publisher.resolveMimeType(MediaType.AUDIO_ONLY, "ogg", null)).isEqualTo("audio/ogg")
        assertThat(publisher.resolveMimeType(MediaType.AUDIO_ONLY, "opus", null)).isEqualTo("audio/ogg")
        assertThat(publisher.resolveMimeType(MediaType.AUDIO_ONLY, "wav", null)).isEqualTo("audio/wav")
        assertThat(publisher.resolveMimeType(MediaType.AUDIO_ONLY, "flac", null)).isEqualTo("audio/flac")
    }

    @Test
    fun resolveMimeType_unsupportedExtension_returnsNull() {
        assertThat(publisher.resolveMimeType(MediaType.VIDEO, "exe", null)).isNull()
        assertThat(publisher.resolveMimeType(MediaType.VIDEO, "html", null)).isNull()
        assertThat(publisher.resolveMimeType(MediaType.AUDIO_ONLY, "json", null)).isNull()
    }

    @Test
    fun resolveMimeType_explicitValidMimeTakesPrecedence() {
        assertThat(publisher.resolveMimeType(MediaType.VIDEO, "mp4", "video/mp4")).isEqualTo("video/mp4")
        // Generic octet-stream falls back to extension resolution
        assertThat(publisher.resolveMimeType(MediaType.VIDEO, "mp4", "application/octet-stream")).isEqualTo("video/mp4")
    }

    @Test
    fun publishMedia_missingSourceFile_failsClosedWithoutCrash() = runBlocking {
        val nonExistent = File(tempFolder.root, "does_not_exist.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = nonExistent,
            title = "Missing Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failure = result as MediaStorePublishResult.Failed
        assertThat(failure.reason).contains("missing, invalid, or empty")
    }

    @Test
    fun publishMedia_emptyZeroByteFile_failsClosed() = runBlocking {
        val emptyFile = tempFolder.newFile("empty.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = emptyFile,
            title = "Empty Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failure = result as MediaStorePublishResult.Failed
        assertThat(failure.reason).contains("missing, invalid, or empty")
    }

    @Test
    fun publishMedia_partStagingFile_failsClosed() = runBlocking {
        val partFile = createValidFile("download_123.part")
        val request = MediaStorePublishRequest(
            sourceFile = partFile,
            title = "Staging Part",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failure = result as MediaStorePublishResult.Failed
        assertThat(failure.reason).contains("incomplete staging")
        // Canonical part file is untouched
        assertThat(partFile.exists()).isTrue()
    }

    @Test
    fun publishMedia_tmpStagingFile_failsClosed() = runBlocking {
        val tmpFile = createValidFile("stream_temp.tmp")
        val request = MediaStorePublishRequest(
            sourceFile = tmpFile,
            title = "Temp File",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failure = result as MediaStorePublishResult.Failed
        assertThat(failure.reason).contains("incomplete staging")
        // Canonical temp file is untouched
        assertThat(tmpFile.exists()).isTrue()
    }

    @Test
    fun publishMedia_unsupportedExtension_failsClosed() = runBlocking {
        val invalidExtFile = createValidFile("document.pdf")
        val request = MediaStorePublishRequest(
            sourceFile = invalidExtFile,
            title = "PDF Doc",
            mediaType = MediaType.VIDEO,
            extension = "pdf"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failure = result as MediaStorePublishResult.Failed
        assertThat(failure.reason).contains("Unsupported or unknown MIME type")
    }
}
