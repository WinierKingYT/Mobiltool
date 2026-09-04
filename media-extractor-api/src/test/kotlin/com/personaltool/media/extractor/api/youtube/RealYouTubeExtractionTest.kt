package com.personaltool.media.extractor.api.youtube

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.media.MediaSource
import com.personaltool.media.extractor.api.DefaultMediaExtractor
import com.personaltool.media.extractor.api.DetectedMediaKind
import com.personaltool.media.extractor.api.DownloadRequest
import com.personaltool.media.extractor.api.DownloadedMediaResult
import com.personaltool.media.extractor.api.SystemDnsLookup
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

class RealYouTubeExtractionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun executeRealPublicYouTubeExtractionAndDownload() = runBlocking {
        val testUrl = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val ytExtractor = NewPipeYouTubeExtractor(dnsLookup = SystemDnsLookup)
        val defaultExtractor = DefaultMediaExtractor(dnsLookup = SystemDnsLookup, youtubeExtractor = ytExtractor)

        println("=== STARTING REAL YOUTUBE VIDEO EXTRACTION TEST ===")
        println("Target URL: $testUrl")

        // 1. Probe URL
        val probeResult = defaultExtractor.probeUrl(testUrl)
        assertThat(probeResult).isInstanceOf(AppResult.Success::class.java)
        val probe = (probeResult as AppResult.Success).data

        println("TITLE OBSERVED: ${probe.title}")
        println("DURATION OBSERVED: ${probe.durationMs} ms")
        println("UPLOADER: ${probe.uploader}")
        println("AVAILABLE FORMAT COUNT: ${probe.availableFormats.size}")

        assertThat(probe.sourcePlatform).isEqualTo(MediaSource.YOUTUBE)
        assertThat(probe.availableFormats).isNotEmpty()

        // 2. Select format with exact upstream itag ID
        val selectedFormat = probe.availableFormats.firstOrNull { it.isAudioOnly }
            ?: probe.availableFormats.first()
        println("REQUESTED FORMAT ID: ${selectedFormat.formatId}")

        // 3. Exact format re-resolution verification (P2-YT-FINAL-02)
        val extractResult = ytExtractor.extractStream(testUrl, selectedFormat.formatId)
        assertThat(extractResult).isInstanceOf(AppResult.Success::class.java)
        val resolvedStream = (extractResult as AppResult.Success).data

        val streamHost = try {
            URI(resolvedStream.directStreamUrl).host ?: "unknown"
        } catch (e: Exception) {
            "unparseable"
        }

        println("RESOLVED FORMAT ID: ${resolvedStream.formatId}")
        println("FORMAT TYPE: ${if (resolvedStream.isAudioOnly) "AUDIO" else "VIDEO"}")
        println("ITAG OBSERVED: ${resolvedStream.itag}")
        println("STREAM HOST OBSERVED: $streamHost")
        println("STREAM URL PRESENT: ${resolvedStream.directStreamUrl.isNotEmpty()}")

        // Strict Invariant: REQUESTED FORMAT ID == RESOLVED FORMAT ID
        assertThat(resolvedStream.formatId).isEqualTo(selectedFormat.formatId)
        assertThat(resolvedStream.directStreamUrl).isNotEmpty()

        // 4. Execute End-to-End Download through Mobiltool verified downloader
        val destinationFile = File(tempFolder.root, "real_yt_sample.${selectedFormat.ext}")
        val downloadRequest = DownloadRequest(
            id = "real-yt-01",
            sourceUrl = testUrl,
            formatId = selectedFormat.formatId,
            destinationPath = destinationFile.absolutePath
        )

        val downloadResult = defaultExtractor.downloadMedia(downloadRequest) { progress ->
            if (progress.percent % 25 == 0) {
                println("Download progress: ${progress.percent}% (${progress.bytesDownloaded} bytes)")
            }
        }

        assertThat(downloadResult).isInstanceOf(AppResult.Success::class.java)
        val downloaded = (downloadResult as AppResult.Success<DownloadedMediaResult>).data
        val file = File(downloaded.outputFilePath)

        println("=== REAL YOUTUBE QUALIFICATION EVIDENCE RECORD (VIDEO) ===")
        println("DATE: 2026-09-03")
        println("SOURCE URL: $testUrl")
        println("CANONICAL URL: ${probe.url}")
        println("NEWPIPE VERSION: v0.26.5")
        println("REQUESTED FORMAT ID: ${downloaded.requestedFormatId}")
        println("RESOLVED FORMAT ID: ${downloaded.resolvedFormatId}")
        println("FORMAT TYPE: ${if (resolvedStream.isAudioOnly) "AUDIO" else "VIDEO"}")
        println("STREAM RESOLUTION RESULT: SUCCESS (Direct $streamHost stream resolved)")
        println("DOWNLOAD RESULT: SUCCESS")
        println("BYTES DOWNLOADED: ${downloaded.fileSizeBytes}")
        println("FINAL FILE SIZE: ${file.length()}")
        println("MEDIA KIND: ${downloaded.mediaKind}")
        println("MIME TYPE: ${downloaded.mimeType}")
        println("SHA-256: ${downloaded.sha256Hex}")
        println("COMMIT METHOD: ${downloaded.commitMethod}")
        println("===========================================================")

        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isEqualTo(downloaded.fileSizeBytes)
        assertThat(downloaded.fileSizeBytes).isGreaterThan(1000L)
        assertThat(downloaded.requestedFormatId).isEqualTo(downloaded.resolvedFormatId)
        assertThat(downloaded.commitMethod).isEqualTo("StandardCopyOption.ATOMIC_MOVE")
        assertThat(downloaded.sha256Hex).isNotEmpty()
        // P2-TRUTH-LOCK-01: generic ISO-BMFF container does not fabricate video/mp4 when track kind is UNKNOWN
        assertThat(downloaded.mediaKind).isEqualTo(DetectedMediaKind.UNKNOWN)
        assertThat(downloaded.mimeType).isNull()
    }

    @Test
    fun executeRealPublicYouTubeShortProbeAndStreamResolution() = runBlocking {
        val shortUrl = "https://www.youtube.com/shorts/jNQXAC9IVRw"
        val ytExtractor = NewPipeYouTubeExtractor(dnsLookup = SystemDnsLookup)
        val defaultExtractor = DefaultMediaExtractor(dnsLookup = SystemDnsLookup, youtubeExtractor = ytExtractor)

        println("=== STARTING REAL YOUTUBE SHORT PROBE TEST ===")
        println("Target Short URL: $shortUrl")

        val probeResult = defaultExtractor.probeUrl(shortUrl)
        assertThat(probeResult).isInstanceOf(AppResult.Success::class.java)
        val probe = (probeResult as AppResult.Success).data

        val selectedFormat = probe.availableFormats.first()
        val extractResult = ytExtractor.extractStream(shortUrl, selectedFormat.formatId)
        assertThat(extractResult).isInstanceOf(AppResult.Success::class.java)
        val resolvedStream = (extractResult as AppResult.Success).data

        val streamHost = try {
            URI(resolvedStream.directStreamUrl).host ?: "unknown"
        } catch (e: Exception) {
            "unparseable"
        }

        println("=== REAL YOUTUBE SHORT EVIDENCE RECORD ===")
        println("DATE: 2026-09-03")
        println("SOURCE URL: $shortUrl")
        println("CANONICAL URL: ${probe.url}")
        println("NEWPIPE VERSION: v0.26.5")
        println("TITLE OBSERVED: ${probe.title}")
        println("DURATION OBSERVED: ${probe.durationMs} ms")
        println("AVAILABLE FORMAT COUNT: ${probe.availableFormats.size}")
        println("REQUESTED FORMAT ID: ${selectedFormat.formatId}")
        println("RESOLVED FORMAT ID: ${resolvedStream.formatId}")
        println("STREAM RESOLUTION RESULT: SUCCESS (Direct $streamHost stream resolved)")
        println("==========================================")

        assertThat(probe.sourcePlatform).isEqualTo(MediaSource.YOUTUBE)
        assertThat(probe.availableFormats).isNotEmpty()
        assertThat(resolvedStream.formatId).isEqualTo(selectedFormat.formatId)
        assertThat(resolvedStream.directStreamUrl).isNotEmpty()
    }

    @Test
    fun executeRealPublicYouTubeComplexUrlWithPlaylistAndQueryParamsExtraction() = runBlocking {
        val complexUrl = "https://www.youtube.com/watch?v=UrLVxJWdGqY&list=RDUrLVxJWdGqY&index=1"
        val ytExtractor = NewPipeYouTubeExtractor(dnsLookup = SystemDnsLookup)
        val defaultExtractor = DefaultMediaExtractor(dnsLookup = SystemDnsLookup, youtubeExtractor = ytExtractor)

        println("=== STARTING REAL YOUTUBE COMPLEX URL EXTRACTION TEST ===")
        println("Target Complex URL: $complexUrl")

        val probeResult = defaultExtractor.probeUrl(complexUrl)
        assertThat(probeResult).isInstanceOf(AppResult.Success::class.java)
        val probe = (probeResult as AppResult.Success).data

        println("CANONICAL URL: ${probe.url}")
        println("TITLE OBSERVED: ${probe.title}")
        println("DURATION OBSERVED: ${probe.durationMs} ms")
        println("UPLOADER: ${probe.uploader}")
        println("AVAILABLE FORMAT COUNT: ${probe.availableFormats.size}")

        assertThat(probe.url).isEqualTo("https://www.youtube.com/watch?v=UrLVxJWdGqY")
        assertThat(probe.sourcePlatform).isEqualTo(MediaSource.YOUTUBE)
        assertThat(probe.availableFormats).isNotEmpty()

        val selectedFormat = probe.availableFormats.first()
        val extractResult = ytExtractor.extractStream(complexUrl, selectedFormat.formatId)
        assertThat(extractResult).isInstanceOf(AppResult.Success::class.java)
        val resolvedStream = (extractResult as AppResult.Success).data

        val streamHost = try {
            URI(resolvedStream.directStreamUrl).host ?: "unknown"
        } catch (e: Exception) {
            "unparseable"
        }

        println("=== REAL YOUTUBE COMPLEX URL EVIDENCE RECORD ===")
        println("DATE: 2026-09-04")
        println("RAW URL: $complexUrl")
        println("CANONICAL URL: ${probe.url}")
        println("NEWPIPE VERSION: v0.26.5")
        println("TITLE: ${probe.title}")
        println("DURATION: ${probe.durationMs} ms")
        println("UPLOADER: ${probe.uploader}")
        println("AVAILABLE FORMATS: ${probe.availableFormats.map { it.formatId }}")
        println("REQUESTED FORMAT ID: ${selectedFormat.formatId}")
        println("RESOLVED FORMAT ID: ${resolvedStream.formatId}")
        println("STREAM RESOLUTION RESULT: SUCCESS (Direct $streamHost stream resolved)")
        println("================================================")

        assertThat(resolvedStream.formatId).isEqualTo(selectedFormat.formatId)
        assertThat(resolvedStream.directStreamUrl).isNotEmpty()
    }
}

