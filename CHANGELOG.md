# SmartCam Pro - Changelog

## Version 1.1.0 (2026-07-18) - Professional Security Platform

### NEU: Echtes WebRTC Streaming
- **Signaling Server** mit vollständiger SDP/ICE-Verhandlung
- **STUN/TURN Unterstützung** via Google STUN Servers
- **Peer-to-Peer Video Streaming** mit niedriger Latenz
- **Room-basiertes Management** (Kamera + Clients)
- Android WebRTC Client mit Signaling WebSocket
- Frontend WebRTCViewer Komponente mit Auto-Reconnect

### NEU: Echte Videoaufnahme
- **RecordingEngine** mit OpenCV VideoWriter
- **Aufnahme bei Bewegung** (automatisch oder manuell)
- **MJPG/AVI Format** für breite Kompatibilität
- **Automatische Speicherverwaltung** mit konfigurierbarer Aufbewahrung
- **Periodisches Cleanup** (konfigurierbar: Tage)
- **Speicherinfo-API** mit Dateianzahl und Größe
- Frontend Record-Button mit Session-Management

### NEU: Erweiterte KI-Erkennung
- **YOLO Auto-Setup**: Automatischer Download der Model-Dateien
- **Multi-Class Detection**: Personen, Fahrzeuge, Tiere
- **CUDA Support**: GPU-Beschleunigung wenn verfügbar
- **Konfigurierbare Erkennungszonen** mit Canvas-Editor
- **Zone-basierte Trigger** mit Cooldown und Klassenfilter
- **Sensitivity pro Zone** einstellbar
- **Class Group Mapping**: person/vehicle/animal/bicycle

### NEU: Erweiterte Sicherheit
- **Rate Limiting** mit konfigurierbaren Limits pro Endpoint
  - Login: 5 req/min
  - Register: 3 req/5min
  - API: 120 req/min
- **Audit Logging** mit JSONL-Format
  - Alle API-Aktionen protokolliert
  - IP-Adresse, User-Agent, Timestamp
  - Query-Filter nach Action/User
- **Session Management** mit Max-Sessions pro User
  - Session-Listing und Revocation
  - Auto-Cleanup abgelaufener Sessions
  - Timeout-Konfiguration

### NEU: Frontend Erweiterungen
- **WebRTCViewer** Komponente mit echtem P2P
- **ZoneEditor** mit Canvas-Zeichenfunktion
- **AISettingsPage** für KI-Konfiguration
  - Model-Download/Load Status
  - Detector-Konfiguration
  - Speicher-Management
  - Session-Übersicht
  - Audit-Log-Viewer
- **v1api.ts** Service für alle neuen Endpunkte
- Updated Sidebar mit "KI & Erweitert" Navigation
- Version Badge "v1.1" im Header

### NEU: API Endpunkte (v1)
```
GET  /api/v1/webrtc/rooms/{camera_id}  - WebRTC Room-Info
GET  /api/v1/webrtc/rooms              - Alle Rooms
POST /api/v1/recording/start/{camera_id} - Aufnahme starten
POST /api/v1/recording/stop/{session_id} - Aufnahme stoppen
GET  /api/v1/recording/storage          - Speicherinfo
POST /api/v1/recording/cleanup          - Alte Dateien löschen
GET  /api/v1/ai/model/status            - YOLO Status
POST /api/v1/ai/model/download          - Model herunterladen
POST /api/v1/ai/model/load              - Model laden
POST /api/v1/ai/detect                  - Frame erkennen
GET  /api/v1/ai/detector/config         - Erkennungskonfig
PUT  /api/v1/ai/detector/config         - Config ändern
GET  /api/v1/zones/{camera_id}          - Zonen abrufen
POST /api/v1/zones/{camera_id}          - Zone erstellen
DELETE /api/v1/zones/{zone_id}          - Zone löschen
GET  /api/v1/audit/logs                 - Audit Logs
GET  /api/v1/sessions                   - Eigene Sessions
DELETE /api/v1/sessions/{session_id}    - Session beenden
DELETE /api/v1/sessions/all             - Alle Sessions beenden
```

### WebSocket Signaling
```
WS /ws/webrtc/camera/{camera_id}       - Kamera Signaling
WS /ws/webrtc/client/{camera_id}       - Client Signaling
```

### Fixes aus v1.0
- Alle 18 Sicherheitsfixe aus v1.0 beibehalten
- Boolean-Typen korrigiert (is_active, is_read)
- Timezone-aware Datetime überall
- Ownership-Verifizierung bei allen DELETE-Operationen

---

## Version 1.0.0 (2026-07-18) - Initial Release
Siehe CHANGELOG.md im v1.0 Branch für Details.
