# SmartCam Pro - Vollständige Installationsanleitung

## 1. Backend Server

### Voraussetzungen
- Python 3.11+
- PostgreSQL 16+
- Redis (optional)

### Installation

```bash
cd backend

# Virtual Environment erstellen
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows

# Dependencies installieren
pip install -r requirements.txt

# Umgebung konfigurieren
cp ../.env.example .env
# .env Datei anpassen (SECRET_KEY, Datenbank, etc.)

# Server starten
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# YOLO Model herunterladen (erster Start)
curl -X POST http://localhost:8000/api/v1/ai/model/download
curl -X POST http://localhost:8000/api/v1/ai/model/load
```

### Docker

```bash
cd SmartCamPro
cp .env.example .env
docker-compose up -d

# Services:
# - Backend:  http://localhost:8000
# - Frontend: http://localhost:3000
# - Database: localhost:5432
```

## 2. Frontend Dashboard

### Voraussetzungen
- Node.js 18+

### Installation

```bash
cd frontend
npm install
npm run dev
# Öffnet sich unter http://localhost:3000
```

### Production Build

```bash
npm run build
# Output: dist/ Ordner
```

## 3. Android App (APK)

### Methode 1: Android Studio
1. Android Studio öffnen
2. `android-app/` Ordner als Projekt öffnen
3. SDK installieren lassen
4. Build > Build APK(s)
5. APK auf Handy installieren

### Methode 2: Command Line
```bash
cd android-app
chmod +x gradlew
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

### Methode 3: GitHub Actions
1. Code in GitHub Repository pushen
2. GitHub Actions baut automatisch die APK
3. Unter Actions > Artifacts die APK herunterladen

### APK Installation auf Android
1. APK-Datei auf das Handy übertragen
2. In den Einstellungen > Apps > Unbekannte Apps erlauben
3. APK öffnen und installieren
4. SmartCam Pro starten

## 4. Ersteinrichtung

### Server-URL konfigurieren
1. SmartCam Pro auf dem alten Handy öffnen
2. Server-URL eingeben (z.B. `https://dein-server.com`)
3. Kamera-Name vergeben
4. Login-Daten eingeben
5. Verbinden

### Dashboard
1. Im Browser http://localhost:3000 öffnen
2. Account erstellen (erster Account wird Admin)
3. Kameras hinzufügen
4. KI-Modell herunterladen (KI & Erweitert > Model herunterladen)

### Bewegungszonen
1. Kamera öffnen
2. Zone-Editor aktivieren (Stift-Symbol)
3. Punkte auf die Kamera-Ansicht klicken
4. Zone speichern

## 5. Port-Übersicht

| Port | Service | Beschreibung |
|------|---------|-------------|
| 8000 | Backend API | REST API + WebSocket |
| 3000 | Frontend | React Dashboard |
| 5432 | PostgreSQL | Datenbank |
| 6379 | Redis | Cache (optional) |

## 6. Sicherheit

### Erste Schritte
1. `SECRET_KEY` in `.env` ändern
2. Standard-Datenbank-Passwort ändern
3. HTTPS mit Let's Encrypt einrichten
4. Firewall: Nur Ports 80/440 offen

### Empfohlen
- Cloudflare oder Nginx als Reverse Proxy
- Automatische SSL-Zertifikate
- Regelmäßige Backups
- Rate Limiting (bereits aktiv)
