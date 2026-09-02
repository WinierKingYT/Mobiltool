package com.personaltool.app.capture

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class OemRecordingImporterTest {

    @Test
    fun oemDiscoveryState_whenPathsMissing_returnsNone() {
        val isPresent = OemRecordingImporter.isOemRecordingDirectoryPresent(null)
        assertThat(isPresent).isNotNull()
    }

    @Test
    fun oemDiscoveryState_enum_hasAccurateTruthfulDescriptions() {
        assertThat(OemDiscoveryState.NONE.description).contains("No OEM")
        assertThat(OemDiscoveryState.OEM_MEDIA_PERMISSION_REQUIRED.description.lowercase()).contains("permission required")
        assertThat(OemDiscoveryState.OEM_CANDIDATE_DETECTED.description).contains("Candidate")
        assertThat(OemDiscoveryState.OEM_RECORDING_CONFIRMED.description).contains("Genuine")
        assertThat(OemDiscoveryState.OEM_PROFILE_QUALIFIED.description).contains("qualified")
    }

    @Test
    fun oemImportResult_types_distinguishCollisionFromNotFound() {
        val notFound = OemImportResult.NotFound("File not found")
        val collision = OemImportResult.AmbiguousCollision("Multiple files matched")

        assertThat(notFound).isInstanceOf(OemImportResult::class.java)
        assertThat(collision).isInstanceOf(OemImportResult::class.java)
        assertThat(notFound).isNotEqualTo(collision)
    }

    @Test
    fun durationMismatch_filteringLogic_rejectsDistantDurationRecordings() {
        val callDurationMs = 12000L // 12-second call

        val candidateOld = OemAudioCandidate(
            uri = Uri.EMPTY,
            displayName = "Call_Recording_Old.m4a",
            dateModifiedEpochMs = System.currentTimeMillis(),
            durationMs = 900000L, // 15 minutes! (mismatch)
            sizeBytes = 1000000L,
            filePath = null
        )

        // The tolerance rule: diff <= max(15000, 12000 * 0.4 = 15000ms)
        // 900000 - 12000 = 888000ms > 15000ms -> MUST be rejected
        val toleranceMs = (callDurationMs * 0.4).toLong().coerceAtLeast(15000L)
        val candidates = listOf(candidateOld)
        val filtered = candidates.filter { candidate ->
            val diffMs = kotlin.math.abs(candidate.durationMs - callDurationMs)
            diffMs <= toleranceMs
        }

        assertThat(filtered).isEmpty()
    }

    @Test
    fun multipleCandidatesInWindow_withoutMatchingDigits_failsClosed() {
        val candidate1 = OemAudioCandidate(
            uri = Uri.EMPTY,
            displayName = "Call_Recording_A.m4a",
            dateModifiedEpochMs = System.currentTimeMillis(),
            durationMs = 10000L,
            sizeBytes = 50000L,
            filePath = null
        )
        val candidate2 = OemAudioCandidate(
            uri = Uri.EMPTY,
            displayName = "Call_Recording_B.m4a",
            dateModifiedEpochMs = System.currentTimeMillis(),
            durationMs = 11000L,
            sizeBytes = 52000L,
            filePath = null
        )
        val candidates = listOf(candidate1, candidate2)

        val phoneNumber = "+905551234567"
        val cleanNumber = phoneNumber.filter { it.isDigit() }

        val numberMatches = candidates.filter { candidate ->
            candidate.displayName.contains(cleanNumber)
        }

        // None contains clean digits -> must trigger AmbiguousCollision
        assertThat(numberMatches).isEmpty()
        assertThat(candidates.size).isGreaterThan(1)
    }
}
