package com.personaltool.app.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.personaltool.core.model.media.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

data class MediaStorePublishRequest(
    val sourceFile: File,
    val title: String,
    val mediaType: MediaType,
    val mimeType: String? = null,
    val extension: String? = null
)

sealed interface MediaStorePublishResult {
    data class Success(
        val contentUri: String,
        val displayName: String,
        val relativePath: String
    ) : MediaStorePublishResult

    data class Failed(
        val reason: String,
        val cause: Throwable? = null
    ) : MediaStorePublishResult

    data class Unsupported(
        val reason: String
    ) : MediaStorePublishResult

    data object Skipped : MediaStorePublishResult
}

interface MediaStorePublisher {
    suspend fun publishMedia(request: MediaStorePublishRequest): MediaStorePublishResult
}

interface MediaStoreContentGateway {
    val sdkInt: Int
    fun insertPendingMedia(
        mediaType: MediaType,
        displayName: String,
        mimeType: String,
        relativePath: String
    ): String?
    fun openOutputStream(contentUri: String, mode: String = "w"): OutputStream?
    fun finalizePending(contentUri: String): Int
    fun deleteMedia(contentUri: String): Int
}

class AndroidMediaStoreContentGateway(private val context: Context) : MediaStoreContentGateway {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT

    override fun insertPendingMedia(
        mediaType: MediaType,
        displayName: String,
        mimeType: String,
        relativePath: String
    ): String? {
        val collectionUri = when (mediaType) {
            MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaType.AUDIO_ONLY -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collectionUri, values)?.toString()
    }

    override fun openOutputStream(contentUri: String, mode: String): OutputStream? {
        val uri = Uri.parse(contentUri) ?: return null
        return context.contentResolver.openOutputStream(uri, mode)
    }

    override fun finalizePending(contentUri: String): Int {
        val uri = Uri.parse(contentUri) ?: return 0
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        return context.contentResolver.update(uri, values, null, null)
    }

    override fun deleteMedia(contentUri: String): Int {
        val uri = Uri.parse(contentUri) ?: return 0
        return context.contentResolver.delete(uri, null, null)
    }
}

class AndroidMediaStorePublisher(
    private val gateway: MediaStoreContentGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaStorePublisher {

    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(AndroidMediaStoreContentGateway(context), ioDispatcher)

    override suspend fun publishMedia(request: MediaStorePublishRequest): MediaStorePublishResult =
        withContext(ioDispatcher) {
            // Preflight 1: Android Version Compatibility (API 29+ scoped storage boundary)
            if (gateway.sdkInt < Build.VERSION_CODES.Q) {
                return@withContext MediaStorePublishResult.Unsupported(
                    "Gallery auto-publish requires Android 10 (API 29) or higher; legacy Android 8-9 without WRITE_EXTERNAL_STORAGE is not supported."
                )
            }

            val file = request.sourceFile

            // Preflight 2: Validate physical source file
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                return@withContext MediaStorePublishResult.Failed(
                    "Source media file is missing, invalid, or empty: ${file.absolutePath}"
                )
            }

            // Preflight 3: Refuse staging / temporary files
            val fileName = file.name.lowercase()
            if (fileName.endsWith(".part") || fileName.endsWith(".tmp") || fileName.startsWith(".")) {
                return@withContext MediaStorePublishResult.Failed(
                    "Refusing to publish incomplete staging or temporary file: ${file.name}"
                )
            }

            // Preflight 4: Resolve extension
            val ext = request.extension?.trim()?.removePrefix(".")?.ifBlank { null }
                ?: file.extension.ifBlank { null }
                ?: resolveExtensionFromMime(request.mediaType, request.mimeType)

            if (ext.isNullOrBlank()) {
                return@withContext MediaStorePublishResult.Failed(
                    "Cannot resolve valid media file extension for publication"
                )
            }

            val cleanExt = ext.lowercase()

            // Preflight 5: Resolve MIME type truthfully and enforce MIME family consistency
            val mime = resolveMimeType(request.mediaType, cleanExt, request.mimeType)
                ?: return@withContext MediaStorePublishResult.Failed(
                    "Unsupported or inconsistent MIME type for extension .$cleanExt and mediaType ${request.mediaType}"
                )

            val displayName = sanitizeDisplayName(request.title, cleanExt)

            val relativePath = when (request.mediaType) {
                MediaType.VIDEO -> "Movies/Mobiltool"
                MediaType.AUDIO_ONLY -> "Music/Mobiltool"
            }

            val insertedUri = try {
                gateway.insertPendingMedia(
                    mediaType = request.mediaType,
                    displayName = displayName,
                    mimeType = mime,
                    relativePath = relativePath
                )
            } catch (e: Throwable) {
                return@withContext MediaStorePublishResult.Failed(
                    "ContentResolver insert failed for $relativePath/$displayName: ${e.message}",
                    e
                )
            }

            if (insertedUri.isNullOrBlank()) {
                return@withContext MediaStorePublishResult.Failed(
                    "ContentResolver insert returned null URI for $relativePath/$displayName"
                )
            }

            try {
                file.inputStream().use { input ->
                    val outputStream = gateway.openOutputStream(insertedUri, "w")
                        ?: throw IOException("Failed to open output stream for MediaStore URI: $insertedUri")
                    outputStream.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }

                val updatedRows = gateway.finalizePending(insertedUri)
                if (updatedRows <= 0) {
                    try {
                        gateway.deleteMedia(insertedUri)
                    } catch (_: Throwable) {
                        // Best effort cleanup of unfinalized row
                    }
                    return@withContext MediaStorePublishResult.Failed(
                        "MediaStore publication could not be finalized: update IS_PENDING=0 returned $updatedRows rows for $insertedUri"
                    )
                }

                MediaStorePublishResult.Success(
                    contentUri = insertedUri,
                    displayName = displayName,
                    relativePath = relativePath
                )
            } catch (e: Throwable) {
                try {
                    gateway.deleteMedia(insertedUri)
                } catch (_: Throwable) {
                    // Best effort cleanup of pending row
                }
                MediaStorePublishResult.Failed(
                    "Failed to copy canonical media to MediaStore: ${e.message}",
                    e
                )
            }
        }

    internal fun sanitizeDisplayName(title: String, ext: String): String {
        val illegalCharsRegex = Regex("""[\\/:*?"<>|\r\n\t]""")
        var sanitized = title.replace(illegalCharsRegex, "_")
            .trim()
            .trim('.', '_', ' ')

        if (sanitized.isBlank()) {
            sanitized = "Mobiltool_Media_${System.currentTimeMillis()}"
        }

        // Limit length to avoid filesystem / MediaStore length bounds (e.g. 180 chars)
        if (sanitized.length > 180) {
            sanitized = sanitized.take(180).trimEnd('.', '_', ' ')
        }

        return if (sanitized.endsWith(".$ext", ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized.$ext"
        }
    }

    internal fun resolveMimeType(
        mediaType: MediaType,
        ext: String,
        requestedMime: String?
    ): String? {
        val trimmedMime = requestedMime?.trim()?.lowercase()
        if (!trimmedMime.isNullOrBlank() && trimmedMime != "application/octet-stream") {
            // FIX 05: MIME family consistency check
            val isMimeConsistent = when (mediaType) {
                MediaType.VIDEO -> trimmedMime.startsWith("video/")
                MediaType.AUDIO_ONLY -> trimmedMime.startsWith("audio/")
            }
            if (isMimeConsistent) {
                return trimmedMime
            }
        }

        // Resolve from trusted extension and verify it matches the MediaType family
        return when (ext) {
            "mp4" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/mp4" else "video/mp4"
            "m4a", "m4b", "aac" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/mp4" else null
            "mp3" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/mpeg" else null
            "webm" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/webm" else "video/webm"
            "ogg", "opus" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/ogg" else null
            "wav" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/wav" else null
            "flac" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/flac" else null
            "mkv" -> if (mediaType == MediaType.VIDEO) "video/x-matroska" else null
            "ts" -> if (mediaType == MediaType.VIDEO) "video/mp2t" else null
            else -> null
        }
    }

    private fun resolveExtensionFromMime(mediaType: MediaType, mime: String?): String? {
        val normalized = mime?.trim()?.lowercase() ?: return null
        return when (mediaType) {
            MediaType.VIDEO -> when (normalized) {
                "video/mp4" -> "mp4"
                "video/webm" -> "webm"
                "video/x-matroska" -> "mkv"
                "video/mp2t" -> "ts"
                else -> null
            }
            MediaType.AUDIO_ONLY -> when (normalized) {
                "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/webm", "audio/ogg", "audio/opus" -> "ogg"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/flac" -> "flac"
                else -> null
            }
        }
    }
}
