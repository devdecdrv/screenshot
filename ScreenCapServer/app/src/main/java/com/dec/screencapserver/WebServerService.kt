package com.dec.screencapserver

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD

class WebServerService : Service() {

    private lateinit var server: CapServer

    companion object {
        const val CHANNEL_ID = "web_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, buildNotification())
        server = CapServer(Config.WEB_SERVER_PORT)
        server.start()
    }

    override fun onDestroy() {
        server.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SysService Web")
            .setContentText("Port ${Config.WEB_SERVER_PORT}")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Web Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

class CapServer(port: Int) : NanoHTTPD(port) {

    private val viewerHtml = """
        <!DOCTYPE html>
        <html>
        <head>
          <title>Live View</title>
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <style>
            * { margin:0; padding:0; box-sizing:border-box; }
            body { background:#0a0a0a; display:flex; flex-direction:column;
                   align-items:center; justify-content:center; min-height:100vh; }
            img { max-width:100%; max-height:92vh; object-fit:contain;
                  border:1px solid #222; }
            #bar { color:#0f0; font:12px monospace; padding:6px 12px;
                   background:#111; width:100%; text-align:center; }
          </style>
        </head>
        <body>
          <div id="bar">connecting...</div>
          <img id="f" src="/frame" alt="screen">
          <script>
            const img = document.getElementById('f');
            const bar = document.getElementById('bar');
            let n = 0, t0 = Date.now();
            function next() {
              img.src = '/frame?_=' + Date.now();
              n++;
              const fps = (n / ((Date.now() - t0) / 1000)).toFixed(1);
              bar.textContent = 'frames: ' + n + '  |  fps: ' + fps;
            }
            img.onload  = () => setTimeout(next, ${Config.CAPTURE_INTERVAL_MS - 50});
            img.onerror = () => setTimeout(next, 1000);
            next();
          </script>
        </body>
        </html>
    """.trimIndent()

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/frame"  -> serveFrame()
            "/status" -> serveStatus()
            else      -> newFixedLengthResponse(Response.Status.OK, "text/html", viewerHtml)
        }
    }

    private fun serveFrame(): Response {
        val jpeg = ScreenCaptureService.latestJpeg
            ?: return newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE, "text/plain", "no frame yet"
            )
        val r = newFixedLengthResponse(
            Response.Status.OK, "image/jpeg",
            jpeg.inputStream(), jpeg.size.toLong()
        )
        r.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        r.addHeader("Access-Control-Allow-Origin", "*")
        return r
    }

    private fun serveStatus(): Response {
        val json = """{"frames":${ScreenCaptureService.frameCount},"ts":${ScreenCaptureService.frameTimestamp},"interval":${Config.CAPTURE_INTERVAL_MS}}"""
        val r = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        r.addHeader("Access-Control-Allow-Origin", "*")
        return r
    }
}
