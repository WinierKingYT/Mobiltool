package com.personaltool.transcription.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment
import com.personaltool.core.model.transcript.TranscriptStatus
import kotlinx.coroutines.delay
import java.util.UUID

class DefaultTranscriptionEngine(
    override val engineName: String = "LocalWhisperEngine"
) : TranscriptionEngine {

    override suspend fun checkModelStatus(): ModelStatus {
        return ModelStatus(
            isReady = true,
            modelName = "Whisper-Base-TR-Int8",
            modelSizeBytes = 74000000L,
            isDownloading = false
        )
    }

    override suspend fun transcribe(
        request: TranscriptionRequest,
        onProgress: (TranscriptionProgress) -> Unit
    ): AppResult<Transcript> {
        val sampleSegments = listOf(
            TranscriptSegment(
                id = UUID.randomUUID().toString(),
                startTimeMs = 0L,
                endTimeMs = 4500L,
                text = "Merhaba, sistem mimarisi ve arama kayıt planı üzerine konuşuyoruz.",
                speakerTag = "YOU",
                confidence = 0.98f
            ),
            TranscriptSegment(
                id = UUID.randomUUID().toString(),
                startTimeMs = 4600L,
                endTimeMs = 9800L,
                text = "Evet, tüm bileşenler olay güdümlü ve pil dostu olarak tasarlandı.",
                speakerTag = "REMOTE",
                confidence = 0.96f
            ),
            TranscriptSegment(
                id = UUID.randomUUID().toString(),
                startTimeMs = 10000L,
                endTimeMs = 15500L,
                text = "Medya indirme ve yerel transkripsiyon süreçleri tamamen cihazda çalışıyor.",
                speakerTag = "YOU",
                confidence = 0.99f
            ),
            TranscriptSegment(
                id = UUID.randomUUID().toString(),
                startTimeMs = 15800L,
                endTimeMs = 22000L,
                text = "Ayrıca zaman damgalarına tıklayarak sesin ilgili saniyesine sarabiliyoruz.",
                speakerTag = "REMOTE",
                confidence = 0.95f
            )
        )

        val accumulatedSegments = mutableListOf<TranscriptSegment>()

        // Simulate streaming / chunked transcription
        for ((index, segment) in sampleSegments.withIndex()) {
            delay(100)
            accumulatedSegments.add(segment)
            val progressPercent = ((index + 1).toDouble() / sampleSegments.size * 100).toInt()
            onProgress(
                TranscriptionProgress(
                    targetId = request.targetId,
                    progressPercent = progressPercent,
                    latestSegment = segment
                )
            )
        }

        val transcript = Transcript(
            id = UUID.randomUUID().toString(),
            targetId = request.targetId,
            language = if (request.language == "auto") "tr" else request.language,
            status = TranscriptStatus.READY,
            segments = accumulatedSegments,
            confidence = 0.97f,
            createdAt = System.currentTimeMillis()
        )

        return AppResult.Success(transcript)
    }

    override suspend fun cancelTranscription(targetId: String): AppResult<Unit> {
        return AppResult.Success(Unit)
    }
}
