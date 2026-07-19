# SmartCam Pro v3.0 - Professionelle Multi-User Sicherheitsplattform

## APK Download
🔗 https://raw.githubusercontent.com/yazeedmustafa57-bit/Kamera-xPro-/main/SmartCamPro.apk

## Architektur (wie AlfredCamera)
```
Kamera-Geraet → HTTPS/WSS → Backend Server → HTTPS/WSS → Zuschauer-Geraet
                                │
                          WebRTC P2P
                          (direkt zwischen Geraeten)
```

## Backend (Node.js + PostgreSQL)
```bash
cd backend
npm install
# PostgreSQL Datenbank einrichten
# .env.example kopieren und ausfuellen
npm start
```

## Features
- ☁️ Cloud-Streaming (ueberall zugaenglich)
- 🔐 JWT Auth mit Refresh Tokens
- 📷 Live-Video mit CameraX
- 🚶 KI-Bewegungserkennung
- 🚨 Alarm-Sirene
- 📱 QR-Code Pairing
- 🔐 Verschluesselte Token-Speicherung
- 💰 Subscription-System

## Sicherheit
- Passwoerter: bcrypt (12 Runden)
- Tokens: EncryptedSharedPreferences
- API: JWT + Owner-ID Filterung
- Server: Rate Limiting + Helmet
- Transport: HTTPS/WSS

## Technologien
- Backend: Node.js + Express + PostgreSQL
- Realtime: Socket.IO
- Auth: JWT + bcrypt
- Android: Kotlin + Retrofit + CameraX
- WebRTC: Socket.IO Signaling (P2P bereit)
