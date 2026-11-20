package com.dec.screencapserver

import android.app.*
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

class AudioCaptureService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    companion object {
        const val CHANNEL_ID  = "audio_service"
        const val SAMPLE_RATE = 44100
        const val CHANNELS    = AudioFormat.CHANNEL_IN_STEREO
        const val ENCODING    = AudioFormat.ENCODING_PCM_16BIT

        // Rolling buffer — WebServerService streams this as audio/wav
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

        startAudioCapture()
        return START_STICKY
    }

    private fun startAudioCapture() {
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
        val bufSize = minBuf * 4

        audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNELS)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .build()

        audioRecord?.startRecording()
        isCapturing = true

        captureThread = Thread {
            val buf = ByteArray(bufSize)
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
        audioRecord?.stop()
        audioRecord?.release()
        mediaProjection.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SysService Audio")
            .setContentText("Capturing audio")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Audio Capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
