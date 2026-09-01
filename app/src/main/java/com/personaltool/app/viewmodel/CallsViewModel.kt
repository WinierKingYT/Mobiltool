package com.personaltool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.call.capture.api.CaptureEngine
import com.personaltool.call.capture.api.DefaultCaptureEngine
import com.personaltool.core.common.result.AppResult
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
import java.util.UUID

class CallsViewModel(
    private val callDao: CallDao,
    private val captureEngine: CaptureEngine = DefaultCaptureEngine()
) : ViewModel() {

    val calls: StateFlow<List<CallSession>> = callDao.getAllCallsFlow()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeCaptureState = captureEngine.activeState

    init {
        // Seed default records if empty
        viewModelScope.launch {
            if (callDao.getCallCount() == 0) {
                seedInitialCalls()
            }
        }
    }

    fun startRecording(phoneNumber: String, contactName: String? = null) {
        val callId = UUID.randomUUID().toString()
        viewModelScope.launch {
            captureEngine.startCapture(callId, phoneNumber)
        }
    }

    fun stopRecording(phoneNumber: String, contactName: String? = null) {
        val active = activeCaptureState.value ?: return
        viewModelScope.launch {
            when (val result = captureEngine.stopCapture(active.callId)) {
                is AppResult.Success -> {
                    val finalized = result.data
                    val session = CallSession(
                        id = finalized.callId,
                        phoneNumber = phoneNumber,
                        contactName = contactName,
                        direction = CallDirection.INCOMING,
                        startTimeEpochMs = System.currentTimeMillis() - finalized.durationMs,
                        endTimeEpochMs = System.currentTimeMillis(),
                        durationMs = finalized.durationMs,
                        recordingQuality = finalized.quality,
                        audioFilePath = finalized.audioFilePath,
                        fileSizeBytes = finalized.fileSizeBytes,
                        hasTranscript = false,
                        isFavorite = false
                    )
                    callDao.insertCall(CallEntity.fromDomain(session))
                }
                is AppResult.Error -> {}
                AppResult.Loading -> {}
            }
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

    private suspend fun seedInitialCalls() {
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
                audioFilePath = "/storage/emulated/0/PersonalTool/calls/sample1.m4a",
                fileSizeBytes = 2450000,
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
                audioFilePath = "/storage/emulated/0/PersonalTool/calls/sample2.m4a",
                fileSizeBytes = 890000,
                hasTranscript = false,
                isFavorite = false
            ),
            CallSession(
                id = UUID.randomUUID().toString(),
                phoneNumber = "+90 850 222 0000",
                contactName = "Support Desk",
                direction = CallDirection.INCOMING,
                startTimeEpochMs = System.currentTimeMillis() - 86400000,
                endTimeEpochMs = System.currentTimeMillis() - 86088000,
                durationMs = 312000,
                recordingQuality = RecordingQuality.ONE_SIDED,
                audioFilePath = "/storage/emulated/0/PersonalTool/calls/sample3.m4a",
                fileSizeBytes = 4100000,
                hasTranscript = false,
                isFavorite = false
            )
        )
        callDao.insertCalls(samples.map { CallEntity.fromDomain(it) })
    }
}
