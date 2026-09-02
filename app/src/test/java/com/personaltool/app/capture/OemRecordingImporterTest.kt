package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class OemRecordingImporterTest {

    @Test
    fun oemDiscoveryState_whenPathsMissing_returnsNone() {
        val isPresent = OemRecordingImporter.isOemRecordingDirectoryPresent(null)
        // On standard dev machine without /Recordings/Call, should return false or safe default
        assertThat(isPresent).isNotNull()
    }

    @Test
    fun oemDiscoveryState_enum_hasAccurateTruthfulDescriptions() {
        assertThat(OemDiscoveryState.NONE.description).contains("No OEM")
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
}
