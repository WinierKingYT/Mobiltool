package com.personaltool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaType
import com.personaltool.core.storage.dao.MediaDao
import com.personaltool.core.storage.entity.MediaEntity
import com.personaltool.media.extractor.api.DefaultMediaExtractor
import com.personaltool.media.extractor.api.DownloadProgress
import com.personaltool.media.extractor.api.DownloadRequest
import com.personaltool.media.extractor.api.MediaExtractor
import com.personaltool.media.extractor.api.MediaProbeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class MediaIntakeUiState(
    val inputUrl: String = "",
    val isProbing: Boolean = false,
    val probeResult: MediaProbeResult? = null,
    val selectedFormatId: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.IDLE,
    val downloadProgressPercent: Int = 0,
    val errorMessage: String? = null,
    val lastDownloadedItem: MediaItem? = null
)

class MediaIntakeViewModel(
    private val mediaDao: MediaDao,
    private val mediaExtractor: MediaExtractor = DefaultMediaExtractor()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaIntakeUiState())
    val uiState: StateFlow<MediaIntakeUiState> = _uiState.asStateFlow()

    fun onUrlChanged(url: String) {
        _uiState.update {
            it.copy(
                inputUrl = url,
                errorMessage = null
            )
        }
    }

    fun probeUrl() {
        val url = _uiState.value.inputUrl
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true, errorMessage = null) }
            when (val result = mediaExtractor.probeUrl(url)) {
                is AppResult.Success -> {
                    val probe = result.data
                    val defaultFormat = probe.availableFormats.firstOrNull { !it.isAudioOnly }?.formatId
                        ?: probe.availableFormats.firstOrNull()?.formatId

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
                            errorMessage = result.message
                        )
                    }
                }
                AppResult.Loading -> {}
            }
        }
    }

    fun selectFormat(formatId: String) {
        _uiState.update { it.copy(selectedFormatId = formatId) }
    }

    fun startDownload(outputDirectory: File) {
        val state = _uiState.value
        val probe = state.probeResult ?: return
        val formatId = state.selectedFormatId ?: return

        val downloadId = UUID.randomUUID().toString()
        val formatOption = probe.availableFormats.find { it.formatId == formatId }
        val isAudioOnly = formatOption?.isAudioOnly == true
        val ext = formatOption?.ext ?: if (isAudioOnly) "m4a" else "mp4"

        val targetFile = File(outputDirectory, "media-$downloadId.$ext")

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloadStatus = DownloadStatus.DOWNLOADING,
                    downloadProgressPercent = 0,
                    errorMessage = null
                )
            }

            val request = DownloadRequest(
                id = downloadId,
                sourceUrl = probe.url,
                formatId = formatId,
                destinationPath = targetFile.absolutePath,
                targetType = if (isAudioOnly) MediaType.AUDIO_ONLY else MediaType.VIDEO
            )

            when (val result = mediaExtractor.downloadMedia(request) { progress: DownloadProgress ->
                _uiState.update { it.copy(downloadProgressPercent = progress.percent) }
            }) {
                is AppResult.Success -> {
                    val downloaded = result.data
                    val mediaItem = MediaItem(
                        id = downloaded.downloadId,
                        sourceUrl = probe.url,
                        title = probe.title,
                        uploader = probe.uploader,
                        durationMs = downloaded.durationMs,
                        localFilePath = downloaded.outputFilePath,
                        thumbnailPath = probe.thumbnailUrl,
                        mediaType = if (isAudioOnly) MediaType.AUDIO_ONLY else MediaType.VIDEO,
                        sourcePlatform = probe.sourcePlatform,
                        formatSelected = formatId,
                        resolution = formatOption?.resolution,
                        fileSizeBytes = downloaded.fileSizeBytes,
                        downloadStatus = DownloadStatus.COMPLETED,
                        downloadProgressPercent = 100,
                        hasTranscript = false,
                        isFavorite = false
                    )

                    // Commit to Room DB
                    mediaDao.insertMedia(MediaEntity.fromDomain(mediaItem))

                    _uiState.update {
                        it.copy(
                            downloadStatus = DownloadStatus.COMPLETED,
                            downloadProgressPercent = 100,
                            lastDownloadedItem = mediaItem
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(
                            downloadStatus = DownloadStatus.FAILED,
                            errorMessage = result.message
                        )
                    }
                }
                AppResult.Loading -> {}
            }
        }
    }
}
