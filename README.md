# 📹 SmartCam Pro v1.1.0

> **Professional Security Camera Platform** – Verwandle ein altes Smartphone in eine professionelle Überwachungskamera.

[![Build Status](https://img.shields.io/badge/build-APK%20ready-green)]()
[![Version](https://img.shields.io/badge/version-1.1.0-blue)]()
[![License](https://img.shields.io/badge/license-MIT-orange)]()

---

## 📱 APK Installation

### Download

Die APK wird über GitHub Actions automatisch gebaut:
1. GitHub öffnen → **Actions** → **Build SmartCam Pro APK**
2. **Run workflow** klicken
3. Unter **Artifacts** die `SmartCamPro-release.apk` herunterladen

Oder manuell bauen:
```bash
# Voraussetzung: JDK 17 + Android SDK
cd android-app
chmod +x gradlew
./gradlew assembleRelease

# APK: app/build/outputs/apk/release/app-release.apk
```

### APK auf Android installieren

1. **APK-Datei** auf das Handy übertragen (USB, Bluetooth, Cloud)
2. **Einstellungen** → **Apps** → **Unbekannte Apps** → Erlauben
3. APK-Datei öffnen → **Installieren**
4. SmartCam Pro öffnen

> ⚠️ Android 8.0+ erfordert "Unbekannte Quellen" für die Installation.

---

## 🚀 Erste Einrichtung

### Schritt 1: Server starten

```bash
# Docker (empfohlen)
cp .env.example .env
docker-compose up -d

# Oder manuell
cd backend
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### Schritt 2: Dashboard öffnen

```
http://deine-server-ip:3000
```

1. **Registrieren** – Erster Account wird automatisch Admin
2. **Kamera hinzufügen** – Kameras → + Kamera
3. **KI-Modell herunterladen** – KI & Erweitert → Model herunterladen

### Schritt 3: Android App konfigurieren

1. SmartCam Pro App öffnen
2. **Server URL**: `https://deine-server.com` (oder `http://IP:8000`)
3. **Kamera Name**: z.B. "Eingangstür"
4. **Username/Password**: Deine Login-Daten
5. **Connect** drücken

---

## 📷 Kamera-Modus

Der Kamera-Modus verwandelt dein Android-Smartphone in eine aktive Überwachungskamera.

### Aktivierung

1. App öffnen → Server-URL + Login eingeben → **Connect**
2. Die Kamera startet automatisch
3. Live-Video wird an den Server gestreamt

### Funktionen

| Funktion | Beschreibung |
|----------|-------------|
| 🔴 **Live Stream** | Echtzeit-Video über WebSocket an Dashboard |
| 🔄 **Kamera wechseln** | Vorder-/Rückseite umschalten |
| 📸 **Screenshot** | Manuell oder automatisch bei Bewegung |
| 🎬 **Aufnahme** | Video aufnehmen (Start/Stop) |
| 🔋 **Status** | Batterie + WLAN-Signal wird gemeldet |
| 🏃 **Bewegung** | Automatische Erkennung im Hintergrund |
| 📲 **Background** | Läuft als Foreground Service |

### Automatische Funktionen

- **Bewegungserkennung**: Erkennt Personen, Fahrzeuge, Tiere
- **Screenshots**: Bei Bewegung automatisch gespeichert
- **Status-Updates**: Alle 10 Sekunden an Server gesendet
- **Alarme**: Bei Erkennung wird Dashboard benachrichtigt

### Kamera-Steuerung

```
┌─────────────────────────────────────┐
│  ● Live          🔋 85%    📶 92%  │
│                                     │
│         [Live Video Stream]         │
│                                     │
│  [Switch] [Photo] [Record] [Stop]  │
└─────────────────────────────────────┘
```

---

## 👁️ Zuschauer-Modus (Dashboard)

Der Zuschauer-Modus ist das Web-Dashboard zum Beobachten und Verwalten.

### Dashboard

```
┌─────────────────────────────────────────────────┐
│  SmartCam Pro v1.1            ● System Online   │
├─────────────────────────────────────────────────┤
│                                                 │
│  📊 Stats: 5 Kameras | 3 Online | 12 Alarme    │
│                                                 │
│  📷 Kamera 1    📷 Kamera 2    📷 Kamera 3     │
│  [Live View]    [Live View]    [Live View]      │
│  ● Online       ● Offline      ● Recording      │
│  🔋 85%         🔋 42%         🔋 91%           │
│                                                 │
│  🚨 Letzte Alarme:                              │
│  - Person erkannt (Eingangstür)  14:23          │
│  - Bewegung erkannt (Garage)     14:15          │
└─────────────────────────────────────────────────┘
```

### Kamera-Verwaltung

1. **Live-Stream**: WebRTC P2P mit niedriger Latenz
2. **Vollbild**: Maximiere eine einzelne Kamera
3. **Mikrofon/Lautsprecher**: Audio zu-/abschalten
4. **Screenshot**: Direkt aus dem Dashboard
5. **Aufnahme**: Server-seitige Videoaufnahme

### Aufnahmen

```
📹 Aufnahmen
├── 2024-01-15 14:23 - Eingangstür (30s)
├── 2024-01-15 13:10 - Garage (15s)
└── 2024-01-15 12:05 - Eingangstür (45s)
```

- Automatisch bei Bewegung
- Nach Datum sortiert
- Direkt im Browser abspielbar
- Speicherverwaltung (automatisches Aufräumen)

### Alarme & Benachrichtigungen

| Typ | Beschreibung |
|-----|-------------|
| 🔵 **Person** | Mensch erkannt |
| 🟢 **Fahrzeug** | Auto/Truck erkannt |
| 🟡 **Tier** | Tier erkannt |
| 🟣 **Bewegung** | Allgemeine Bewegung |

- Echtzeit-Push im Dashboard
- E-Mail-Benachrichtigung (optional)
- Konfigurierbare Erkennungszonen
- Empfindlichkeit einstellbar

### Benutzerverwaltung

- **Admin**: Volle Kontrolle, kann User verwalten
- **Benutzer**: Kann Kameras sehen, keine Admin-Funktionen
- Max. 5 parallele Sessions pro User
- Session-Management mit Revocation

---

## 🔧 Erweiterte Funktionen (v1.1)

### KI-Erkennung

```bash
# YOLO Model herunterladen (einmalig)
curl -X POST http://localhost:8000/api/v1/ai/model/download
curl -X POST http://localhost:8000/api/v1/ai/model/load
```

- **YOLO v4-tiny**: Personen, Fahrzeuge, Tiere
- **CUDA Support**: GPU-Beschleunigung (wenn verfügbar)
- **Auto-Download**: Model wird beim ersten Start geladen

### Erkennungszonen

1. Kamera öffnen → Zone-Editor aktivieren
2. Punkte auf die Kamera-Ansicht klicken (min. 3)
3. Zone benennen und Empfindlichkeit einstellen
4. Nur definierte Bereiche werden überwacht

### WebRTC Streaming

- **P2P-Verbindung**: Direkt zwischen Kamera und Zuschauer
- **STUN/TURN**: Über Firewalls hinweg
- **Niedrige Latenz**: <100ms
- **Auto-Reconnect**: Bei Verbindungsverlust

### Sicherheit

- **JWT Authentifizierung**: Sichere API-Zugriffe
- **Rate Limiting**: Login 5/min, API 120/min
- **Audit Logging**: Alle Aktionen protokolliert
- **HTTPS/TLS**: Verschlüsselte Verbindung
- **Role-Based Access**: Admin vs. Benutzer

---

## 📂 Projektstruktur

```
SmartCam Pro/
├── backend/                 # Python FastAPI Server
│   ├── app/
│   │   ├── api/             # REST API Endpunkte
│   │   ├── core/            # Security, Rate Limiting, Audit
│   │   ├── models/          # Datenbank-Modelle
│   │   ├── services/        # KI, Aufnahme, Benachrichtigung
│   │   └── webrtc/          # WebRTC Signaling Server
│   └── tests/               # Test Suite (30+ Tests)
│
├── frontend/                # React Dashboard
│   └── src/
│       ├── components/      # WebRTCViewer, ZoneEditor
│       ├── pages/           # Dashboard, Kameras, KI-Einstellungen
│       └── services/        # API Clients
│
├── android-app/             # Android Kamera-App
│   └── app/src/main/
│       ├── java/            # Kotlin Source Code
│       └── res/             # Layouts, Icons
│
├── docker-compose.yml       # Docker Setup
├── build-apk.sh            # APK Build Script
└── .github/workflows/       # CI/CD (optional)
```

---

## 🐳 Docker

```bash
# Starten
cp .env.example .env
docker-compose up -d

# Services:
# - Backend:  http://localhost:8000
# - Frontend: http://localhost:3000
# - Database: localhost:5432

# Stoppen
docker-compose down
```

---

## 📖 API Dokumentation

Wenn der Server läuft:
- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

### Schnellstart

```bash
# Registrieren
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@test.com","password":"Admin123"}'

# Login
curl -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123"}'

# Health Check
curl http://localhost:8000/api/health
```

---

## 🛠️ Troubleshooting

| Problem | Lösung |
|---------|--------|
| APK installiert nicht | "Unbekannte Quellen" aktivieren |
| App verbindet nicht | Server-URL prüfen, HTTPS/HTTP |
| Kamera startet nicht | Kamera-Berechtigung erteilen |
| Kein Video | WebSocket-Verbindung prüfen |
| Build fehlgeschlagen | JDK 17, Android SDK 34 installieren |

---

## 📄 Lizenz

MIT License – Frei nutzbar und modifizierbar.

---

**SmartCam Pro v1.1.0** – Professionelles Sicherheitssystem für zu Hause.
