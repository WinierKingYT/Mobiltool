package com.personaltool.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val title: String,
    val uploader: String?,
    val durationMs: Long,
    val localFilePath: String?,
    val thumbnailPath: String?,
    val mediaType: String,
    val sourcePlatform: String,
    val formatSelected: String?,
    val resolution: String?,
    val fileSizeBytes: Long,
    val downloadStatus: String,
    val downloadProgressPercent: Int,
    val hasTranscript: Boolean,
    val isFavorite: Boolean,
    val createdAt: Long
) {
    fun toDomain(): MediaItem = MediaItem(
        id = id,
        sourceUrl = sourceUrl,
        title = title,
        uploader = uploader,
        durationMs = durationMs,
        localFilePath = localFilePath,
        thumbnailPath = thumbnailPath,
        mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.VIDEO),
        sourcePlatform = runCatching { MediaSource.valueOf(sourcePlatform) }.getOrDefault(MediaSource.GENERIC_URL),
        formatSelected = formatSelected,
        resolution = resolution,
        fileSizeBytes = fileSizeBytes,
        downloadStatus = runCatching { DownloadStatus.valueOf(downloadStatus) }.getOrDefault(DownloadStatus.IDLE),
        downloadProgressPercent = downloadProgressPercent,
        hasTranscript = hasTranscript,
        isFavorite = isFavorite,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(item: MediaItem): MediaEntity = MediaEntity(
            id = item.id,
            sourceUrl = item.sourceUrl,
            title = item.title,
            uploader = item.uploader,
            durationMs = item.durationMs,
            localFilePath = item.localFilePath,
            thumbnailPath = item.thumbnailPath,
            mediaType = item.mediaType.name,
            sourcePlatform = item.sourcePlatform.name,
            formatSelected = item.formatSelected,
            resolution = item.resolution,
            fileSizeBytes = item.fileSizeBytes,
            downloadStatus = item.downloadStatus.name,
            downloadProgressPercent = item.downloadProgressPercent,
            hasTranscript = item.hasTranscript,
            isFavorite = item.isFavorite,
            createdAt = item.createdAt
        )
    }
}
