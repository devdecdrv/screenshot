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
    * { margin:0; padding:0; box-sizing:border-box; }
    body { background:#000; display:flex; flex-direction:column;
           align-items:center; min-height:100vh; }
    #bar { width:100%; background:#111; color:#0f0; font:11px monospace;
           padding:4px 10px; display:flex; justify-content:space-between;
           align-items:center; gap:8px; }
    #f { width:100%; height:calc(100vh - 30px); object-fit:contain; display:block; }
    button { background:#1a7a1a; color:#fff; border:none; padding:3px 10px;
             font-size:11px; cursor:pointer; border-radius:3px; }
    button.on { background:#7a1a1a; }
    audio { display:none; }
  </style>
</head>
<body>
  <div id="bar">
    <span id="info">connecting...</span>
    <button id="abtn" onclick="toggleAudio()">🔇 Audio OFF</button>
  </div>
  <img id="f" src="/frame" alt="">
  <audio id="aud" autoplay></audio>

  <script>
    const img  = document.getElementById('f');
    const info = document.getElementById('info');
    const aud  = document.getElementById('aud');
    const abtn = document.getElementById('abtn');
    let n = 0, t0 = Date.now(), audioOn = false;

    // ── Screen stream ──────────────────────────────────────────
    function nextFrame() {
      img.src = '/frame?_=' + Date.now();
    }
    img.onload = function() {
      n++;
      const elapsed = (Date.now() - t0) / 1000;
      const fps = elapsed > 0 ? (n / elapsed).toFixed(1) : '0';
      info.textContent = 'frames: ' + n + '  |  fps: ' + fps;
      requestAnimationFrame(nextFrame);
    };
    img.onerror = function() {
      setTimeout(nextFrame, 500);
    };
    nextFrame();

    // ── Audio stream ───────────────────────────────────────────
    function toggleAudio() {
      if (!audioOn) {
        aud.src = '/audio?_=' + Date.now();
        aud.play().catch(function(e) { console.log('audio:', e); });
        audioOn = true;
        abtn.textContent = '🔊 Audio ON';
        abtn.className = 'on';
      } else {
        aud.pause();
        aud.src = '';
        audioOn = false;
        abtn.textContent = '🔇 Audio OFF';
        abtn.className = '';
      }
    }
  </script>
</body>
</html>
    """.trimIndent()

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
        r.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        r.addHeader("Access-Control-Allow-Origin", "*")
        return r
    }

    private fun serveAudio(): Response {
        val pipe  = PipedOutputStream()
        val input = PipedInputStream(pipe, 131072)

        Thread {
            try {
                pipe.write(buildWavHeader())
                pipe.flush()
                while (true) {
                    val chunk = AudioCaptureService.latestPcmChunk
                    if (chunk != null) {
                        pipe.write(chunk)
                        pipe.flush()
                    }
                    Thread.sleep(10)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { pipe.close() }
            }
        }.also { it.isDaemon = true }.start()

        val r = newChunkedResponse(Response.Status.OK, "audio/wav", input)
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Cache-Control", "no-cache, no-store")
        r.addHeader("Connection", "keep-alive")
        return r
    }

    private fun buildWavHeader(
        sampleRate: Int    = AudioCaptureService.SAMPLE_RATE,
        channels: Int      = 2,
        bitsPerSample: Int = 16
    ): ByteArray {
        val h          = ByteArray(44)
        val byteRate   = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        fun w2(v: Int, o: Int) {
            h[o]   = (v and 0xFF).toByte()
            h[o+1] = (v shr 8 and 0xFF).toByte()
        }
        fun w4(v: Int, o: Int) { w2(v, o); w2(v shr 16, o + 2) }
        fun str(s: String, o: Int) = s.toByteArray().copyInto(h, o)

        str("RIFF", 0);  w4(0x7FFFFFFF, 4);  str("WAVE", 8)
        str("fmt ", 12); w4(16, 16);          w2(1, 20)
        w2(channels, 22);    w4(sampleRate, 24)
        w4(byteRate, 28);    w2(blockAlign, 32)
        w2(bitsPerSample, 34)
        str("data", 36); w4(0x7FFFFFFF, 40)
        return h
    }

    private fun serveStatus(): Response {
        val json = """{"frames":${ScreenCaptureService.frameCount},"ts":${ScreenCaptureService.frameTimestamp},"audio":${AudioCaptureService.isCapturing}}"""
        val r = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        r.addHeader("Access-Control-Allow-Origin", "*")
        return r
    }
}
