package com.personaltool.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.app.audio.AmbientMicRecorder
import com.personaltool.app.audio.RealAudioPlayer
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

    val recorder = AmbientMicRecorder(application.applicationContext)
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

    fun startLiveRecording(phoneNumber: String = "+90 532 100 2030", contactName: String? = "Ambient Audio Session") {
        val sessionId = UUID.randomUUID().toString()
        recorder.startRecording(sessionId)
    }

    fun stopLiveRecording(phoneNumber: String = "+90 532 100 2030", contactName: String? = "Ambient Audio Session") {
        val recordedPath = recorder.stopRecording() ?: return
        val file = File(recordedPath)
        if (!file.exists() || file.length() == 0L) return

        val durationMs = (recordingState.value.durationSeconds * 1000L).coerceAtLeast(1000L)

        // Truth Pass: Ambient mic recording is ONE_SIDED by physical reality on Android 9+
        val session = CallSession(
            id = UUID.randomUUID().toString(),
            phoneNumber = phoneNumber,
            contactName = contactName,
            direction = CallDirection.INCOMING,
            startTimeEpochMs = System.currentTimeMillis() - durationMs,
            endTimeEpochMs = System.currentTimeMillis(),
            durationMs = durationMs,
            recordingQuality = RecordingQuality.ONE_SIDED,
            captureTier = CallCaptureTier.TIER_1_STANDARD_USERSPACE,
            isLoudspeakerActive = false,
            audioFilePath = recordedPath,
            fileSizeBytes = file.length(),
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
        if (file.exists() && file.length() > 0) {
            player.loadAndPlay(path)
        }
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
}
