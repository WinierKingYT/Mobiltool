package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class RealHttpStreamDownloader(
    private val connectTimeoutMs: Int = 10000,
    private val readTimeoutMs: Int = 30000,
    private val bufferSizeBytes: Int = 32768 // 32KB buffer
) {

    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()

    suspend fun download(
        downloadId: String,
        sourceUrl: String,
        destinationFile: File,
        onProgress: (DownloadProgress) -> Unit
    ): AppResult<File> = withContext(Dispatchers.IO) {
        val validation = UrlClassifier.validateAndNormalize(sourceUrl)
        if (validation is UrlValidationResult.Invalid) {
            return@withContext AppResult.Error(
                message = "Invalid URL: ${validation.reason}",
                code = ErrorCode.VALIDATION_ERROR
            )
        }

        destinationFile.parentFile?.mkdirs()
        val partFile = File(destinationFile.parentFile, "${destinationFile.name}.part")

        var connection: HttpURLConnection? = null
        try {
            val url = URI(sourceUrl).toURL()
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mobiltool/1.0 (Linux; Android)")
            }

            activeConnections[downloadId] = connection

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext AppResult.Error(
                    message = "Server returned HTTP $responseCode (${connection.responseMessage})",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            var bytesDownloaded = 0L
            var lastProgressTime = System.currentTimeMillis()
            var bytesSinceLastProgress = 0L

            connection.inputStream.use { input ->
                FileOutputStream(partFile, false).use { output ->
                    val buffer = ByteArray(bufferSizeBytes)
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        if (!coroutineContext.isActive) {
                            throw CancellationException("Download cancelled by coroutine context")
                        }

                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        bytesSinceLastProgress += read

                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastProgressTime
                        if (timeDiff >= 200 || bytesDownloaded == totalBytes) {
                            val speed = if (timeDiff > 0) (bytesSinceLastProgress * 1000L) / timeDiff else 0L
                            val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

                            onProgress(
                                DownloadProgress(
                                    downloadId = downloadId,
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes,
                                    percent = percent,
                                    speedBytesPerSec = speed
                                )
                            )

                            lastProgressTime = now
                            bytesSinceLastProgress = 0L
                        }
                    }
                    output.flush()
                }
            }

            if (bytesDownloaded == 0L) {
                partFile.delete()
                return@withContext AppResult.Error("Download completed with 0 bytes received", code = ErrorCode.EXTRACTION_FAILED)
            }

            // Atomic rename from .part to target file
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            val renamed = partFile.renameTo(destinationFile)
            if (!renamed) {
                partFile.copyTo(destinationFile, overwrite = true)
                partFile.delete()
            }

            AppResult.Success(destinationFile)
        } catch (e: CancellationException) {
            partFile.delete()
            AppResult.Error("Download cancelled: ${e.message}", code = ErrorCode.UNKNOWN)
        } catch (e: Exception) {
            partFile.delete()
            AppResult.Error("Download failed: ${e.message}", code = ErrorCode.NETWORK_ERROR)
        } finally {
            activeConnections.remove(downloadId)
            runCatching { connection?.disconnect() }
        }
    }

    fun cancel(downloadId: String) {
        val conn = activeConnections.remove(downloadId)
        conn?.disconnect()
    }
}
