package com.personaltool.media.extractor.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.common.result.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

enum class DestinationCollisionPolicy {
    FAIL_IF_EXISTS,
    OVERWRITE_CRASH_SAFE
}

interface FileCommitter {
    fun commit(stagingFile: File, destinationFile: File, overwrite: Boolean): Boolean
    fun commitMethodName(): String
}

object StandardAtomicFileCommitter : FileCommitter {
    override fun commit(stagingFile: File, destinationFile: File, overwrite: Boolean): Boolean {
        val options = if (overwrite) {
            arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            arrayOf(StandardCopyOption.ATOMIC_MOVE)
        }
        return try {
            Files.move(stagingFile.toPath(), destinationFile.toPath(), *options)
            true
        } catch (e: Exception) {
            // Strict Invariant (P2-DIRECT-FINAL-01):
            // Never fall back to non-atomic renameTo or copy. Fail closed.
            false
        }
    }

    override fun commitMethodName(): String = "StandardCopyOption.ATOMIC_MOVE"
}

data class DownloadedFileInfo(
    val file: File,
    val fileSizeBytes: Long,
    val sha256Hex: String,
    val containerType: DetectedContainer,
    val mediaKind: DetectedMediaKind,
    val detectedMimeType: String,
    val requestedUrl: String,
    val finalResolvedUrl: String,
    val responseCode: Int,
    val expectedContentLength: Long,
    val commitMethod: String
)

class RealHttpStreamDownloader(
    private val dnsLookup: DnsLookup = SystemDnsLookup,
    private val transportEngine: SafeHttpTransportEngine = SafeHttpTransport,
    private val fileCommitter: FileCommitter = StandardAtomicFileCommitter
) {

    private val activeDownloads = ConcurrentHashMap<String, Boolean>()

    suspend fun download(
        downloadId: String,
        sourceUrl: String,
        destinationFile: File,
        collisionPolicy: DestinationCollisionPolicy = DestinationCollisionPolicy.FAIL_IF_EXISTS,
        onProgress: (DownloadProgress) -> Unit
    ): AppResult<DownloadedFileInfo> = withContext(Dispatchers.IO) {
        activeDownloads[downloadId] = true

        // 1. Collision and directory check
        val parentDir = destinationFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        if (destinationFile.exists() && collisionPolicy == DestinationCollisionPolicy.FAIL_IF_EXISTS) {
            return@withContext AppResult.Error(
                message = "Destination file already exists and collision policy is FAIL_IF_EXISTS: ${destinationFile.name}",
                code = ErrorCode.STORAGE_ERROR
            )
        }

        // Staging file in same directory for same-filesystem atomic commit
        val stagingPartFile = File(parentDir, "${destinationFile.name}.part")
        if (stagingPartFile.exists()) {
            stagingPartFile.delete()
        }

        // 2. Open verified and DNS-bound connection
        val connResult = transportEngine.openSafeConnection(
            initialUrl = sourceUrl,
            method = "GET",
            dnsLookup = dnsLookup
        )

        val safeResponse = when (connResult) {
            is AppResult.Success -> connResult.data
            is AppResult.Error -> return@withContext connResult
            AppResult.Loading -> return@withContext AppResult.Loading
        }

        var bytesDownloaded = 0L
        val expectedContentLength = safeResponse.contentLength
        val startTime = System.currentTimeMillis()
        var wasCancelled = false

        try {
            val responseCode = safeResponse.responseCode
            if (responseCode !in 200..299) {
                stagingPartFile.delete()
                return@withContext AppResult.Error(
                    message = "Server returned HTTP $responseCode for stream download",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            safeResponse.use { resp ->
                resp.responseBodyStream.use { input ->
                    FileOutputStream(stagingPartFile).use { output ->
                        val buffer = ByteArray(32768) // 32KB bounded memory buffer
                        var bytesRead: Int
                        var lastProgressPercent = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (activeDownloads[downloadId] == false) {
                                wasCancelled = true
                                break
                            }

                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            val percent = if (expectedContentLength > 0L) {
                                ((bytesDownloaded * 100) / expectedContentLength).toInt().coerceIn(0, 99)
                            } else {
                                -1
                            }

                            if (percent != lastProgressPercent) {
                                lastProgressPercent = percent
                                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                                val speedBps = (bytesDownloaded / elapsedSec).toLong()
                                onProgress(
                                    DownloadProgress(
                                        downloadId = downloadId,
                                        bytesDownloaded = bytesDownloaded,
                                        totalBytes = expectedContentLength,
                                        percent = percent,
                                        speedBytesPerSec = speedBps
                                    )
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (wasCancelled) {
                stagingPartFile.delete()
                return@withContext AppResult.Error(
                    message = "Download cancelled by user",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            // 3. Response Completeness Check (P2-DIRECT-FIX-04)
            if (expectedContentLength > 0L && bytesDownloaded != expectedContentLength) {
                stagingPartFile.delete()
                return@withContext AppResult.Error(
                    message = "Premature EOF: expected $expectedContentLength bytes but received $bytesDownloaded bytes",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            if (bytesDownloaded == 0L) {
                stagingPartFile.delete()
                return@withContext AppResult.Error(
                    message = "Downloaded 0 bytes (empty response payload)",
                    code = ErrorCode.NETWORK_ERROR
                )
            }

            // 4. Content Validation on Staging File (P2-DIRECT-FIX-01 & P2-DIRECT-FINAL-02)
            val stagingValidation = MediaFileValidator.validateFile(
                stagingPartFile,
                context = ValidationContext.STAGING_PAYLOAD
            )

            val validResult = when (stagingValidation) {
                is FileValidationResult.Valid -> stagingValidation
                is FileValidationResult.Invalid -> {
                    stagingPartFile.delete()
                    return@withContext AppResult.Error(
                        message = "Downloaded stream validation failed: ${stagingValidation.reason}",
                        code = ErrorCode.VALIDATION_ERROR
                    )
                }
            }

            // 5. Strictly Atomic Commit (P2-DIRECT-FINAL-01)
            // Sequence: VALID STAGING -> FINALIZATION PREP -> ATOMIC MOVE -> CANONICAL FILE VISIBLE
            val overwrite = collisionPolicy == DestinationCollisionPolicy.OVERWRITE_CRASH_SAFE
            val commitSuccess = fileCommitter.commit(stagingPartFile, destinationFile, overwrite)

            if (!commitSuccess || !destinationFile.exists()) {
                // Fail closed: Do NOT fallback to non-atomic renameTo/copy. Preserve staging.
                return@withContext AppResult.Error(
                    message = "Atomic commit to canonical destination failed (${fileCommitter.commitMethodName()}); staging file preserved at ${stagingPartFile.name}",
                    code = ErrorCode.STORAGE_ERROR
                )
            }

            // 6. Verify Canonical File Lifecycle
            val canonicalValidation = MediaFileValidator.validateFile(
                destinationFile,
                context = ValidationContext.CANONICAL_MEDIA
            )

            if (canonicalValidation is FileValidationResult.Invalid) {
                destinationFile.delete()
                return@withContext AppResult.Error(
                    message = "Canonical media verification failed: ${canonicalValidation.reason}",
                    code = ErrorCode.VALIDATION_ERROR
                )
            }

            onProgress(
                DownloadProgress(
                    downloadId = downloadId,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = bytesDownloaded,
                    percent = 100,
                    speedBytesPerSec = 0L
                )
            )

            AppResult.Success(
                DownloadedFileInfo(
                    file = destinationFile,
                    fileSizeBytes = validResult.fileSizeBytes,
                    sha256Hex = validResult.sha256Hex,
                    containerType = validResult.containerType,
                    mediaKind = validResult.mediaKind,
                    detectedMimeType = validResult.detectedMimeType,
                    requestedUrl = safeResponse.requestedUrl,
                    finalResolvedUrl = safeResponse.finalUrl,
                    responseCode = safeResponse.responseCode,
                    expectedContentLength = expectedContentLength,
                    commitMethod = fileCommitter.commitMethodName()
                )
            )

        } catch (e: Exception) {
            stagingPartFile.delete()
            AppResult.Error(
                message = "Download stream failed: ${e.message}",
                cause = e,
                code = ErrorCode.NETWORK_ERROR
            )
        } finally {
            activeDownloads.remove(downloadId)
        }
    }

    fun cancel(downloadId: String) {
        activeDownloads[downloadId] = false
    }
}
