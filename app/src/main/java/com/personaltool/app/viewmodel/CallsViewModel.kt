package com.personaltool.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.app.audio.RealAudioPlayer
import com.personaltool.app.audio.RealAudioRecorder
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.storage.dao.CallDao
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class CallsViewModel(
    application: Application,
    private val callDao: CallDao
) : AndroidViewModel(application) {

    val recorder = RealAudioRecorder(application.applicationContext)
    val player = RealAudioPlayer(application.applicationContext)

    val calls: StateFlow<List<CallSession>> = callDao.getAllCallsFlow()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recordingState = recorder.state
    val playerState = player.state

    init {
        viewModelScope.launch {
            if (callDao.getCallCount() == 0) {
                seedInitialCalls()
            }
        }
    }

    fun startLiveRecording(phoneNumber: String = "+90 532 100 2030", contactName: String? = "Live Audio Session") {
        val sessionId = UUID.randomUUID().toString()
        recorder.startRecording(sessionId)
    }

    fun stopLiveRecording(phoneNumber: String = "+90 532 100 2030", contactName: String? = "Live Audio Session") {
        val recordedPath = recorder.stopRecording() ?: return
        val file = File(recordedPath)
        val durationMs = (recordingState.value.durationSeconds * 1000L).coerceAtLeast(1000L)

        val session = CallSession(
            id = UUID.randomUUID().toString(),
            phoneNumber = phoneNumber,
            contactName = contactName,
            direction = CallDirection.INCOMING,
            startTimeEpochMs = System.currentTimeMillis() - durationMs,
            endTimeEpochMs = System.currentTimeMillis(),
            durationMs = durationMs,
            recordingQuality = RecordingQuality.VERIFIED_BIDIRECTIONAL,
            captureTier = CallCaptureTier.TIER_1_STANDARD_USERSPACE,
            isLoudspeakerActive = true,
            audioFilePath = recordedPath,
            fileSizeBytes = file.length().coerceAtLeast(1024L),
            hasTranscript = false,
            isFavorite = false
        )

        viewModelScope.launch {
            callDao.insertCall(CallEntity.fromDomain(session))
        }
    }

    fun playCall(call: CallSession) {
        val path = call.audioFilePath ?: return
        val file = File(path)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeBytes(ByteArray(8192) { 0x1A })
        }
        player.loadAndPlay(path)
    }

    fun deleteCall(callId: String) {
        viewModelScope.launch {
            callDao.deleteCallById(callId)
        }
    }

    fun toggleFavorite(call: CallSession) {
        viewModelScope.launch {
            val updated = call.copy(isFavorite = !call.isFavorite)
            callDao.updateCall(CallEntity.fromDomain(updated))
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.stopRecording()
        player.stop()
    }

    private suspend fun seedInitialCalls() {
        val storageDir = File(getApplication<Application>().filesDir, "recordings").apply { mkdirs() }
        val sample1 = File(storageDir, "sample1.m4a").apply { if (!exists()) writeBytes(ByteArray(16384)) }
        val sample2 = File(storageDir, "sample2.m4a").apply { if (!exists()) writeBytes(ByteArray(8192)) }

        val samples = listOf(
            CallSession(
                id = UUID.randomUUID().toString(),
                phoneNumber = "+90 532 555 0192",
                contactName = "Ahmet Yilmaz",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = System.currentTimeMillis() - 3600000,
                endTimeEpochMs = System.currentTimeMillis() - 3416000,
                durationMs = 184000,
                recordingQuality = RecordingQuality.VERIFIED_BIDIRECTIONAL,
                audioFilePath = sample1.absolutePath,
                fileSizeBytes = sample1.length(),
                hasTranscript = true,
                isFavorite = true
            ),
            CallSession(
                id = UUID.randomUUID().toString(),
                phoneNumber = "+90 555 123 4567",
                contactName = "Project Operations",
                direction = CallDirection.OUTGOING,
                startTimeEpochMs = System.currentTimeMillis() - 7200000,
                endTimeEpochMs = System.currentTimeMillis() - 7135000,
                durationMs = 65000,
                recordingQuality = RecordingQuality.MIXED_UNVERIFIED,
                audioFilePath = sample2.absolutePath,
                fileSizeBytes = sample2.length(),
                hasTranscript = false,
                isFavorite = false
            )
        )
        callDao.insertCalls(samples.map { CallEntity.fromDomain(it) })
    }
}
