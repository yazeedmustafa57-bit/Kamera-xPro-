# SmartCam Pro v3.3 - Professionelle Sicherheitsplattform

## APK Download
🔗 https://raw.githubusercontent.com/yazeedmustafa57-bit/Kamera-xPro-/main/SmartCamPro-v3.3.apk

## Quick Start
1. APK installieren
2. E-Mail + Passwort eingeben (Server-URL ist bereits vorausgefüllt)
3. Registrieren oder Anmelden
4. "Kamera-Modus" wählen → altes Handy wird zur Überwachungskamera
5. Auf iPhone/anderem Handy: Gleichen Account einloggen → "Zuschauer" wählen → Live-Stream ansehen

## Features
- 📷 Live-HD-Video (1280x720) via Socket.IO
- 🔦 Taschenlampe remote ein/ausschalten
- 🔄 Kamera wechseln (Front/Rück) remote
- 🚨 Alarm auslösen (vibration + LED blink)
- 📱 QR-Code zum Verbinden
- 🔐 JWT Auth mit verschlüsselten Tokens
- 🔋 Batteriestatus-Anzeige
- 👥 Zuschauer-Anzeige
- 🌐 Web-Zuschauer (iPhone/Safari nutzbar)

## So funktioniert es
```
Kamera-Handy (Android)  →  Cloud-Server  →  Zuschauer (iPhone/Android/Web)
  CameraX + Socket.IO       Express+WS        Socket.IO Viewer
```

## Technologien
- **Backend:** Node.js + Express + Socket.IO
- **Datenbank:** JSON-File (data.json)
- **Auth:** JWT + bcrypt
- **Android:** Kotlin + CameraX + Socket.IO
- **Web:** HTML5 + Socket.IO Client
- **Tunnel:** Cloudflare Quick Tunnel

## Sicherheit
- Passwörter: bcrypt (12 Runden)
- Tokens: JWT mit Owner-ID Filterung
- API: Nur authentifizierte Zugriffe
- Server: CORS + Helmet

## Server starten
```bash
cd backend
npm install
PORT=3000 node src/server.js
```

## Changelog
### v3.3 (2026-07-20)
- FIX: Grüner Bildschirm behoben (korrekte YUV→JPEG Konvertierung)
- FIX: HD-Qualität (1280x720, JPEG Quality 60)
- FIX: Remote-Flash, Remote-Switch, Remote-Alarm von iPhone
- UPDATE: Server-URL aktualisiert

### v3.2
- Multi-User System (Registrierung/Login)
- Remote-Controls (Flash, Switch, Alarm)
- Web-Zuschauer für iPhone
