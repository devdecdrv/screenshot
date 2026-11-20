# ScreenCapServer

## Config.kt में क्या डालना है

फाइल: `app/src/main/java/com/dec/screencapserver/Config.kt`

```kotlin
const val TELEGRAM_BOT_TOKEN  = "7123456789:AAHxxxxx..."   // @BotFather से
const val TELEGRAM_CHAT_ID    = "123456789"                 // @userinfobot से
const val NGROK_AUTHTOKEN     = "2abc123_xxxxxxxx..."       // dashboard.ngrok.com
const val CAPTURE_INTERVAL_MS = 1000L                       // ms between screenshots
```

## Setup

### Telegram Bot
1. Telegram में `@BotFather` को message करो → `/newbot`
2. Token copy करो → `TELEGRAM_BOT_TOKEN`
3. `@userinfobot` को message करो → ID मिलेगी → `TELEGRAM_CHAT_ID`

### ngrok (HTTPS tunnel)
1. https://ngrok.com पर free account बनाओ
2. https://dashboard.ngrok.com/get-started/your-authtoken पर authtoken copy करो
3. `NGROK_AUTHTOKEN` में डालो

### GitHub Secrets (CI के लिए)
```
KEYSTORE_BASE64  → base64 -w0 release.keystore
KEYSTORE_PASS    → keystore password
KEY_ALIAS        → key alias
KEY_PASS         → key password
```

Keystore generate:
```bash
keytool -genkey -v -keystore release.keystore -alias deckey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass yourpass -keypass yourpass \
  -dname "CN=Dec, OU=Dev, O=Dec, L=IN, S=IN, C=IN"
base64 -w0 release.keystore
```

## Phone पर install के बाद

1. App खोलो → "Start Capture" tap करो
2. Screen capture permission दो
3. Settings → Accessibility → SysService → Enable करो
4. Telegram पर HTTPS link आएगा
5. उस link को Claude MCP config में डालो

## Tunnel Priority
1. ngrok API (authtoken set है तो)
2. Cloudflare Quick Tunnel API (no account needed)
3. Local IP fallback
