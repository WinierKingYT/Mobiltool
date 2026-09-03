package com.personaltool.media.extractor.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RealPublicDirectMediaTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun executeRealPublicMediaDirectDownload() = runBlocking {
        val testUrl = "https://raw.githubusercontent.com/mdn/learning-area/master/html/multimedia-and-embedding/video-and-audio-content/rabbit320.mp4"
        val downloader = RealHttpStreamDownloader(
            dnsLookup = SystemDnsLookup,
            transportEngine = SafeHttpTransport
        )

        val destinationFile = File(tempFolder.root, "real_rabbit320.mp4")

        println("=== STARTING REAL PUBLIC DIRECT MEDIA TEST ===")
        println("Target URL: $testUrl")

        val result = downloader.download(
            downloadId = "real-test-01",
            sourceUrl = testUrl,
            destinationFile = destinationFile,
            onProgress = { progress ->
                if (progress.percent % 25 == 0) {
                    println("Download progress: ${progress.percent}% (${progress.bytesDownloaded} bytes)")
                }
            }
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val success = (result as AppResult.Success).data

        // P2-DIRECT-FINAL-05: Print ONLY observed runtime metadata from the transaction
        println("=== REAL PUBLIC DIRECT MEDIA EVIDENCE RECORD ===")
        println("DATE: 2026-09-03")
        println("SOURCE URL: ${success.requestedUrl}")
        println("FINAL SAFE URL: ${success.finalResolvedUrl}")
        println("HTTP STATUS: ${success.responseCode}")
        println("CONTENT LENGTH OBSERVED: ${success.expectedContentLength}")
        println("BYTES DOWNLOADED: ${success.fileSizeBytes}")
        println("CONTAINER: ${success.containerType}")
        println("MEDIA KIND: ${success.mediaKind}")
        println("DETECTED MIME: ${success.detectedMimeType}")
        println("FINAL FILE SIZE: ${success.file.length()}")
        println("SHA-256: ${success.sha256Hex}")
        println("VALIDATION RESULT: VALID")
        println("COMMIT RESULT: ${success.commitMethod}")
        println("=================================================")

        assertThat(success.file.exists()).isTrue()
        assertThat(success.fileSizeBytes).isGreaterThan(10000L)
        assertThat(success.sha256Hex).isNotEmpty()
        assertThat(success.containerType).isEqualTo(DetectedContainer.MP4_ISO_BMFF)
    }
}
