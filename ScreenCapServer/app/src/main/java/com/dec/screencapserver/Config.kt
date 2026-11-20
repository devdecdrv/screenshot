package com.dec.screencapserver

/**
 * ════════════════════════════════════════════════════
 *  CONFIG — यहाँ अपना token और ID डालो
 * ════════════════════════════════════════════════════
 *
 *  TELEGRAM_BOT_TOKEN  → @BotFather से मिला token
 *                        example: "7123456789:AAHxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
 *
 *  TELEGRAM_CHAT_ID    → अपना chat ID
 *                        @userinfobot को message करके मिलेगा
 *                        example: "123456789"
 *
 *  NGROK_AUTHTOKEN     → https://dashboard.ngrok.com/get-started/your-authtoken
 *                        Free account पर मिलता है
 *                        example: "2abc123_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
 *
 *  CAPTURE_INTERVAL_MS → screenshot interval
 *                        1000 = 1 per second
 *                         500 = 2 per second
 *                         200 = 5 per second
 * ════════════════════════════════════════════════════
 */
object Config {
    const val TELEGRAM_BOT_TOKEN  = "YOUR_BOT_TOKEN_HERE"
    const val TELEGRAM_CHAT_ID    = "YOUR_CHAT_ID_HERE"
    const val NGROK_AUTHTOKEN     = "YOUR_NGROK_AUTHTOKEN_HERE"
    const val CAPTURE_INTERVAL_MS = 1000L
    const val WEB_SERVER_PORT     = 8080
}
