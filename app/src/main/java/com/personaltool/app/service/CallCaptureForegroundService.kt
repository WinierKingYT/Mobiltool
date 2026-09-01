package com.personaltool.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.personaltool.app.PersonalToolApplication
import com.personaltool.app.capture.AudioFileInspector
import com.personaltool.app.capture.CallCaptureCapabilityDetector
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

    private var recorder: MediaRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L
    private var activePhoneNumber: String = "UNKNOWN"
    private var activeDirection: CallDirection = CallDirection.INCOMING

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
                activePhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown Caller"
                val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, true)
                activeDirection = if (isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING
                startForegroundWithNotification()
                startRecordingSession()
            }
            ACTION_STOP_CALL_CAPTURE -> {
                stopRecordingSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mobiltool // Active Call Capture")
            .setContentText("Recording in progress for $activePhoneNumber")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun startRecordingSession() {
        acquireWakeLock()
        val dir = File(filesDir, "calls").apply { mkdirs() }
        val callId = UUID.randomUUID().toString()
        val file = File(dir, "call_${callId}_${System.currentTimeMillis()}.m4a")
        currentOutputFile = file
        startTimeMs = System.currentTimeMillis()

        runCatching {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            val audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION

            mediaRecorder.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
        }.onFailure {
            runCatching {
                val fallbackRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    MediaRecorder()
                }
                fallbackRecorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                recorder = fallbackRecorder
            }
        }
    }

    private fun stopRecordingSession() {
        val file = currentOutputFile
        val endTimeMs = System.currentTimeMillis()
        val durationMs = (endTimeMs - startTimeMs).coerceAtLeast(0L)

        runCatching {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null
        }

        releaseWakeLock()

        if (file != null && file.exists()) {
            val capability = CallCaptureCapabilityDetector.detectCapability(this)
            val inspection = AudioFileInspector.inspectRecordedFile(file.absolutePath, capability.expectedQuality)

            if (inspection.isValid) {
                val app = applicationContext as? PersonalToolApplication
                val dao = app?.database?.callDao()

                val session = CallSession(
                    id = UUID.randomUUID().toString(),
                    phoneNumber = activePhoneNumber,
                    contactName = null,
                    direction = activeDirection,
                    startTimeEpochMs = startTimeMs,
                    endTimeEpochMs = endTimeMs,
                    durationMs = inspection.durationMs,
                    recordingQuality = inspection.determinedQuality,
                    captureTier = capability.tier,
                    isLoudspeakerActive = capability.isLoudspeakerOn,
                    audioFilePath = file.absolutePath,
                    fileSizeBytes = inspection.fileSizeBytes,
                    hasTranscript = false,
                    isFavorite = false
                )

                CoroutineScope(Dispatchers.IO).launch {
                    dao?.insertCall(CallEntity.fromDomain(session))
                }
            } else {
                file.delete()
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mobiltool::CallCaptureWakeLock")?.apply {
            acquire(45 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
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
        stopRecordingSession()
        super.onDestroy()
    }
}
