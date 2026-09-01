package com.personaltool.transcription.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment
import com.personaltool.core.model.transcript.TranscriptStatus
import org.junit.Test

class TranscriptExporterTest {

    private val sampleTranscript = Transcript(
        id = "t-test",
        targetId = "target-1",
        language = "tr",
        status = TranscriptStatus.READY,
        confidence = 0.98f,
        segments = listOf(
            TranscriptSegment("s1", 1200L, 4500L, "First test speech segment.", "YOU", 0.99f),
            TranscriptSegment("s2", 5000L, 8200L, "Second test speech segment.", "REMOTE", 0.97f)
        )
    )

    @Test
    fun exportAsPlainText_containsTimestampsAndSpeakers() {
        val text = TranscriptExporter.exportAsPlainText(sampleTranscript, includeTimestamps = true)
        assertThat(text).contains("[00:01] YOU: First test speech segment.")
        assertThat(text).contains("[00:05] REMOTE: Second test speech segment.")
    }

    @Test
    fun exportAsSrt_containsStandardSrtIndexAndTimingFormat() {
        val srt = TranscriptExporter.exportAsSrt(sampleTranscript)
        assertThat(srt).contains("1")
        assertThat(srt).contains("00:00:01,200 --> 00:00:04,500")
        assertThat(srt).contains("YOU: First test speech segment.")
        assertThat(srt).contains("2")
        assertThat(srt).contains("00:00:05,000 --> 00:00:08,200")
    }

    @Test
    fun exportAsVtt_containsHeaderAndTags() {
        val vtt = TranscriptExporter.exportAsVtt(sampleTranscript)
        assertThat(vtt).startsWith("WEBVTT")
        assertThat(vtt).contains("00:00:01.200 --> 00:00:04.500")
        assertThat(vtt).contains("<v YOU>First test speech segment.</v>")
    }

    @Test
    fun exportAsMarkdown_containsMetadataHeaderAndMarkdownList() {
        val md = TranscriptExporter.exportAsMarkdown(sampleTranscript, "Call Transcript")
        assertThat(md).contains("# Call Transcript")
        assertThat(md).contains("**Language:** tr")
        assertThat(md).contains("`[00:01]` **YOU** First test speech segment.")
    }
}
