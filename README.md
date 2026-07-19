# SmartCam Pro v2.0 - Cloud Sicherheitskamera-System

## APK Download
🔗 https://raw.githubusercontent.com/yazeedmustafa57-bit/Kamera-xPro-/main/SmartCamPro.apk

## Architektur
```
┌──────────────────┐         Internet          ┌──────────────┐
│  Android Tablet  │◄────────────────────────►│  Cloud Server│
│  (Kamera)        │  WebSocket + REST API     │  (FastAPI)   │
└──────────────────┘                           └──────────────┘
        │                                            │
        └────────────────────────────────────────────┘
                         │
                         ▼
                 ┌──────────────┐
                 │  iPhone/PC   │
                 │  (Zuschauer) │
                 │  Web-Browser │
                 └──────────────┘
```

## Features
- ☁️ Cloud-Streaming (ueberall zugaenglich)
- 📷 Live-Video mit CameraX
- 🚶 KI-Bewegungserkennung
- 🚨 Alarm-Sirene mit Vibration
- 🔦 Taschenlampe (auto bei Bewegung)
- 🎬 Video-Aufnahme
- 📸 Screenshots
- 📱 QR-Code zum Verbinden
- 🔐 Benutzerverwaltung mit JWT
- 💰 Subscription-System (Free/Pro/Business)

## Subscription-Plaene
| Plan | Kameras | Speicher | Preis |
|------|---------|----------|-------|
| Free | 1 | 500 MB | 0 €/Monat |
| Pro | 10 | 10 GB | 9.99 €/Monat |
| Business | 100 | 100 GB | 29.99 €/Monat |

## Cloud-Server deployen

### Option 1: Railway (empfohlen)
1. GitHub-Repo verbinden
2. Railway erkennt automatisch den Dockerfile
3. Deploy-Button klicken
4. URL kopieren

### Option 2: Render
1. GitHub-Repo verbinden
2. "New Web Service" waehlen
3. Dockerfile als Build-Befehl
4. Port: 8000

### Option 3: Docker lokal
```bash
cd backend
docker build -t smartcampro .
docker run -p 8000:8000 smartcampro
```

## Server lokal starten
```bash
cd backend
pip install -r requirements.txt
python -m app.main
```

## API-Dokumentation
Nach Server-Start: http://localhost:8000/docs

## Android App
1. APK installieren
2. App oeffnen
3. "Mit Cloud anmelden" oder "Skip"
4. Kamera starten
5. QR-Code mit iPhone scannen
