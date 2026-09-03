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
import java.util.concurrent.ConcurrentHashMap

data class DownloadedFileInfo(
    val file: File,
    val fileSizeBytes: Long,
    val sha256Hex: String,
    val detectedMimeType: String
)

class RealHttpStreamDownloader(
    private val connectTimeoutMs: Int = SafeHttpTransport.DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = SafeHttpTransport.DEFAULT_READ_TIMEOUT_MS,
    private val bufferSizeBytes: Int = 32768, // 32KB buffer
    private val dnsLookup: DnsLookup = SystemDnsLookup
) {

    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()

    suspend fun download(
        downloadId: String,
        sourceUrl: String,
        destinationFile: File,
        onProgress: (DownloadProgress) -> Unit
    ): AppResult<DownloadedFileInfo> = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()
        val partFile = File(destinationFile.parentFile, "${destinationFile.name}.part")

        // Clean up any stale partial files from prior aborted attempts
        if (partFile.exists()) {
            partFile.delete()
        }

        val transportResult = SafeHttpTransport.openSafeConnection(
            initialUrl = sourceUrl,
            method = "GET",
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            dnsLookup = dnsLookup
        )

        val response = when (transportResult) {
            is AppResult.Success -> transportResult.data
            is AppResult.Error -> return@withContext transportResult
            AppResult.Loading -> return@withContext AppResult.Loading
        }

        try {
            val conn = response.connection
            activeConnections[downloadId] = conn

            val code = response.responseCode
            if (code !in 200..299) {
                return@withContext AppResult.Error(
                    message = "Server returned HTTP $code (${conn.responseMessage})",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var bytesDownloaded = 0L
            var lastProgressTime = System.currentTimeMillis()
            var bytesSinceLastProgress = 0L

            conn.inputStream.use { input ->
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
                        if (timeDiff >= 200 || (totalBytes > 0 && bytesDownloaded == totalBytes)) {
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
                return@withContext AppResult.Error("Download finished with 0 bytes received", code = ErrorCode.EXTRACTION_FAILED)
            }

            // P2 Invariant: FILE VALIDATION + HASH before final destination commit
            val validation = MediaFileValidator.validateFile(partFile)
            if (validation is FileValidationResult.Invalid) {
                partFile.delete()
                return@withContext AppResult.Error(
                    message = "Downloaded file validation failed: ${validation.reason}",
                    code = ErrorCode.VALIDATION_ERROR
                )
            }

            val validResult = validation as FileValidationResult.Valid

            // Crash-safe commit: rename or verified hash copy
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val renameSuccess = partFile.renameTo(destinationFile)
            if (!renameSuccess) {
                partFile.copyTo(destinationFile, overwrite = true)
                val destinationSha = MediaFileValidator.calculateSha256(destinationFile)
                if (destinationSha != validResult.sha256Hex) {
                    destinationFile.delete()
                    partFile.delete()
                    return@withContext AppResult.Error(
                        message = "Crash-safe commit hash mismatch between staging and canonical destination",
                        code = ErrorCode.STORAGE_ERROR
                    )
                }
                partFile.delete()
            }

            AppResult.Success(
                DownloadedFileInfo(
                    file = destinationFile,
                    fileSizeBytes = validResult.fileSizeBytes,
                    sha256Hex = validResult.sha256Hex,
                    detectedMimeType = validResult.detectedMimeType
                )
            )
        } catch (e: CancellationException) {
            partFile.delete()
            AppResult.Error("Download cancelled: ${e.message}", code = ErrorCode.UNKNOWN)
        } catch (e: Exception) {
            partFile.delete()
            AppResult.Error("Download failed: ${e.message}", code = ErrorCode.NETWORK_ERROR)
        } finally {
            activeConnections.remove(downloadId)
            response.close()
        }
    }

    fun cancel(downloadId: String) {
        val conn = activeConnections.remove(downloadId)
        conn?.disconnect()
    }
}
