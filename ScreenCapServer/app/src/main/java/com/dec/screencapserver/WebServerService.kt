package com.dec.screencapserver

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.PipedInputStream
import java.io.PipedOutputStream

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
  <title>Live</title>
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <style>
    *{margin:0;padding:0;box-sizing:border-box}
    body{background:#000;display:flex;flex-direction:column;
         align-items:center;min-height:100vh}
    #bar{width:100%;background:#111;color:#0f0;font:11px monospace;
         padding:4px 8px;display:flex;justify-content:space-between;align-items:center}
    #f{width:100%;height:calc(100vh - 28px);object-fit:contain}
    audio{height:28px;width:220px}
    button{background:#1a1;color:#fff;border:none;padding:4px 10px;
           font-size:11px;cursor:pointer;border-radius:3px}
  </style>
</head>
<body>
  <div id="bar">
    <span id="info">connecting...</span>
    <audio id="aud" autoplay></audio>
    <button onclick="toggleAudio()">🔊 Audio</button>
  </div>
  <img id="f" src="/frame">

  <script>
    const img  = document.getElementById('f');
    const info = document.getElementById('info');
    const aud  = document.getElementById('aud');
    let n=0, t0=Date.now(), audioOn=false;

    // ── Screen stream ──────────────────────────────
    function next() {
      img.src = '/frame?_=' + Date.now();
    }
    img.onload  = () => { 
      n++;
      const fps = (n/((Date.now()-t0)/1000)).toFixed(1);
      info.textContent = 'frames:'+n+' fps:'+fps;
      requestAnimationFrame(next);
    };
    img.onerror = () => setTimeout(next, 500);
    next();

    // ── Audio stream ───────────────────────────────
    function toggleAudio() {
      if (!audioOn) {
        // Use MediaSource for low-latency PCM streaming
        aud.src = '/audio';
        aud.play().catch(()=>{});
        audioOn = true;
      } else {
        aud.src = '';
        audioOn = false;
      }
    }
  </script>
</body>
</html>
    """.trimIndent()

    // WAV header for raw PCM stream
    private fun wavHeader(sampleRate: Int = 44100, channels: Int = 2): ByteArray {
        // Streaming WAV — data chunk size = 0xFFFFFFFF (unknown)
        val h = ByteArray(44)
        fun int2(v: Int, off: Int) { h[off]=(v and 0xFF).toByte(); h[off+1]=(v shr 8 and 0xFF).toByte() }
        fun int4(v: Int, off: Int) { h[off]=(v and 0xFF).toByte(); h[off+1]=(v shr 8 and 0xFF).toByte(); h[off+2]=(v shr 16 and 0xFF).toByte(); h[off+3]=(v shr 24 and 0xFF).toByte() }
        "RIFF".toByteArray().copyInto(h, 0)
        int4(0x7FFFFFFF, 4)
        "WAVE".toByteArray().copyInto(h, 8)
        "fmt ".toByteArray().copyInto(h, 12)
        int4(16, 16); int2(1, 20); int2(channels, 22)
        int4(sampleRate, 24); int4(sampleRate * channels * 2, 28)
        int2(channels * 2, 32); int2(16, 34)
        "data".toByteArray().copyInto(h, 36)
        int4(0x7FFFFFFF, 40)
        return h
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/frame"  -> serveFrame()
            "/audio"  -> serveAudio()
            "/status" -> serveStatus()
            else      -> newFixedLengthResponse(Response.Status.OK, "text/html", viewerHtml)
        }
    }

    private fun serveFrame(): Response {
        val jpeg = ScreenCaptureService.latestJpeg
            ?: return newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE, "text/plain", "no frame"
            )
        val r = newFixedLengthResponse(
            Response.Status.OK, "image/jpeg",
            jpeg.inputStream(), jpeg.size.toLong()
        )
        r.addHeader("Cache-Control", "no-cache, no-store")
        r.addHeader("Access-Control-Allow-Origin", "*")
        return r
    }

    private fun serveAudio(): Response {
        // Chunked streaming WAV — browser plays as it receives
        val pipe = PipedOutputStream()
        val input = PipedInputStream(pipe, 65536)

        Thread {
            try {
                pipe.write(wavHeader())
                while (true) {
                    val chunk = AudioCaptureService.latestPcmChunk
                    if (chunk != null) {
                        pipe.write(chunk)
                        pipe.flush()
                    }
                    Thread.sleep(20)
                }
            } catch (_: Exception) {}
            finally { pipe.close() }
        }.start()

        val r = newChunkedResponse(Response.Status.OK, "audio/wav", input)
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Cache-Control", "no-cache")
        return r
    }

    private fun serveStatus(): Response {
        val json = """{"frames":${ScreenCaptureService.frameCount},"ts":${ScreenCaptureService.frameTimestamp}}"""
        val r = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        r.addHeader("Access-Control-Allow-Origin", "*")
        return r
    }
}
