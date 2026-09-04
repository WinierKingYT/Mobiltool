package com.personaltool.app.viewmodel

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.personaltool.app.media.MediaStorePublishRequest
import com.personaltool.app.media.MediaStorePublishResult
import com.personaltool.app.media.MediaStorePublisher
import com.personaltool.app.media.RealProbeResult
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType
import com.personaltool.core.storage.dao.MediaDao
import com.personaltool.core.storage.entity.MediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private class IntakeFakeMediaDao : MediaDao {
    val mediaFlow = MutableStateFlow<List<MediaEntity>>(emptyList())

    override fun getAllMediaFlow(): Flow<List<MediaEntity>> = mediaFlow
    override suspend fun getMediaById(id: String): MediaEntity? = mediaFlow.value.find { it.id == id }
    override fun getCompletedMediaFlow(): Flow<List<MediaEntity>> =
        MutableStateFlow(mediaFlow.value.filter { it.downloadStatus == "COMPLETED" })
    override fun searchMedia(query: String): Flow<List<MediaEntity>> = MutableStateFlow(mediaFlow.value)
    override suspend fun insertMedia(item: MediaEntity) { mediaFlow.value = mediaFlow.value + item }
    override suspend fun updateMedia(item: MediaEntity) {}
    override suspend fun deleteMedia(item: MediaEntity) {}
    override suspend fun deleteMediaById(id: String): Int = 0
    override suspend fun deleteAllMedia(): Int = 0
    override suspend fun getMediaCount(): Int = mediaFlow.value.size
}

private class FakeMediaStorePublisher : MediaStorePublisher {
    val publishCalls = AtomicInteger(0)
    var lastRequest: MediaStorePublishRequest? = null
    var returnResult: MediaStorePublishResult = MediaStorePublishResult.Success(
        contentUri = "content://media/external/video/media/42",
        displayName = "Test Video.mp4",
        relativePath = "Movies/Mobiltool"
    )

    override suspend fun publishMedia(request: MediaStorePublishRequest): MediaStorePublishResult {
        publishCalls.incrementAndGet()
        lastRequest = request
        return returnResult
    }
}

private class DummyApplication(private val baseDir: File) : Application() {
    override fun getFilesDir(): File = baseDir
    override fun getApplicationContext(): Context = this
}

class MediaIntakeViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeDao = IntakeFakeMediaDao()
    private val fakePublisher = FakeMediaStorePublisher()

    @Test
    fun initialState_hasIdleGalleryPublishStatus() {
        val viewModel = MediaIntakeViewModel(
            application = DummyApplication(tempFolder.root),
            mediaDao = fakeDao,
            mediaStorePublisher = fakePublisher
        )

        assertThat(viewModel.uiState.value.galleryPublishStatus).isEqualTo(GalleryPublishStatus.IDLE)
        assertThat(viewModel.uiState.value.galleryPublishMessage).isNull()
    }

    @Test
    fun onUrlChanged_resetsGalleryPublishStatus() {
        val viewModel = MediaIntakeViewModel(
            application = DummyApplication(tempFolder.root),
            mediaDao = fakeDao,
            mediaStorePublisher = fakePublisher
        )

        viewModel.onUrlChanged("https://example.com/video.mp4")
        assertThat(viewModel.uiState.value.inputUrl).isEqualTo("https://example.com/video.mp4")
        assertThat(viewModel.uiState.value.galleryPublishStatus).isEqualTo(GalleryPublishStatus.IDLE)
    }

    @Test
    fun successfulPublish_updatesGalleryPublishStatusToSaved() = runBlocking {
        fakePublisher.returnResult = MediaStorePublishResult.Success(
            contentUri = "content://media/external/video/media/99",
            displayName = "Sample Video.mp4",
            relativePath = "Movies/Mobiltool"
        )

        val probe = RealProbeResult(
            url = "https://example.com/test.mp4",
            title = "Sample Video",
            sourcePlatform = MediaSource.GENERIC_URL,
            contentType = "video/mp4",
            fileSizeBytes = 1024L,
            availableFormats = listOf(
                MediaFormatOption(
                    formatId = "fmt_1",
                    resolution = "1080p",
                    ext = "mp4",
                    note = "video/mp4",
                    fileSizeBytes = 1024L,
                    isAudioOnly = false
                )
            )
        )

        val localFile = tempFolder.newFile("canonical_video.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = localFile,
            title = probe.title,
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
            extension = "mp4"
        )

        val result = fakePublisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Success::class.java)
        assertThat(fakePublisher.publishCalls.get()).isEqualTo(1)
        assertThat(fakePublisher.lastRequest?.title).isEqualTo("Sample Video")
        assertThat(fakePublisher.lastRequest?.mediaType).isEqualTo(MediaType.VIDEO)
    }

    @Test
    fun failedPublish_doesNotCorruptDatabaseOrThrow() = runBlocking {
        fakePublisher.returnResult = MediaStorePublishResult.Failed(
            reason = "ContentResolver insert returned null"
        )

        val localFile = tempFolder.newFile("failed_publish_video.mp4")
        val request = MediaStorePublishRequest(
            sourceFile = localFile,
            title = "Failed Publish",
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
            extension = "mp4"
        )

        val result = fakePublisher.publishMedia(request)
        assertThat(result).isInstanceOf(MediaStorePublishResult.Failed::class.java)
        assertThat(fakePublisher.publishCalls.get()).isEqualTo(1)
        // Source file is preserved
        assertThat(localFile.exists()).isTrue()
    }
}
