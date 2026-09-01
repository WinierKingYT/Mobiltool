package com.personaltool.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AmbientRecordingState(
    val isRecording: Boolean = false,
    val durationSeconds: Long = 0L,
    val currentMaxAmplitude: Int = 0,
    val outputFilePath: String? = null,
    val errorMessage: String? = null
)

/**
 * Truthful Ambient Microphone Recorder:
 * Records acoustic room/device ambient sound via MIC source.
 * By physical definition on Android 9+, standard mic recording cannot guarantee
 * bidirectional call capture (it captures local voice + room audio only).
 */
class AmbientMicRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var currentFile: File? = null

    private val _state = MutableStateFlow(AmbientRecordingState())
    val state: StateFlow<AmbientRecordingState> = _state.asStateFlow()

    @Suppress("DEPRECATION")
    fun startRecording(sessionId: String): Result<String> {
        return runCatching {
            stopRecording()

            val dir = File(context.filesDir, "ambient_recordings").apply { mkdirs() }
            val file = File(dir, "ambient_${sessionId}_${System.currentTimeMillis()}.m4a")
            currentFile = file

            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            val startTime = System.currentTimeMillis()

            recordingJob = CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    val duration = (System.currentTimeMillis() - startTime) / 1000L
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    _state.value = AmbientRecordingState(
                        isRecording = true,
                        durationSeconds = duration,
                        currentMaxAmplitude = amp,
                        outputFilePath = file.absolutePath
                    )
                    delay(200)
                }
            }

            file.absolutePath
        }.onFailure { err ->
            _state.value = AmbientRecordingState(isRecording = false, errorMessage = err.message)
        }
    }

    fun stopRecording(): String? {
        recordingJob?.cancel()
        recordingJob = null

        return runCatching {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null
            val path = currentFile?.absolutePath
            _state.value = AmbientRecordingState(isRecording = false, outputFilePath = path)
            path
        }.getOrNull()
    }
}
