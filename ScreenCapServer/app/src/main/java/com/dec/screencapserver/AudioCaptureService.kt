package com.dec.screencapserver

import android.app.*
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat

class AudioCaptureService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    companion object {
        const val CHANNEL_ID  = "audio_service"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO
        const val ENCODING    = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_SIZE  = 4096

        @Volatile var latestPcmChunk: ByteArray? = null
        @Volatile var isCapturing = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(3, buildNotification())

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        startCapture()
        return START_STICKY
    }

    private fun startCapture() {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
            .build()

        val minBuf = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING),
            CHUNK_SIZE * 4
        )

        audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_MASK)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            // Fallback: mic capture
            startMicCapture()
            return
        }

        audioRecord?.startRecording()
        isCapturing = true

        captureThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = ByteArray(CHUNK_SIZE)
            while (isCapturing) {
                val read = audioRecord?.read(buf, 0, buf.size) ?: break
                if (read > 0) {
                    latestPcmChunk = buf.copyOf(read)
                }
            }
        }.also { it.start() }
    }

    private fun startMicCapture() {
        // Fallback to microphone if playback capture fails
        val minBuf = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING),
            CHUNK_SIZE * 4
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_MASK,
            ENCODING,
            minBuf
        )

        audioRecord?.startRecording()
        isCapturing = true

        captureThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = ByteArray(CHUNK_SIZE)
            while (isCapturing) {
                val read = audioRecord?.read(buf, 0, buf.size) ?: break
                if (read > 0) {
                    latestPcmChunk = buf.copyOf(read)
                }
            }
        }.also { it.start() }
    }

    override fun onDestroy() {
        isCapturing = false
        captureThread?.interrupt()
        audioRecord?.stop()
        audioRecord?.release()
        mediaProjection.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SysService Audio")
            .setContentText("Capturing")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Audio", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
