package com.personaltool.app.media

import android.content.ContentValues
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.media.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

private class FakeMediaStoreContentGateway(
    override var sdkInt: Int = 34
) : MediaStoreContentGateway {
    val defaultUri = "content://media/external/video/media/101"
    var insertResult: String? = defaultUri
    var lastInsertedMediaType: MediaType? = null
    var lastInsertedDisplayName: String? = null
    var lastInsertedMimeType: String? = null
    var lastInsertedRelativePath: String? = null

    var shouldFailOpenOutputStream = false
    var shouldThrowOnCopy = false
    var outputStreamBytes = ByteArrayOutputStream()
    var updateResult: Int = 1
    var updateThrows = false
    val deletedUris = mutableListOf<String>()

    override fun insertPendingMedia(
        mediaType: MediaType,
        displayName: String,
        mimeType: String,
        relativePath: String
    ): String? {
        lastInsertedMediaType = mediaType
        lastInsertedDisplayName = displayName
        lastInsertedMimeType = mimeType
        lastInsertedRelativePath = relativePath
        return insertResult
    }

    override fun openOutputStream(contentUri: String, mode: String): OutputStream? {
        if (shouldFailOpenOutputStream) return null
        if (shouldThrowOnCopy) {
            return object : OutputStream() {
                override fun write(b: Int) {
                    throw IOException("Simulated disk write error")
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    throw IOException("Simulated disk write error")
                }
            }
        }
        return outputStreamBytes
    }

    override fun finalizePending(contentUri: String): Int {
        if (updateThrows) throw IllegalStateException("Simulated database update failure")
        return updateResult
    }

    override fun deleteMedia(contentUri: String): Int {
        deletedUris.add(contentUri)
        return 1
    }
}

class MediaStorePublisherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeGateway = FakeMediaStoreContentGateway(sdkInt = 34)
    private val publisher = AndroidMediaStorePublisher(gateway = fakeGateway)

    private fun createValidFile(name: String, sizeBytes: Int = 1024): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { fos ->
            fos.write(ByteArray(sizeBytes) { 0x42 })
        }
        return file
    }

    @Test
    fun api26_to_28_returnsUnsupported_andDoesNotInvokeInsert() = runBlocking {
        fakeGateway.sdkInt = 28
        val source = createValidFile("video_api28.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = source,
            title = "API 28 Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Unsupported::class.java)
        val unsupported = result as MediaStorePublishResult.Unsupported
        assertThat(unsupported.reason).contains("Android 10 (API 29) or higher")
        assertThat(fakeGateway.lastInsertedMediaType).isNull()
        assertThat(source.exists()).isTrue()
    }

    @Test
    fun insertNull_failsClosedWithoutCrash() = runBlocking {
        fakeGateway.sdkInt = 34
        fakeGateway.insertResult = null
        val source = createValidFile("video_insert_null.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = source,
            title = "Insert Null Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failed = result as MediaStorePublishResult.Failed
        assertThat(failed.reason).contains("ContentResolver insert returned null")
        assertThat(source.exists()).isTrue()
    }

    @Test
    fun openOutputStreamNull_failsClosed_andDeletesPendingRow() = runBlocking {
        fakeGateway.sdkInt = 34
        fakeGateway.shouldFailOpenOutputStream = true
        val source = createValidFile("video_null_stream.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = source,
            title = "Null Stream Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failed = result as MediaStorePublishResult.Failed
        assertThat(failed.reason).contains("Failed to open output stream")
        assertThat(fakeGateway.deletedUris).contains(fakeGateway.defaultUri)
        assertThat(source.exists()).isTrue()
    }

    @Test
    fun copyThrows_failsClosed_andDeletesPendingRow() = runBlocking {
        fakeGateway.sdkInt = 34
        fakeGateway.shouldThrowOnCopy = true
        val source = createValidFile("video_throw_stream.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = source,
            title = "Throw Stream Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failed = result as MediaStorePublishResult.Failed
        assertThat(failed.reason).contains("Simulated disk write error")
        assertThat(fakeGateway.deletedUris).contains(fakeGateway.defaultUri)
        assertThat(source.exists()).isTrue()
    }

    @Test
    fun finalizeUpdateThrows_failsClosed_andDeletesPendingRow() = runBlocking {
        fakeGateway.sdkInt = 34
        fakeGateway.updateThrows = true
        val source = createValidFile("video_update_throws.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = source,
            title = "Update Throws Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failed = result as MediaStorePublishResult.Failed
        assertThat(failed.reason).contains("Simulated database update failure")
        assertThat(fakeGateway.deletedUris).contains(fakeGateway.defaultUri)
        assertThat(source.exists()).isTrue()
    }

    @Test
    fun finalizeUpdateReturnsZeroRows_failsClosed_andDeletesPendingRow() = runBlocking {
        fakeGateway.sdkInt = 34
        fakeGateway.updateResult = 0
        val source = createValidFile("video_update_zero.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = source,
            title = "Zero Rows Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        val failed = result as MediaStorePublishResult.Failed
        assertThat(failed.reason).contains("could not be finalized: update IS_PENDING=0 returned 0 rows")
        assertThat(fakeGateway.deletedUris).contains(fakeGateway.defaultUri)
        assertThat(source.exists()).isTrue()
    }

    @Test
    fun finalizeUpdateReturnsPositiveRows_succeedsWithPublishedResult() = runBlocking {
        fakeGateway.sdkInt = 34
        fakeGateway.updateResult = 1
        val source = createValidFile("video_success.mp4", sizeBytes = 2048)
        val request = MediaStorePublishRequest(
            sourceFile = source,
            title = "Success Video",
            mediaType = MediaType.VIDEO,
            extension = "mp4"
        )

        val result = publisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Success::class.java)
        val success = result as MediaStorePublishResult.Success
        assertThat(success.displayName).isEqualTo("Success Video.mp4")
        assertThat(success.relativePath).isEqualTo("Movies/Mobiltool")
        assertThat(fakeGateway.outputStreamBytes.size()).isEqualTo(2048)
        assertThat(fakeGateway.deletedUris).isEmpty()
        assertThat(source.exists()).isTrue()
    }

    @Test
    fun mimeFamilyConsistency_videoWithVideoMime_accepted() {
        val mime = publisher.resolveMimeType(MediaType.VIDEO, "mp4", "video/mp4")
        assertThat(mime).isEqualTo("video/mp4")
    }

    @Test
    fun mimeFamilyConsistency_audioWithAudioMime_accepted() {
        val mime = publisher.resolveMimeType(MediaType.AUDIO_ONLY, "mp4", "audio/mp4")
        assertThat(mime).isEqualTo("audio/mp4")
    }

    @Test
    fun mimeFamilyConsistency_videoWithAudioMime_ignoresMime_andResolvesVideoExt() {
        val mime = publisher.resolveMimeType(MediaType.VIDEO, "mp4", "audio/mp4")
        assertThat(mime).isEqualTo("video/mp4")
    }

    @Test
    fun mimeFamilyConsistency_audioWithVideoMime_ignoresMime_andResolvesAudioExt() {
        val mime = publisher.resolveMimeType(MediaType.AUDIO_ONLY, "mp4", "video/mp4")
        assertThat(mime).isEqualTo("audio/mp4")
    }

    @Test
    fun mimeFamilyConsistency_videoWithAudioOnlyExtension_failsClosed() {
        val mime = publisher.resolveMimeType(MediaType.VIDEO, "mp3", "audio/mpeg")
        assertThat(mime).isNull()
    }

    @Test
    fun mimeFamilyConsistency_audioWithVideoOnlyExtension_failsClosed() {
        val mime = publisher.resolveMimeType(MediaType.AUDIO_ONLY, "mkv", "video/x-matroska")
        assertThat(mime).isNull()
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
        assertThat(failure.reason).contains("Unsupported or inconsistent MIME type")
    }
}
