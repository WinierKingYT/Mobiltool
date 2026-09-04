package com.personaltool.app.viewmodel

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.personaltool.app.media.MediaIntakeDownloader
import com.personaltool.app.media.MediaStorePublishRequest
import com.personaltool.app.media.MediaStorePublishResult
import com.personaltool.app.media.MediaStorePublisher
import com.personaltool.app.media.RealProbeResult
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaFormatOption
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType
import com.personaltool.core.storage.dao.MediaDao
import com.personaltool.core.storage.entity.MediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private class IntakeFakeMediaDao : MediaDao {
    val mediaFlow = MutableStateFlow<List<MediaEntity>>(emptyList())
    val insertCalls = AtomicInteger(0)

    override fun getAllMediaFlow(): Flow<List<MediaEntity>> = mediaFlow
    override suspend fun getMediaById(id: String): MediaEntity? = mediaFlow.value.find { it.id == id }
    override fun getCompletedMediaFlow(): Flow<List<MediaEntity>> =
        MutableStateFlow(mediaFlow.value.filter { it.downloadStatus == "COMPLETED" })
    override fun searchMedia(query: String): Flow<List<MediaEntity>> = MutableStateFlow(mediaFlow.value)
    override suspend fun insertMedia(item: MediaEntity) {
        insertCalls.incrementAndGet()
        mediaFlow.value = mediaFlow.value + item
    }
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

private class FakeMediaIntakeDownloader : MediaIntakeDownloader {
    val probeCalls = AtomicInteger(0)
    val downloadCalls = AtomicInteger(0)
    var probeResult: AppResult<RealProbeResult>? = null
    var downloadResult: AppResult<MediaItem>? = null
    var onDownloadStart: (suspend () -> Unit)? = null

    override suspend fun probeUrl(rawUrl: String): AppResult<RealProbeResult> {
        probeCalls.incrementAndGet()
        return probeResult ?: AppResult.Error("No probe result configured")
    }

    override suspend fun downloadUrl(
        probe: RealProbeResult,
        selectedFormat: MediaFormatOption,
        onProgress: (Int, Long) -> Unit
    ): AppResult<MediaItem> {
        downloadCalls.incrementAndGet()
        onDownloadStart?.invoke()
        return downloadResult ?: AppResult.Error("No download result configured")
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
    private val fakeDownloader = FakeMediaIntakeDownloader()
    private lateinit var testScope: CoroutineScope

    private val standardFormat = MediaFormatOption(
        formatId = "fmt_1080p",
        resolution = "1080p",
        ext = "mp4",
        note = "video/mp4",
        fileSizeBytes = 2048L,
        isAudioOnly = false
    )

    private val standardProbe = RealProbeResult(
        url = "https://example.com/test.mp4",
        title = "Sample Video",
        sourcePlatform = MediaSource.GENERIC_URL,
        contentType = "video/mp4",
        fileSizeBytes = 2048L,
        availableFormats = listOf(standardFormat)
    )

    @Before
    fun setUp() {
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    private fun createViewModel(): MediaIntakeViewModel {
        return MediaIntakeViewModel(
            application = DummyApplication(tempFolder.root),
            mediaDao = fakeDao,
            mediaStorePublisher = fakePublisher,
            downloader = fakeDownloader,
            coroutineScope = testScope
        )
    }

    @Test
    fun initialState_hasIdleGalleryPublishStatus() = runBlocking {
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value.galleryPublishStatus).isEqualTo(GalleryPublishStatus.IDLE)
        assertThat(viewModel.uiState.value.galleryPublishMessage).isNull()
    }

    @Test
    fun onUrlChanged_resetsGalleryPublishStatus() = runBlocking {
        val viewModel = createViewModel()
        viewModel.onUrlChanged("https://example.com/video.mp4")
        assertThat(viewModel.uiState.value.inputUrl).isEqualTo("https://example.com/video.mp4")
        assertThat(viewModel.uiState.value.galleryPublishStatus).isEqualTo(GalleryPublishStatus.IDLE)
    }

    @Test
    fun startDownload_orchestrationSuccess_insertsDb_publishesOnce_andSetsSavedState() = runBlocking {
        val viewModel = createViewModel()
        val canonicalFile = tempFolder.newFile("media_success.mp4")

        fakeDownloader.probeResult = AppResult.Success(standardProbe)
        fakeDownloader.downloadResult = AppResult.Success(
            MediaItem(
                id = "item_123",
                sourceUrl = standardProbe.url,
                title = standardProbe.title,
                localFilePath = canonicalFile.absolutePath,
                mediaType = MediaType.VIDEO,
                sourcePlatform = standardProbe.sourcePlatform,
                formatSelected = standardFormat.formatId,
                downloadStatus = DownloadStatus.COMPLETED
            )
        )
        fakePublisher.returnResult = MediaStorePublishResult.Success(
            contentUri = "content://media/external/video/media/101",
            displayName = "Sample Video.mp4",
            relativePath = "Movies/Mobiltool"
        )

        viewModel.onUrlChanged(standardProbe.url)
        viewModel.probeUrl()
        viewModel.selectFormat(standardFormat.formatId)
        viewModel.startDownload()

        val state = viewModel.uiState.value
        assertThat(state.downloadStatus).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(state.galleryPublishStatus).isEqualTo(GalleryPublishStatus.SAVED)
        assertThat(state.galleryPublishMessage).isEqualTo("Movies/Mobiltool/Sample Video.mp4")
        assertThat(fakeDao.insertCalls.get()).isEqualTo(1)
        assertThat(fakePublisher.publishCalls.get()).isEqualTo(1)
        assertThat(fakePublisher.lastRequest?.sourceFile?.absolutePath).isEqualTo(canonicalFile.absolutePath)
        assertThat(canonicalFile.exists()).isTrue()
    }

    @Test
    fun startDownload_orchestrationGalleryFailure_preservesDbAndCanonicalFile() = runBlocking {
        val viewModel = createViewModel()
        val canonicalFile = tempFolder.newFile("media_gallery_fail.mp4")

        fakeDownloader.probeResult = AppResult.Success(standardProbe)
        fakeDownloader.downloadResult = AppResult.Success(
            MediaItem(
                id = "item_gallery_fail",
                sourceUrl = standardProbe.url,
                title = standardProbe.title,
                localFilePath = canonicalFile.absolutePath,
                mediaType = MediaType.VIDEO,
                sourcePlatform = standardProbe.sourcePlatform,
                formatSelected = standardFormat.formatId,
                downloadStatus = DownloadStatus.COMPLETED
            )
        )
        fakePublisher.returnResult = MediaStorePublishResult.Failed("Disk full error")

        viewModel.onUrlChanged(standardProbe.url)
        viewModel.probeUrl()
        viewModel.selectFormat(standardFormat.formatId)
        viewModel.startDownload()

        val state = viewModel.uiState.value
        assertThat(state.downloadStatus).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(state.galleryPublishStatus).isEqualTo(GalleryPublishStatus.FAILED)
        assertThat(state.galleryPublishMessage).isEqualTo("Disk full error")
        // DB record exists and canonical internal file is untouched
        assertThat(fakeDao.insertCalls.get()).isEqualTo(1)
        assertThat(fakeDao.mediaFlow.value).hasSize(1)
        assertThat(canonicalFile.exists()).isTrue()
    }

    @Test
    fun startDownload_orchestrationUnsupportedApi_preservesDbAndReportsUnsupported() = runBlocking {
        val viewModel = createViewModel()
        val canonicalFile = tempFolder.newFile("media_unsupported.mp4")

        fakeDownloader.probeResult = AppResult.Success(standardProbe)
        fakeDownloader.downloadResult = AppResult.Success(
            MediaItem(
                id = "item_unsupported",
                sourceUrl = standardProbe.url,
                title = standardProbe.title,
                localFilePath = canonicalFile.absolutePath,
                mediaType = MediaType.VIDEO,
                sourcePlatform = standardProbe.sourcePlatform,
                formatSelected = standardFormat.formatId,
                downloadStatus = DownloadStatus.COMPLETED
            )
        )
        fakePublisher.returnResult = MediaStorePublishResult.Unsupported("Requires API 29+")

        viewModel.onUrlChanged(standardProbe.url)
        viewModel.probeUrl()
        viewModel.selectFormat(standardFormat.formatId)
        viewModel.startDownload()

        val state = viewModel.uiState.value
        assertThat(state.downloadStatus).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(state.galleryPublishStatus).isEqualTo(GalleryPublishStatus.UNSUPPORTED)
        assertThat(fakeDao.insertCalls.get()).isEqualTo(1)
        assertThat(canonicalFile.exists()).isTrue()
    }

    @Test
    fun startDownload_downloadFailure_doesNotInsertDbOrPublish() = runBlocking {
        val viewModel = createViewModel()

        fakeDownloader.probeResult = AppResult.Success(standardProbe)
        fakeDownloader.downloadResult = AppResult.Error("Network socket closed")

        viewModel.onUrlChanged(standardProbe.url)
        viewModel.probeUrl()
        viewModel.selectFormat(standardFormat.formatId)
        viewModel.startDownload()

        val state = viewModel.uiState.value
        assertThat(state.downloadStatus).isEqualTo(DownloadStatus.FAILED)
        assertThat(state.errorMessage).isEqualTo("Network socket closed")
        assertThat(fakeDao.insertCalls.get()).isEqualTo(0)
        assertThat(fakePublisher.publishCalls.get()).isEqualTo(0)
    }

    @Test
    fun startDownload_duplicateInvocation_isGuardedAndExecutesOnlyOnce() = runBlocking {
        val viewModel = createViewModel()
        val canonicalFile = tempFolder.newFile("media_concurrent.mp4")

        val pauseDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
        fakeDownloader.probeResult = AppResult.Success(standardProbe)
        fakeDownloader.onDownloadStart = {
            pauseDeferred.await()
        }
        fakeDownloader.downloadResult = AppResult.Success(
            MediaItem(
                id = "item_concurrent",
                sourceUrl = standardProbe.url,
                title = standardProbe.title,
                localFilePath = canonicalFile.absolutePath,
                mediaType = MediaType.VIDEO,
                sourcePlatform = standardProbe.sourcePlatform,
                formatSelected = standardFormat.formatId,
                downloadStatus = DownloadStatus.COMPLETED
            )
        )

        viewModel.onUrlChanged(standardProbe.url)
        viewModel.probeUrl()
        viewModel.selectFormat(standardFormat.formatId)

        // First call starts and pauses inside onDownloadStart
        viewModel.startDownload()
        assertThat(viewModel.uiState.value.downloadStatus).isEqualTo(DownloadStatus.DOWNLOADING)

        // Second duplicate call while first is in-flight is guarded
        viewModel.startDownload()
        assertThat(fakeDownloader.downloadCalls.get()).isEqualTo(1)

        // Release the pause so first download completes
        pauseDeferred.complete(Unit)

        assertThat(viewModel.uiState.value.downloadStatus).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(fakeDownloader.downloadCalls.get()).isEqualTo(1)
        assertThat(fakeDao.insertCalls.get()).isEqualTo(1)
        assertThat(fakePublisher.publishCalls.get()).isEqualTo(1)
    }
}
