package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OemCorrelationEngineTest {

    private val baseStartTime = 1700000000000L
    private val baseEndTime = 1700000060000L // 60-second call
    private val basePhone = "+905551234567"

    @Test
    fun singleGoodCandidate_inWindow_andMatchingDuration_returnsMatch() {
        val candidate = OemAudioCandidate(
            uri = null,
            displayName = "Call_Recording_20231114.m4a",
            dateModifiedEpochMs = baseStartTime + 5000L, // during call
            durationMs = 58000L, // close to 60s
            sizeBytes = 250000L,
            filePath = "/storage/emulated/0/Recordings/Call/Call_Recording_20231114.m4a"
        )

        val decision = OemCorrelationEngine.correlate(
            startTimeMs = baseStartTime,
            endTimeMs = baseEndTime,
            phoneNumber = basePhone,
            candidates = listOf(candidate)
        )

        assertThat(decision).isInstanceOf(OemCorrelationDecision.Match::class.java)
        val match = decision as OemCorrelationDecision.Match
        assertThat(match.candidate.displayName).isEqualTo("Call_Recording_20231114.m4a")
    }

    @Test
    fun singleCandidate_outsideTimeWindow_returnsNotFound() {
        val candidate = OemAudioCandidate(
            uri = null,
            displayName = "Old_Call.m4a",
            dateModifiedEpochMs = baseStartTime - 60000L, // 1 minute before call
            durationMs = 60000L,
            sizeBytes = 250000L,
            filePath = null
        )

        val decision = OemCorrelationEngine.correlate(
            startTimeMs = baseStartTime,
            endTimeMs = baseEndTime,
            phoneNumber = basePhone,
            candidates = listOf(candidate)
        )

        assertThat(decision).isInstanceOf(OemCorrelationDecision.NotFound::class.java)
        val notFound = decision as OemCorrelationDecision.NotFound
        assertThat(notFound.reason).contains("timestamp window")
    }

    @Test
    fun singleCandidate_severeDurationMismatch_returnsNotFound() {
        val candidate = OemAudioCandidate(
            uri = null,
            displayName = "Call_Recording_15Min.m4a",
            dateModifiedEpochMs = baseStartTime + 10000L,
            durationMs = 900000L, // 15 minutes vs 60s call!
            sizeBytes = 5000000L,
            filePath = null
        )

        val decision = OemCorrelationEngine.correlate(
            startTimeMs = baseStartTime,
            endTimeMs = baseEndTime,
            phoneNumber = basePhone,
            candidates = listOf(candidate)
        )

        assertThat(decision).isInstanceOf(OemCorrelationDecision.NotFound::class.java)
        val notFound = decision as OemCorrelationDecision.NotFound
        assertThat(notFound.reason).contains("duration mismatch")
    }

    @Test
    fun candidateWithUnknownDuration_isRetainedAndMatched() {
        val candidate = OemAudioCandidate(
            uri = null,
            displayName = "Call_Recording_NoMeta.m4a",
            dateModifiedEpochMs = baseStartTime + 2000L,
            durationMs = 0L, // unpopulated metadata
            sizeBytes = 150000L,
            filePath = null
        )

        val decision = OemCorrelationEngine.correlate(
            startTimeMs = baseStartTime,
            endTimeMs = baseEndTime,
            phoneNumber = basePhone,
            candidates = listOf(candidate)
        )

        assertThat(decision).isInstanceOf(OemCorrelationDecision.Match::class.java)
    }

    @Test
    fun multipleCandidates_withUniqueCleanNumberMatch_returnsMatch() {
        val candidate1 = OemAudioCandidate(
            uri = null,
            displayName = "Call_905551234567_Rec.m4a",
            dateModifiedEpochMs = baseStartTime + 1000L,
            durationMs = 59000L,
            sizeBytes = 250000L,
            filePath = null
        )
        val candidate2 = OemAudioCandidate(
            uri = null,
            displayName = "Call_905329876543_Rec.m4a",
            dateModifiedEpochMs = baseStartTime + 2000L,
            durationMs = 60000L,
            sizeBytes = 250000L,
            filePath = null
        )

        val decision = OemCorrelationEngine.correlate(
            startTimeMs = baseStartTime,
            endTimeMs = baseEndTime,
            phoneNumber = basePhone,
            candidates = listOf(candidate1, candidate2)
        )

        assertThat(decision).isInstanceOf(OemCorrelationDecision.Match::class.java)
        val match = decision as OemCorrelationDecision.Match
        assertThat(match.candidate.displayName).isEqualTo("Call_905551234567_Rec.m4a")
    }

    @Test
    fun multipleCandidates_forPrivateUnknownNumber_failsClosedAsAmbiguous() {
        val candidate1 = OemAudioCandidate(
            uri = null,
            displayName = "Call_A.m4a",
            dateModifiedEpochMs = baseStartTime + 1000L,
            durationMs = 60000L,
            sizeBytes = 250000L,
            filePath = null
        )
        val candidate2 = OemAudioCandidate(
            uri = null,
            displayName = "Call_B.m4a",
            dateModifiedEpochMs = baseStartTime + 2000L,
            durationMs = 60000L,
            sizeBytes = 250000L,
            filePath = null
        )

        val decision = OemCorrelationEngine.correlate(
            startTimeMs = baseStartTime,
            endTimeMs = baseEndTime,
            phoneNumber = "Private Number",
            candidates = listOf(candidate1, candidate2)
        )

        assertThat(decision).isInstanceOf(OemCorrelationDecision.Ambiguous::class.java)
        val ambiguous = decision as OemCorrelationDecision.Ambiguous
        assertThat(ambiguous.reason).contains("private/unknown number")
    }

    @Test
    fun multipleCandidates_matchingSameNumber_failsClosedAsAmbiguous() {
        val candidate1 = OemAudioCandidate(
            uri = null,
            displayName = "Call_905551234567_part1.m4a",
            dateModifiedEpochMs = baseStartTime + 1000L,
            durationMs = 58000L, // matches 60s call duration
            sizeBytes = 240000L,
            filePath = null
        )
        val candidate2 = OemAudioCandidate(
            uri = null,
            displayName = "Call_905551234567_part2.m4a",
            dateModifiedEpochMs = baseStartTime + 2000L,
            durationMs = 59000L, // matches 60s call duration
            sizeBytes = 245000L,
            filePath = null
        )

        val decision = OemCorrelationEngine.correlate(
            startTimeMs = baseStartTime,
            endTimeMs = baseEndTime,
            phoneNumber = basePhone,
            candidates = listOf(candidate1, candidate2)
        )

        assertThat(decision).isInstanceOf(OemCorrelationDecision.Ambiguous::class.java)
        val ambiguous = decision as OemCorrelationDecision.Ambiguous
        assertThat(ambiguous.reason).contains("Multiple OEM recording files (2) matched phone number")
    }
}
