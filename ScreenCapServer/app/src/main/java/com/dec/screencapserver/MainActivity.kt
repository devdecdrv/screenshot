package com.dec.screencapserver

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val PROJECTION_REQUEST = 100
    }

    private lateinit var mpm: MediaProjectionManager
    lateinit var statusTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTv = findViewById(R.id.tvStatus)
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivityForResult(mpm.createScreenCaptureIntent(), PROJECTION_REQUEST)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, WebServerService::class.java))
            statusTv.text = "Stopped."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJECTION_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            startService(Intent(this, WebServerService::class.java))

            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(intent)
            // Start audio capture with same projection token
val audioIntent = Intent(this, AudioCaptureService::class.java).apply {
    putExtra("resultCode", resultCode)
    putExtra("data", data)
}
startForegroundService(audioIntent)
            statusTv.text = "Starting... tunnel creating..."
        }
    }

    fun updateStatus(msg: String) {
        runOnUiThread { statusTv.text = msg }
    }
}
