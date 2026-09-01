package com.personaltool.transcription.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.transcript.Transcript
import java.io.File

/**
 * Honest Transcription Engine:
 * Reports actual implementation status. On-device C++/JNI Whisper runtime is currently
 * unlinked, so it refuses to generate fake transcripts and truthfully returns ENGINE_UNAVAILABLE.
 */
class DefaultTranscriptionEngine(
    override val engineName: String = "UnlinkedLocalWhisperEngine",
    private val modelFile: File = File(System.getProperty("java.io.tmpdir"), "PersonalTool/models/whisper-tiny.tflite")
) : TranscriptionEngine {

    override suspend fun checkModelStatus(): ModelStatus {
        val exists = modelFile.exists() && modelFile.length() > 1024 * 1024
        return ModelStatus(
            isReady = false, // Not ready because inference runtime (C++/JNI Whisper) is not yet linked
            modelName = if (exists) "Whisper-Tiny (Model on disk, Native Runner Unlinked)" else "Whisper-Tiny (Not Present)",
            modelSizeBytes = if (exists) modelFile.length() else 0L,
            isDownloading = false
        )
    }

    override suspend fun transcribe(
        request: TranscriptionRequest,
        onProgress: (TranscriptionProgress) -> Unit
    ): AppResult<Transcript> {
        val audioFile = File(request.audioFilePath)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return AppResult.Error("Transcription failed: Audio source file does not exist on disk.")
        }

        // Truth Gate: Refuse to fabricate fake transcription text
        return AppResult.Error(
            "STT_RUNTIME_UNAVAILABLE: Local on-device Whisper C++ inference engine is not yet linked. " +
            "Fabricated placeholder transcripts have been purged. " +
            "Please use Desktop GPU Offload or wait for native libwhisper.so JNI binding."
        )
    }

    override suspend fun cancelTranscription(targetId: String): AppResult<Unit> {
        return AppResult.Success(Unit)
    }
}
