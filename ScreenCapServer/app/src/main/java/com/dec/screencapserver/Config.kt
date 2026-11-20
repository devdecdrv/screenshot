package com.dec.screencapserver

/**
 * ════════════════════════════════════════════════════
 *  CONFIG — यहाँ अपना token और ID डालो
 * ════════════════════════════════════════════════════
 *
 *  TELEGRAM_BOT_TOKEN  → "9281773269:AAEftdC2zATUIo7xSFd_Stb5zxtAK-mx4gs"
 *                        example: "7123456789:AAHxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
 *
 *  TELEGRAM_CHAT_ID    → "8889476230"
 *                        @userinfobot को message करके मिलेगा
 *                        example: "123456789"
 *
 *  NGROK_AUTHTOKEN     → "3LLtRyJIu7vI4iZtpMmm2Xp61L0_57aoeZBKMHFeZDhdkZyrM"
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
    const val TELEGRAM_BOT_TOKEN  = "9281773269:AAEftdC2zATUIo7xSFd_Stb5zxtAK-mx4gs"
    const val TELEGRAM_CHAT_ID    = "8889476230"
    const val NGROK_AUTHTOKEN     = "3LLtRyJIu7vI4iZtpMmm2Xp61L0_57aoeZBKMHFeZDhdkZyrM"
    const val CAPTURE_INTERVAL_MS = 300L
    const val WEB_SERVER_PORT     = 8080
}
