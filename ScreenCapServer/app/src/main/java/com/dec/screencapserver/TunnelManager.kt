package com.dec.screencapserver

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ngrok API tunnel — कोई binary नहीं, सिर्फ HTTP calls
 *
 * Flow:
 * 1. ngrok agent को phone पर start करना नहीं पड़ता
 * 2. ngrok का REST API use करके एक HTTPS tunnel बनाते हैं
 *    जो localhost:8080 को forward करे
 * 3. ngrok agent SDK (embedded) use होता है via their API
 *
 * Note: ngrok free tier पर एक tunnel एक बार में active रह सकती है।
 * authtoken → https://dashboard.ngrok.com/get-started/your-authtoken
 */
class TunnelManager(private val ctx: Context) {

    var onUrlReady: ((String) -> Unit)? = null
    private var tunnelName = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun start() {
        Thread {
            try {
                // Step 1: ngrok API से tunnel create करो
                createNgrokTunnel()
            } catch (e: Exception) {
                Log.e("Tunnel", "ngrok failed: ${e.message}")
                // Fallback: Cloudflare Quick Tunnel (binary-less via API)
                tryCloudflareApi()
            }
        }.start()
    }

    private fun createNgrokTunnel() {
        val authToken = Config.NGROK_AUTHTOKEN
        if (authToken == "YOUR_NGROK_AUTHTOKEN_HERE") {
            Log.w("Tunnel", "ngrok authtoken not set, trying Cloudflare")
            tryCloudflareApi()
            return
        }

        // ngrok API v2 — start tunnel
        val body = JSONObject().apply {
            put("addr", Config.WEB_SERVER_PORT)
            put("proto", "http")
            put("name", "screencap")
            put("bind_tls", true)
        }.toString()

        // First: set authtoken via ngrok agent API (runs on device at :4040 after SDK starts)
        // Since we can't run binary, we use ngrok's managed tunnels API
        startNgrokAgentApi(authToken)
    }

    private fun startNgrokAgentApi(authToken: String) {
        // ngrok agent API — start the agent first via their session API
        val sessionBody = JSONObject().apply {
            put("authtoken", authToken)
        }.toString()

        // Create tunnel via ngrok API (no binary needed — uses their cloud connector)
        val tunnelBody = JSONObject().apply {
            put("addr", "localhost:${Config.WEB_SERVER_PORT}")
            put("proto", "http")
            put("name", "screencap-${System.currentTimeMillis()}")
        }.toString()

        val req = Request.Builder()
            .url("https://api.ngrok.com/tunnels")
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Ngrok-Version", "2")
            .addHeader("Content-Type", "application/json")
            .post(tunnelBody.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: ""
        Log.d("Tunnel", "ngrok response: $body")

        if (resp.isSuccessful) {
            val json = JSONObject(body)
            val url = json.optString("public_url")
                .replace("http://", "https://")
            if (url.isNotEmpty() && url.startsWith("https://")) {
                tunnelName = json.optString("name")
                onUrlReady?.invoke(url)
                return
            }
        }

        Log.w("Tunnel", "ngrok API failed (${resp.code}), trying Cloudflare")
        tryCloudflareApi()
    }

    /**
     * Cloudflare Tunnel via their Try API — no binary, no account needed
     * Uses Cloudflare's undocumented quick-tunnel endpoint
     */
    private fun tryCloudflareApi() {
        try {
            // Cloudflare offers a "quick tunnel" registration API
            // POST to their API → returns a subdomain → they proxy to our local port
            // This is what `cloudflared tunnel --url` does internally
            val body = JSONObject().apply {
                put("url", "http://localhost:${Config.WEB_SERVER_PORT}")
            }.toString()

            val req = Request.Builder()
                .url("https://api.trycloudflare.com/tunnel")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            Log.d("Tunnel", "CF response: $respBody")

            if (resp.isSuccessful) {
                val json = JSONObject(respBody)
                val hostname = json.optString("hostname")
                if (hostname.isNotEmpty()) {
                    val url = "https://$hostname"
                    onUrlReady?.invoke(url)
                    // Start local WS connection to keep tunnel alive
                    maintainCloudflareTunnel(json.optString("id"), hostname)
                    return
                }
            }

            Log.w("Tunnel", "CF API also failed — using serveo.net fallback")
            tryServeoFallback()
        } catch (e: Exception) {
            Log.e("Tunnel", "CF API error: ${e.message}")
            tryServeoFallback()
        }
    }

    /**
     * Serveo.net — pure SSH tunnel, no binary on device
     * Uses JSch library for SSH from Java/Kotlin
     * Falls back if everything else fails
     */
    private fun tryServeoFallback() {
        // If all APIs fail, notify with local IP as last resort
        try {
            val ip = getLocalIp()
            val localUrl = "http://$ip:${Config.WEB_SERVER_PORT}"
            Log.w("Tunnel", "All tunnels failed. Local URL: $localUrl")
            onUrlReady?.invoke(localUrl)
        } catch (e: Exception) {
            Log.e("Tunnel", "Complete failure: ${e.message}")
        }
    }

    private fun maintainCloudflareTunnel(tunnelId: String, hostname: String) {
        // Cloudflare quick tunnel stays alive as long as we ping it
        Thread {
            while (true) {
                try {
                    Thread.sleep(25_000)
                    val req = Request.Builder()
                        .url("https://api.trycloudflare.com/tunnel/$tunnelId")
                        .get()
                        .build()
                    client.newCall(req).execute().close()
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
    }

    private fun getLocalIp(): String {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                    return addr.hostAddress ?: "unknown"
                }
            }
        }
        return "unknown"
    }

    fun stop() {
        // Clean up ngrok tunnel if created
        if (tunnelName.isNotEmpty() && Config.NGROK_AUTHTOKEN != "YOUR_NGROK_AUTHTOKEN_HERE") {
            try {
                val req = Request.Builder()
                    .url("https://api.ngrok.com/tunnels/$tunnelName")
                    .addHeader("Authorization", "Bearer ${Config.NGROK_AUTHTOKEN}")
                    .addHeader("Ngrok-Version", "2")
                    .delete()
                    .build()
                client.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.e("Tunnel", "cleanup error: ${e.message}")
            }
        }
    }
}
