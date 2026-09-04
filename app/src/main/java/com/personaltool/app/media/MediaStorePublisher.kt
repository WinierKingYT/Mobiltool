package com.personaltool.app.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.personaltool.core.model.media.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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

    data object Skipped : MediaStorePublishResult
}

interface MediaStorePublisher {
    suspend fun publishMedia(request: MediaStorePublishRequest): MediaStorePublishResult
}

class AndroidMediaStorePublisher(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaStorePublisher {

    override suspend fun publishMedia(request: MediaStorePublishRequest): MediaStorePublishResult =
        withContext(ioDispatcher) {
            val file = request.sourceFile

            // Preflight 1: Validate physical source file
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                return@withContext MediaStorePublishResult.Failed(
                    "Source media file is missing, invalid, or empty: ${file.absolutePath}"
                )
            }

            // Preflight 2: Refuse staging / temporary files
            val fileName = file.name.lowercase()
            if (fileName.endsWith(".part") || fileName.endsWith(".tmp") || fileName.startsWith(".")) {
                return@withContext MediaStorePublishResult.Failed(
                    "Refusing to publish incomplete staging or temporary file: ${file.name}"
                )
            }

            // Preflight 3: Resolve extension
            val ext = request.extension?.trim()?.removePrefix(".")?.ifBlank { null }
                ?: file.extension.ifBlank { null }
                ?: resolveExtensionFromMime(request.mimeType)

            if (ext.isNullOrBlank()) {
                return@withContext MediaStorePublishResult.Failed(
                    "Cannot resolve valid media file extension for publication"
                )
            }

            val cleanExt = ext.lowercase()

            // Preflight 4: Resolve MIME type truthfully
            val mime = resolveMimeType(request.mediaType, cleanExt, request.mimeType)
                ?: return@withContext MediaStorePublishResult.Failed(
                    "Unsupported or unknown MIME type for extension .$cleanExt and mediaType ${request.mediaType}"
                )

            val displayName = sanitizeDisplayName(request.title, cleanExt)

            val (collectionUri, relativePath) = when (request.mediaType) {
                MediaType.VIDEO -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    val path = "${Environment.DIRECTORY_MOVIES}/Mobiltool"
                    uri to path
                }
                MediaType.AUDIO_ONLY -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val path = "${Environment.DIRECTORY_MUSIC}/Mobiltool"
                    uri to path
                }
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val insertedUri = try {
                resolver.insert(collectionUri, contentValues)
            } catch (e: Throwable) {
                return@withContext MediaStorePublishResult.Failed(
                    "ContentResolver insert failed for $collectionUri: ${e.message}",
                    e
                )
            } ?: return@withContext MediaStorePublishResult.Failed(
                "ContentResolver insert returned null URI for $collectionUri"
            )

            try {
                file.inputStream().use { input ->
                    val outputStream = resolver.openOutputStream(insertedUri, "w")
                        ?: throw IOException("Failed to open output stream for MediaStore URI: $insertedUri")
                    outputStream.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val finalizeValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, finalizeValues, null, null)
                }

                MediaStorePublishResult.Success(
                    contentUri = insertedUri.toString(),
                    displayName = displayName,
                    relativePath = relativePath
                )
            } catch (e: Throwable) {
                try {
                    resolver.delete(insertedUri, null, null)
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
        val trimmedMime = requestedMime?.trim()
        if (!trimmedMime.isNullOrBlank() &&
            trimmedMime != "application/octet-stream" &&
            (trimmedMime.startsWith("video/") || trimmedMime.startsWith("audio/"))
        ) {
            return trimmedMime
        }

        return when (ext) {
            "mp4" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/mp4" else "video/mp4"
            "m4a", "m4b", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "webm" -> if (mediaType == MediaType.AUDIO_ONLY) "audio/webm" else "video/webm"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            else -> null
        }
    }

    private fun resolveExtensionFromMime(mime: String?): String? {
        val normalized = mime?.trim()?.lowercase() ?: return null
        return when (normalized) {
            "video/mp4" -> "mp4"
            "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "video/webm" -> "webm"
            "audio/webm", "audio/ogg", "audio/opus" -> "ogg"
            "video/x-matroska" -> "mkv"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/flac" -> "flac"
            else -> null
        }
    }
}
