# 📹 SmartCam Pro v1.2

> **Professionelles Sicherheitskamera-System** – Verwandle dein altes Smartphone in eine professionelle Überwachungskamera.

[![Version](https://img.shields.io/badge/version-1.2.0-green)]()
[![APK](https://img.shields.io/badge/APK-download-blue)]()
[![License](https://img.shields.io/badge/license-MIT-orange)]()

---

## ⚡ Schnellstart (2 Minuten)

### 1. APK herunterladen
**👉 https://github.com/yazeedmustafa57-bit/Kamera-xPro-/releases/tag/v1.2**

- Klicke auf **SmartCamPro-v1.2.apk**
- Lade die Datei herunter
- Installiere sie auf deinem Android-Gerät

### 2. Server auf PC starten

```bash
# Windows: Doppelklick auf "Server-starten.bat"
# Oder manuell:
cd backend
python -m venv venv
venv\Scripts\activate          # Windows
source venv/bin/activate       # Mac/Linux
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 3. Deine PC-IP finden

**Windows:** `Windows+R` → `cmd` → `ipconfig` → **IPv4-Adresse**

**Mac:** Systemeinstellungen → Netzwerk → WLAN

### 4. App öffnen und anmelden

- **Server-URL:** `http://DEINE-PC-IP:8000`
- **Benutzername:** `admin`
- **Passwort:** `Admin123!`

---

## 📱 APK Installation

### Download
1. Öffne: https://github.com/yazeedmustafa57-bit/Kamera-xPro-/releases/tag/v1.2
2. Klicke auf **SmartCamPro-v1.2.apk**
3. Lade die Datei herunter

### Installation
1. Öffne die heruntergeladene APK-Datei
2. Bestätige die Installation
3. Bei "Unbekannte App": **Erlauben** klicken

### ⚠️ Download funktioniert nicht?
- Kopiere die APK per USB auf dein Tablet
- Oder sende sie dir selbst per WhatsApp/E-Mail

---

## 🖥️ Server auf PC starten

### Voraussetzungen
- [Python 3.10+](https://www.python.org/downloads/)
- [Git](https://git-scm.com/downloads)

### Windows (einfach)
1. Doppelklick auf `Server-starten.bat`
2. Terminal öffnet sich und startet den Server
3. **Terminal offen lassen!**

### Mac/Linux
```bash
chmod +x server-starten.sh
./server-starten.sh
```

### Manuell
```bash
git clone https://github.com/yazeedmustafa57-bit/Kamera-xPro-.git
cd Kamera-xPro-/backend
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

---

## 📷 Kameras

### Kamera-Modus (altes Smartphone als Kamera)
- Öffne SmartCam Pro App
- Server-URL, Benutzername, Passwort eingeben
- **Anmelden** drücken
- Kamera startet automatisch

### Dashboard (Web-Browser)
```bash
cd frontend
npm install
npm run start
```
Öffne: http://localhost:3000

---

## 🔐 Login-Daten

| Feld | Wert |
|------|------|
| Benutzername | `admin` |
| Passwort | `Admin123!` |

---

## 🏗️ Projektstruktur

```
SmartCamPro/
├── backend/           # FastAPI Python Server
├── frontend/          # React Dashboard
├── android-app/       # Android Kotlin App
├── Server-starten.bat # Windows Server-Skript
├── server-starten.sh  # Linux/Mac Server-Skript
├── INSTALLATION_DE.md # Deutsche Anleitung
└── README.md          # Diese Datei
```

---

## ⚠️ Häufige Probleme

### "Server nicht erreichbar"
- ✅ Server auf PC gestartet?
- ✅ PC und Tablet im selben WLAN?
- ✅ Richtige IP-Adresse eingegeben?
- ✅ Firewall erlaubt Port 8000?

### "Falscher Benutzername oder Passwort"
- Benutzername: `admin` (klein geschrieben!)
- Passwort: `Admin123!` (großes A, Ausrufezeichen!)

### "Unbekannte App installieren"
- Einstellungen → Sicherheit → Unbekannte Apps
- Browser/Dateimanager erlauben

---

## 📋 Checkliste

- [ ] Python auf PC installiert
- [ ] Server gestartet (Terminal offen!)
- [ ] PC-IP-Adresse gemerkt
- [ ] APK auf Tablet installiert
- [ ] Server-URL in App eingegeben
- [ ] Login mit admin / Admin123!
- [ ] Kamera funktioniert

---

*SmartCam Pro v1.2 – Professionelles Sicherheitssystem*
