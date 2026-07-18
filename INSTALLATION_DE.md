# 📹 SmartCam Pro - Installationsanleitung (Deutsch)

## ⚠️ WICHTIG: So funktioniert das System

SmartCam Pro besteht aus **2 Teilen**:

1. **Backend-Server** → läuft auf deinem PC/Laptop
2. **Android-App** → läuft auf deinem Honor Magic Pad 4

**Beide müssen im selben WLAN sein!**

---

## Schritt 1: Backend auf deinem PC starten

### Voraussetzungen
- Python 3.10 oder neuer (download: https://www.python.org/downloads/)
- Git (download: https://git-scm.com/downloads)

### Backend installieren

```bash
# 1. Repository klonen
git clone https://github.com/yazeedmustafa57-bit/Kamera-xPro-.git
cd Kamera-xPro-

# 2. Python Virtual Environment erstellen
cd backend
python -m venv venv

# 3. Aktivieren (Windows)
venv\Scripts\activate

# 4. Aktivieren (Mac/Linux)
source venv/bin/activate

# 5. Dependencies installieren
pip install -r requirements.txt

# 6. Server starten
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### Server läuft wenn du siehst:
```
INFO:     Uvicorn running on http://0.0.0.0:8000
```

### Terminal OFFEN lassen! Der Server muss laufen.

---

## Schritt 2: Deine PC-IP-Adresse finden

### Windows:
1. Drücke `Windows + R`
2. Tippe `cmd` und drücke Enter
3. Tippe `ipconfig` und drücke Enter
4. Suche nach **"IPv4-Adresse"** (z.B. `192.168.1.50`)

### Mac:
1. Öffne **Systemeinstellungen** → **Netzwerk**
2. Klicke auf dein WLAN
3. Die IP-Adresse steht dort (z.B. `192.168.1.50`)

### Linux:
```bash
ip addr show
```

**Merke dir diese IP-Adresse!**

---

## Schritt 3: APK auf dem Tablet installieren

### APK herunterladen:
1. Öffne den Browser auf deinem Honor Magic Pad 4
2. Gehe zu: https://github.com/yazeedmustafa57-bit/Kamera-xPro-/releases
3. Klicke auf **"SmartCamPro-v1.1.apk"**
4. Lade die Datei herunter
5. Wenn der Download fertig ist, öffne die Datei
6. Bestätige die Installation

**Wenn der Download nicht funktioniert:**
- Kopiere die APK-Datei per USB auf das Tablet
- Oder sende sie dir selbst per E-Mail/WhatsApp

---

## Schritt 4: App konfigurieren

### Auf deinem Honor Magic Pad 4:

1. Öffne die **SmartCam Pro** App
2. Gib ein:
   - **Server-URL:** `http://DEINE-PC-IP:8000`
     (z.B. `http://192.168.1.50:8000`)
   - **Kamera-Name:** `Kamera 1`
   - **Benutzername:** `admin`
   - **Passwort:** `Admin123!`
3. Klicke auf **"Anmelden"**

---

## Schritt 5: Frontend Dashboard (optional)

Wenn du auch das Web-Dashboard nutzen willst:

```bash
# In einem neuen Terminal
cd frontend
npm install
npm run start
```

Öffne dann: http://localhost:3000

Login: `admin` / `Admin123!`

---

## ⚠️ Häufige Probleme

### "Server nicht erreichbar"
- Ist der Server auf deinem PC gestartet?
- Sind PC und Tablet im selben WLAN?
- Stimmt die IP-Adresse?

### "Falscher Benutzername oder Passwort"
- Benutzername: `admin`
- Passwort: `Admin123!`
- Groß-/Kleinschreibung beachten!

### Download funktioniert nicht
- APK per USB auf das Tablet kopieren
- Oder per E-Mail/WhatsApp senden

### "Unbekannte App installieren"
- Gehe zu: Einstellungen → Sicherheit → Unbekannte Apps installieren
- Erlaube den Browser/Dateimanager

---

## 📋 Checkliste

- [ ] Python auf PC installiert
- [ ] Backend gestartet (Terminal offen lassen!)
- [ ] PC-IP-Adresse gemerkt
- [ ] APK auf Tablet installiert
- [ ] Server-URL in App eingeben (http://DEINE-IP:8000)
- [ ] Login mit admin / Admin123!
- [ ] Kamera funktioniert

---

## 🔧 Admin Account erstellen

Der Admin-Account `admin / Admin123!` ist bereits vorkonfiguriert.

Falls du einen neuen Account brauchst:
```bash
# Auf dem PC im Backend-Terminal:
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"deinname","email":"deine@email.de","password":"DeinPasswort123!","role":"user"}'
```

---

## 📱 App herunterladen

**APK-Download:** https://github.com/yazeedmustafa57-bit/Kamera-xPro-/releases

Falls der Download nicht funktioniert, kopiere die APK-Datei direkt:
1. Öffne den Link oben im Browser deines Tablets
2. Scrolle zu "Assets"
3. Klicke auf "SmartCamPro-v1.1.apk"
4. Bestätige die Installation

---

*SmartCam Pro v1.2 - Professionelles Sicherheitssystem*
