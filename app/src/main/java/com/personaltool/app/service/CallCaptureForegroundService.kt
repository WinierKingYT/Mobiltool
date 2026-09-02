package com.personaltool.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.personaltool.app.PersonalToolApplication
import com.personaltool.app.capture.AudioFileInspector
import com.personaltool.app.capture.CallCaptureCapabilityDetector
import com.personaltool.app.capture.CallRecordingJournal
import com.personaltool.app.capture.OemImportResult
import com.personaltool.app.capture.OemRecordingImporter
import com.personaltool.app.capture.PrivilegedCompanionClient
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class CallCaptureForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L
    private var activePhoneNumber: String = "UNKNOWN"
    private var activeDirection: CallDirection = CallDirection.INCOMING
    private var isRecordingActive = false
    private var activeTier: CallCaptureTier = CallCaptureTier.UNSUPPORTED_USERSPACE
    private var activeCallId: String = ""

    companion object {
        const val ACTION_START_CALL_CAPTURE = "com.personaltool.action.START_CALL_CAPTURE"
        const val ACTION_STOP_CALL_CAPTURE = "com.personaltool.action.STOP_CALL_CAPTURE"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "mobiltool_call_capture_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL_CAPTURE -> {
                if (!isRecordingActive) {
                    activePhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown Caller"
                    val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, true)
                    activeDirection = if (isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING
                    activeCallId = UUID.randomUUID().toString()
                    startTimeMs = System.currentTimeMillis()

                    val capability = CallCaptureCapabilityDetector.detectCapability(this)
                    activeTier = capability.tier

                    if (capability.tier == CallCaptureTier.UNSUPPORTED_USERSPACE || !capability.isTwoWaySupported) {
                        // Hard Capability Gate: Fail closed. Record unrecorded metadata session.
                        startForegroundWithNotification()
                        val app = applicationContext as? PersonalToolApplication
                        val dao = app?.database?.callDao()
                        if (dao != null) {
                            val now = System.currentTimeMillis()
                            val unrecordedSession = CallSession(
                                id = activeCallId,
                                phoneNumber = activePhoneNumber,
                                direction = activeDirection,
                                startTimeEpochMs = now,
                                endTimeEpochMs = now,
                                durationMs = 0L,
                                recordingQuality = RecordingQuality.UNSUPPORTED,
                                captureTier = CallCaptureTier.UNSUPPORTED_USERSPACE,
                                unrecordedReason = capability.physicalLimitationReason
                            )
                            CoroutineScope(Dispatchers.IO).launch {
                                dao.insertCall(CallEntity.fromDomain(unrecordedSession))
                            }
                        }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        startForegroundWithNotification()
                        startActiveCapture(capability.tier)
                    }
                }
            }
            ACTION_STOP_CALL_CAPTURE -> {
                if (isRecordingActive) {
                    stopActiveCapture()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mobiltool // Active Call Session")
            .setContentText("Monitoring status for $activePhoneNumber")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startActiveCapture(tier: CallCaptureTier) {
        acquireWakeLock()
        isRecordingActive = true
        val dir = File(filesDir, "calls").apply { mkdirs() }
        val file = File(dir, "call_${activeCallId}_${startTimeMs}.m4a")
        currentOutputFile = file

        // Crash-safety journal registration
        CallRecordingJournal.recordStart(
            context = this,
            entry = com.personaltool.app.capture.InFlightCallJournalEntry(
                callId = activeCallId,
                phoneNumber = activePhoneNumber,
                direction = activeDirection,
                captureTier = tier,
                startTimeEpochMs = startTimeMs,
                tempAudioPath = file.absolutePath
            )
        )

        when (tier) {
            CallCaptureTier.PRIVILEGED_DIRECT -> {
                PrivilegedCompanionClient.startCapture(
                    callId = activeCallId,
                    phoneNumber = activePhoneNumber,
                    outputFile = file
                ) { /* Completion handled in stopActiveCapture */ }
            }
            CallCaptureTier.OEM_IMPORT -> {
                // OEM recorder runs natively in dialer; importer will harvest file upon call conclusion
            }
            else -> {
                // No action
            }
        }
    }

    private fun stopActiveCapture() {
        isRecordingActive = false
        val endTimeMs = System.currentTimeMillis()
        val dir = File(filesDir, "calls").apply { mkdirs() }
        val app = applicationContext as? PersonalToolApplication
        val dao = app?.database?.callDao()

        when (activeTier) {
            CallCaptureTier.PRIVILEGED_DIRECT -> {
                PrivilegedCompanionClient.stopCapture()
                releaseWakeLock()

                val file = currentOutputFile
                if (file != null && file.exists()) {
                    val inspection = AudioFileInspector.inspectRecordedFile(
                        filePath = file.absolutePath,
                        defaultQuality = RecordingQuality.MIXED_UNVERIFIED,
                        captureTier = CallCaptureTier.PRIVILEGED_DIRECT,
                        isPhysicallyQualified = false
                    )

                    if (inspection.isValid) {
                        val session = CallSession(
                            id = activeCallId,
                            phoneNumber = activePhoneNumber,
                            direction = activeDirection,
                            startTimeEpochMs = startTimeMs,
                            endTimeEpochMs = endTimeMs,
                            durationMs = inspection.durationMs,
                            recordingQuality = inspection.determinedQuality,
                            captureTier = CallCaptureTier.PRIVILEGED_DIRECT,
                            audioFilePath = file.absolutePath,
                            fileSizeBytes = inspection.fileSizeBytes
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            dao?.insertCall(CallEntity.fromDomain(session))
                        }
                    } else {
                        file.delete()
                    }
                }
                CallRecordingJournal.recordEnd(this)
            }
            CallCaptureTier.OEM_IMPORT -> {
                releaseWakeLock()
                val importResult = OemRecordingImporter.findAndImport(
                    context = this,
                    phoneNumber = activePhoneNumber,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    targetVaultDir = dir
                )

                when (importResult) {
                    is OemImportResult.Success -> {
                        val file = importResult.importedFile
                        val inspection = AudioFileInspector.inspectRecordedFile(
                            filePath = file.absolutePath,
                            defaultQuality = RecordingQuality.MIXED_UNVERIFIED,
                            captureTier = CallCaptureTier.OEM_IMPORT,
                            isPhysicallyQualified = false
                        )

                        val session = CallSession(
                            id = activeCallId,
                            phoneNumber = activePhoneNumber,
                            direction = activeDirection,
                            startTimeEpochMs = startTimeMs,
                            endTimeEpochMs = endTimeMs,
                            durationMs = if (inspection.isValid) inspection.durationMs else (endTimeMs - startTimeMs).coerceAtLeast(0L),
                            recordingQuality = if (inspection.isValid) inspection.determinedQuality else RecordingQuality.MIXED_UNVERIFIED,
                            captureTier = CallCaptureTier.OEM_IMPORT,
                            audioFilePath = file.absolutePath,
                            fileSizeBytes = importResult.fileSize
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            dao?.insertCall(CallEntity.fromDomain(session))
                        }
                    }
                    is OemImportResult.NotFound -> {
                        val unrecordedSession = CallSession(
                            id = activeCallId,
                            phoneNumber = activePhoneNumber,
                            direction = activeDirection,
                            startTimeEpochMs = startTimeMs,
                            endTimeEpochMs = endTimeMs,
                            durationMs = 0L,
                            recordingQuality = RecordingQuality.UNSUPPORTED,
                            captureTier = CallCaptureTier.OEM_IMPORT,
                            unrecordedReason = "OEM Ingestion: ${importResult.diagnosticReason}"
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            dao?.insertCall(CallEntity.fromDomain(unrecordedSession))
                        }
                    }
                    is OemImportResult.AmbiguousCollision -> {
                        val unrecordedSession = CallSession(
                            id = activeCallId,
                            phoneNumber = activePhoneNumber,
                            direction = activeDirection,
                            startTimeEpochMs = startTimeMs,
                            endTimeEpochMs = endTimeMs,
                            durationMs = 0L,
                            recordingQuality = RecordingQuality.UNSUPPORTED,
                            captureTier = CallCaptureTier.OEM_IMPORT,
                            unrecordedReason = "OEM Collision Safety: ${importResult.diagnosticReason}"
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            dao?.insertCall(CallEntity.fromDomain(unrecordedSession))
                        }
                    }
                }
                CallRecordingJournal.recordEnd(this)
            }
            else -> {
                releaseWakeLock()
                CallRecordingJournal.recordEnd(this)
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mobiltool::CallCaptureWakeLock")?.apply {
            acquire(45 * 60 * 1000L) // 45-minute safety threshold
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification during legitimate active call capture"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (isRecordingActive) {
            stopActiveCapture()
        }
        super.onDestroy()
    }
}
