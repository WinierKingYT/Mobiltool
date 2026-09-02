package com.personaltool.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.app.audio.AmbientMicRecorder
import com.personaltool.app.audio.RealAudioPlayer
import com.personaltool.app.capture.CallCaptureCapabilityDetector
import com.personaltool.app.capture.DetailedCaptureCapability
import com.personaltool.app.capture.OemPermissionManager
import com.personaltool.app.capture.OemPermissionState
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.storage.dao.CallDao
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _hardwareCapability = MutableStateFlow(
        CallCaptureCapabilityDetector.detectCapability(application.applicationContext)
    )
    val hardwareCapability: StateFlow<DetailedCaptureCapability> = _hardwareCapability.asStateFlow()

    private val _permissionState = MutableStateFlow(
        OemPermissionManager.getPermissionState(application.applicationContext)
    )
    val permissionState: StateFlow<OemPermissionState> = _permissionState.asStateFlow()

    val calls: StateFlow<List<CallSession>> = callDao.getAllCallsFlow()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recordingState = recorder.state
    val playerState = player.state

    fun refreshCapability() {
        val context = getApplication<Application>().applicationContext
        _permissionState.value = OemPermissionManager.getPermissionState(context)
        _hardwareCapability.value = CallCaptureCapabilityDetector.detectCapability(context)
    }

    fun onPermissionResult(isGranted: Boolean) {
        val context = getApplication<Application>().applicationContext
        _permissionState.value = if (isGranted) OemPermissionState.GRANTED else OemPermissionState.DENIED
        _hardwareCapability.value = CallCaptureCapabilityDetector.detectCapability(context)
    }

    fun startLiveRecording() {
        val sessionId = UUID.randomUUID().toString()
        recorder.startRecording(sessionId)
    }

    fun stopLiveRecording() {
        val recordedPath = recorder.stopRecording() ?: return
        val file = File(recordedPath)
        if (!file.exists() || file.length() == 0L) return

        val durationMs = (recordingState.value.durationSeconds * 1000L).coerceAtLeast(1000L)

        // Truth Pass: Manual mic tests are strictly LOCAL_AMBIENT_MEMO, not fake incoming calls
        val session = CallSession(
            id = UUID.randomUUID().toString(),
            phoneNumber = "LOCAL_MIC",
            contactName = "Local Ambient Mic Memo",
            direction = CallDirection.LOCAL_AMBIENT_MEMO,
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
