package com.personaltool.core.storage

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.model.media.DownloadStatus
import com.personaltool.core.model.media.MediaItem
import com.personaltool.core.model.media.MediaSource
import com.personaltool.core.model.media.MediaType
import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment
import com.personaltool.core.model.transcript.TranscriptStatus
import com.personaltool.core.storage.entity.CallEntity
import com.personaltool.core.storage.entity.MediaEntity
import com.personaltool.core.storage.entity.TranscriptEntity
import org.junit.Test

class EntityConversionTest {

    @Test
    fun callEntity_roundtripConversion_preservesData() {
        val domain = CallSession(
            id = "c-1",
            phoneNumber = "+901234567890",
            contactName = "Engineer",
            direction = CallDirection.OUTGOING,
            startTimeEpochMs = 1700000000000L,
            endTimeEpochMs = 1700000060000L,
            durationMs = 60000L,
            recordingQuality = RecordingQuality.VERIFIED_BIDIRECTIONAL,
            audioFilePath = "/path/to/audio.m4a",
            fileSizeBytes = 1024000L,
            hasTranscript = true,
            isFavorite = true
        )

        val entity = CallEntity.fromDomain(domain)
        val converted = entity.toDomain()

        assertThat(converted.id).isEqualTo(domain.id)
        assertThat(converted.phoneNumber).isEqualTo(domain.phoneNumber)
        assertThat(converted.contactName).isEqualTo(domain.contactName)
        assertThat(converted.direction).isEqualTo(domain.direction)
        assertThat(converted.recordingQuality).isEqualTo(domain.recordingQuality)
        assertThat(converted.durationMs).isEqualTo(domain.durationMs)
        assertThat(converted.hasTranscript).isTrue()
    }

    @Test
    fun mediaEntity_roundtripConversion_preservesData() {
        val domain = MediaItem(
            id = "m-1",
            sourceUrl = "https://youtube.com/watch?v=sample",
            title = "Sample Video Title",
            uploader = "Test Channel",
            durationMs = 120000L,
            localFilePath = "/path/to/video.mp4",
            mediaType = MediaType.VIDEO,
            sourcePlatform = MediaSource.YOUTUBE,
            downloadStatus = DownloadStatus.COMPLETED,
            fileSizeBytes = 5000000L
        )

        val entity = MediaEntity.fromDomain(domain)
        val converted = entity.toDomain()

        assertThat(converted.id).isEqualTo(domain.id)
        assertThat(converted.sourceUrl).isEqualTo(domain.sourceUrl)
        assertThat(converted.title).isEqualTo(domain.title)
        assertThat(converted.sourcePlatform).isEqualTo(MediaSource.YOUTUBE)
        assertThat(converted.downloadStatus).isEqualTo(DownloadStatus.COMPLETED)
    }

    @Test
    fun transcriptEntity_roundtripConversion_preservesSegments() {
        val domain = Transcript(
            id = "t-1",
            targetId = "c-1",
            language = "tr",
            status = TranscriptStatus.READY,
            segments = listOf(
                TranscriptSegment("s1", 0, 5000, "Merhaba nasılsınız?", "Speaker 1", 0.98f),
                TranscriptSegment("s2", 5000, 10000, "İyiyim teşekkür ederim.", "Speaker 2", 0.95f)
            ),
            confidence = 0.965f
        )

        val entity = TranscriptEntity.fromDomain(domain)
        val converted = entity.toDomain()

        assertThat(converted.id).isEqualTo(domain.id)
        assertThat(converted.targetId).isEqualTo(domain.targetId)
        assertThat(converted.status).isEqualTo(TranscriptStatus.READY)
        assertThat(converted.segments).hasSize(2)
        assertThat(converted.segments[0].text).isEqualTo("Merhaba nasılsınız?")
        assertThat(converted.segments[1].text).isEqualTo("İyiyim teşekkür ederim.")
    }
}
