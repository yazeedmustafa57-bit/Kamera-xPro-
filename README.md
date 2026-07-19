# SmartCam Pro v1.6 - Professionelles Sicherheitskamera-System

## Zuschauer-Modus (NEU! 📱)

### So funktioniert es:
1. **Tablet (Kamera):** Starte die App und tippe auf "📷 Kamera starten (ohne Server)"
2. **QR-Code anzeigen:** Tippe auf das 📱-Symbol unten
3. **iPhone (Zuschauer):** Scanne den QR-Code mit der Kamera-App
4. **Fertig!** Du siehst das Live-Bild auf dem iPhone

### Voraussetzungen:
- Beide Geräte im gleichen WLAN
- Kein PC erforderlich
- Kein Internet erforderlich

### Schritt-für-Schritt:
1. SmartCam Pro auf dem Tablet installieren
2. App öffnen und auf "📷 Kamera starten (ohne Server)" tippen
3. Kamera startet automatisch
4. Auf das 📱-Symbol unten tippen
5. QR-Code wird angezeigt
6. iPhone-Kamera öffnen und QR-Code scannen
7. Safari öffnet sich mit dem Live-Bild

### Features im Zuschauer-Modus:
- Live-Bild in Echtzeit
- Batteriestatus des Tablets
- WLAN-Signal des Tablets
- Anzahl verbundener Zuschauer
- Screenshot-Funktion
- Vollbild-Modus
- Automatische Wiederverbindung

---

## Server-Modus (mit PC)

### Installation:
```bash
# Backend starten
cd backend
pip install -r requirements.txt
python main.py

# Frontend starten
cd frontend
npm install
npm run dev
```

### Features:
- Dashboard mit Live-Kamera-Views
- Bewegungserkennung mit KI
- Alarm-System mit Benachrichtigungen
- Videoaufnahmen und Speicherung
- Benutzerverwaltung mit Rollen
- Dark Theme Design

---

## Technische Details:

### Streaming-Protokoll:
- **MJPEG über HTTP** (Port 8080)
- Kompatibel mit jedem Browser (Safari, Chrome, Firefox)
- Keine spezielle App auf dem Zuschauer-Gerät nötig

### Sicherheit:
- Nur im lokalen WLAN erreichbar
- Kein Internet-Zugang erforderlich
- Verschlüsselte Passwörter (bcrypt)
- JWT Token-Authentifizierung

### Kompatibilität:
- Android 7.0+ (Tablet als Kamera)
- Jedes Gerät mit Browser (iPhone, Android, PC als Zuschauer)
