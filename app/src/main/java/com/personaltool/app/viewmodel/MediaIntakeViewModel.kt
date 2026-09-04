package com.personaltool.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.app.media.AndroidMediaStorePublisher
import com.personaltool.app.media.MediaIntakeDownloader
import com.personaltool.app.media.MediaStorePublishRequest
import com.personaltool.app.media.MediaStorePublishResult
import com.personaltool.app.media.MediaStorePublisher
import com.personaltool.app.media.RealHttpMediaDownloader
import com.personaltool.app.media.RealProbeResult
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.storage.dao.MediaDao
import com.personaltool.core.storage.entity.MediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class GalleryPublishStatus {
    IDLE,
    PUBLISHING,
    SAVED,
    FAILED,
    UNSUPPORTED
}

data class MediaIntakeUiState(
    val inputUrl: String = "",
    val isProbing: Boolean = false,
    val probeResult: RealProbeResult? = null,
    val selectedFormatId: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.IDLE,
    val downloadProgressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val errorMessage: String? = null,
    val activePlayingVideoPath: String? = null,
    val activePlayingVideoTitle: String? = null,
    val galleryPublishStatus: GalleryPublishStatus = GalleryPublishStatus.IDLE,
    val galleryPublishMessage: String? = null
)

class MediaIntakeViewModel(
    application: Application,
    private val mediaDao: MediaDao,
    private val mediaStorePublisher: MediaStorePublisher =
        AndroidMediaStorePublisher(application.applicationContext),
    private val downloader: MediaIntakeDownloader =
        RealHttpMediaDownloader(application.applicationContext),
    coroutineScope: CoroutineScope? = null
) : AndroidViewModel(application) {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(MediaIntakeUiState())
    val uiState: StateFlow<MediaIntakeUiState> = _uiState.asStateFlow()

    private var activeDownloadJob: Job? = null

    val libraryItems: StateFlow<List<MediaItem>> = mediaDao.getAllMediaFlow()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onUrlChanged(url: String) {
        _uiState.update {
            it.copy(
                inputUrl = url,
                errorMessage = null,
                galleryPublishStatus = GalleryPublishStatus.IDLE,
                galleryPublishMessage = null
            )
        }
    }

    fun probeUrl() {
        val url = _uiState.value.inputUrl
        if (url.isBlank()) return

        _uiState.update {
            it.copy(
                isProbing = true,
                errorMessage = null,
                galleryPublishStatus = GalleryPublishStatus.IDLE,
                galleryPublishMessage = null
            )
        }

        scope.launch {
            when (val result = downloader.probeUrl(url)) {
                is AppResult.Success -> {
                    val probe = result.data
                    val defaultFormat = probe.availableFormats.firstOrNull()?.formatId

                    _uiState.update {
                        it.copy(
                            isProbing = false,
                            probeResult = probe,
                            selectedFormatId = defaultFormat,
                            downloadStatus = DownloadStatus.IDLE,
                            downloadProgressPercent = 0
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isProbing = false,
                            errorMessage = formatUserFriendlyErrorMessage(result.message)
                        )
                    }
                }
                AppResult.Loading -> {}
            }
        }
    }

    fun selectFormat(formatId: String) {
        _uiState.update {
            it.copy(
                selectedFormatId = formatId,
                galleryPublishStatus = GalleryPublishStatus.IDLE,
                galleryPublishMessage = null
            )
        }
    }

    fun startDownload() {
        if (activeDownloadJob?.isActive == true || _uiState.value.downloadStatus == DownloadStatus.DOWNLOADING) {
            return
        }

        val state = _uiState.value
        val probe = state.probeResult ?: return
        val formatId = state.selectedFormatId ?: return
        val formatOption = probe.availableFormats.find { it.formatId == formatId } ?: return

        _uiState.update {
            it.copy(
                downloadStatus = DownloadStatus.DOWNLOADING,
                downloadProgressPercent = 0,
                downloadedBytes = 0L,
                errorMessage = null,
                galleryPublishStatus = GalleryPublishStatus.IDLE,
                galleryPublishMessage = null
            )
        }

        activeDownloadJob = scope.launch {
            when (val result = downloader.downloadUrl(probe, formatOption) { percent, bytes ->
                _uiState.update {
                    it.copy(
                        downloadProgressPercent = percent,
                        downloadedBytes = bytes
                    )
                }
            }) {
                is AppResult.Success -> {
                    val completed = result.data
                    mediaDao.insertMedia(MediaEntity.fromDomain(completed))

                    // FIX 04: Intermediate PUBLISHING state
                    _uiState.update {
                        it.copy(
                            downloadStatus = DownloadStatus.COMPLETED,
                            downloadProgressPercent = 100,
                            galleryPublishStatus = GalleryPublishStatus.PUBLISHING,
                            galleryPublishMessage = null
                        )
                    }

                    // Auto-publish to MediaStore (shared Gallery storage)
                    var publishResult: MediaStorePublishResult = MediaStorePublishResult.Skipped

                    val localPath = completed.localFilePath
                    if (completed.downloadStatus == DownloadStatus.COMPLETED && !localPath.isNullOrBlank()) {
                        val localFile = File(localPath)
                        val publishRequest = MediaStorePublishRequest(
                            sourceFile = localFile,
                            title = completed.title,
                            mediaType = completed.mediaType,
                            mimeType = formatOption.note,
                            extension = formatOption.ext ?: localFile.extension
                        )
                        publishResult = mediaStorePublisher.publishMedia(publishRequest)
                    }

                    _uiState.update {
                        it.copy(
                            downloadStatus = DownloadStatus.COMPLETED,
                            downloadProgressPercent = 100,
                            galleryPublishStatus = when (publishResult) {
                                is MediaStorePublishResult.Success -> GalleryPublishStatus.SAVED
                                is MediaStorePublishResult.Failed -> GalleryPublishStatus.FAILED
                                is MediaStorePublishResult.Unsupported -> GalleryPublishStatus.UNSUPPORTED
                                MediaStorePublishResult.Skipped -> GalleryPublishStatus.IDLE
                            },
                            galleryPublishMessage = when (publishResult) {
                                is MediaStorePublishResult.Success ->
                                    "${publishResult.relativePath}/${publishResult.displayName}"
                                is MediaStorePublishResult.Failed ->
                                    publishResult.reason
                                is MediaStorePublishResult.Unsupported ->
                                    publishResult.reason
                                MediaStorePublishResult.Skipped -> null
                            },
                            inputUrl = ""
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(
                            downloadStatus = DownloadStatus.FAILED,
                            errorMessage = formatUserFriendlyErrorMessage(result.message),
                            galleryPublishStatus = GalleryPublishStatus.IDLE,
                            galleryPublishMessage = null
                        )
                    }
                }
                AppResult.Loading -> {}
            }
        }
    }

    private fun formatUserFriendlyErrorMessage(rawMessage: String): String {
        return when {
            rawMessage.contains("YouTube", ignoreCase = true) ->
                "YouTube bağlantısı çözümlenemedi. Bağlantıyı kontrol edin veya tekrar deneyin."
            rawMessage.contains("Instagram", ignoreCase = true) ->
                "Instagram platformundan medya indirme şu anda desteklenmiyor."
            rawMessage.contains("Twitter", ignoreCase = true) || rawMessage.contains("X_TWITTER", ignoreCase = true) ->
                "X (Twitter) platformundan medya indirme şu anda desteklenmiyor."
            rawMessage.startsWith("PLATFORM_EXTRACTION_UNAVAILABLE") ->
                "Medya akışı çözümlenemedi. Bağlantıyı kontrol edin veya tekrar deneyin."
            else -> rawMessage
        }
    }

    fun openVideoViewer(item: MediaItem) {
        val path = item.localFilePath ?: return
        _uiState.update {
            it.copy(
                activePlayingVideoPath = path,
                activePlayingVideoTitle = item.title
            )
        }
    }

    fun closeVideoViewer() {
        _uiState.update {
            it.copy(
                activePlayingVideoPath = null,
                activePlayingVideoTitle = null
            )
        }
    }

    fun deleteMediaItem(id: String) {
        scope.launch {
            mediaDao.deleteMediaById(id)
        }
    }
}
