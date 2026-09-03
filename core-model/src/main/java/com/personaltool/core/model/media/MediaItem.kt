package com.personaltool.core.model.media

enum class MediaType {
    VIDEO,
    AUDIO_ONLY
}

enum class MediaSource {
    YOUTUBE,
    INSTAGRAM,
    X_TWITTER,
    LOCAL_IMPORT,
    GENERIC_URL
}

enum class DownloadStatus {
    IDLE,
    PROBING,
    QUEUED,
    DOWNLOADING,
    POSTPROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MediaFormatOption(
    val formatId: String,
    val ext: String? = null,
    val resolution: String? = null,
    val note: String? = null,
    val fileSizeBytes: Long? = null,
    val isAudioOnly: Boolean = false,
    val videoCodec: String? = null,
    val audioCodec: String? = null
)

data class MediaItem(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val uploader: String? = null,
    val durationMs: Long = 0L,
    val localFilePath: String? = null,
    val thumbnailPath: String? = null,
    val mediaType: MediaType = MediaType.VIDEO,
    val sourcePlatform: MediaSource = MediaSource.GENERIC_URL,
    val formatSelected: String? = null,
    val resolution: String? = null,
    val fileSizeBytes: Long = 0L,
    val downloadStatus: DownloadStatus = DownloadStatus.IDLE,
    val downloadProgressPercent: Int = 0,
    val hasTranscript: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
