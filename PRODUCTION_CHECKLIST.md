# SmartCam Pro - Produktions-Checkliste

## VOR DEM DEPLOYMENT

### Sicherheit
- [ ] `SECRET_KEY` in `.env` auf sicheren Zufallswert ändern (min. 32 Zeichen)
- [ ] Standard-Passwörter ändern (PostgreSQL, Redis)
- [ ] HTTPS/TLS-Zertifikat installieren (Let's Encrypt)
- [ ] CORS_ORIGINS auf prod-Domain beschränken
- [ ] Debug-Modus deaktivieren (`echo=False` in DB)
- [ ] `ALLOW_ORIGINS` keine Wildcards in Produktion
- [ ] Firewall: Nur Ports 80/443 offen
- [ ] SSH-Key-basierte Authentifizierung (kein Password-Login)

### Datenbank
- [ ] PostgreSQL 16+ installiert und konfiguriert
- [ ] Datenbank-Backup-Plan eingerichtet (täglich)
- [ ] Alembic Migrations ausgeführt (`alembic upgrade head`)
- [ ] DB-Connection-Pool getunst (pool_size, max_overflow)
- [ ] SSL-Verbindung für Production-DB aktiviert

### Server
- [ ] Python 3.11+ installiert
- [ ] Virtual Environment erstellt und Dependencies installiert
- [ ] Uvicorn mit Gunicorn (4 Worker) gestartet
- [ ] Process-Manager (systemd/supervisor) konfiguriert
- [ ] Log-Rotation eingerichtet
- [ ] Monitoring (Prometheus/Grafana) aktiv
- [ ] Speicherplatz-Monitoring aktiv

### Frontend
- [ ] `npm run build` erfolgreich (keine TypeScript-Fehler)
- [ ] Nginx konfiguriert (Proxy, SSL, Gzip)
- [ ] Bundle-Optimierung (Tree-Shaking, Code-Splitting)
- [ ] CSP-Header (Content Security Policy) gesetzt
- [ ] Cache-Control-Header für statische Assets

### Docker
- [ ] Docker-Images optimiert (Multi-Stage Build)
- [ ] Keine secrets in Docker-Images
- [ ] Health-Checks konfiguriert
- [ ] Resource-Limits gesetzt (CPU, Memory)
- [ ] Volumes für Persistenz konfiguriert

### Android App
- [ ] ProGuard/R8 für Release-Build aktiviert
- [ ] API-Keys aus Code entfernt
- [ ] SSL-Pinning implementiert
- [ ] MinSDK >= 24 (Android 7.0)
- [ ] APK-Signierung mit Release-Key

## NACH DEM DEPLOYMENT

### Verifikation
- [ ] Health-Endpunkt antwortet (`/api/health`)
- [ ] Login funktioniert (neuer Account)
- [ ] Kamera kann sich verbinden (WebSocket)
- [ ] Live-Stream funktioniert
- [ ] Bewegungserkennung löst Alarm aus
- [ ] E-Mail-Benachrichtigung funktioniert
- [ ] Dashboard zeigt korrekte Stats
- [ ] Aufnahmen werden gespeichert und abgespielt
- [ ] Responsive Design auf Mobile funktioniert

### Performance
- [ ] API-Antwortzeiten < 200ms
- [ ] WebSocket Latenz < 100ms
- [ ] Video-Streaming FPS >= 15
- [ ] DB-Query-Zeiten < 50ms
- [ ] Speicherverbrauch stabil

### Backup & Recovery
- [ ] Tägliches DB-Backup getestet
- [ ] Recording-Backup auf externen Storage
- [ ] Restore-Verfahren dokumentiert und getestet
- [ ] Disaster-Recovery-Plan vorhanden

## BETRIEB

### Wartung
- [ ] Wöchentliche Security-Updates
- [ ] Monatliche Abhängigkeits-Updates
- [ ] Log-Auswertung wöchentlich
- [ ] Speicherplatz-Bereinigung (alte Aufnahmen)
- [ ] SSL-Zertifikat-Aktualisierung (auto via certbot)

### Monitoring-Alerts
- [ ] Server-Down Alert
- [ ] DB-Verbindung Alert
- [ ] Speicherplatz > 80% Alert
- [ ] API-Fehlerrate > 5% Alert
- [ ] WebSocket-Verbindungsabbrüche Alert

## VERSIONSNUMMER

```
SmartCam Pro v1.0.0
Release Date: 2026-07-18
Status: PRODUCTION READY (nach Durchführung der Checkliste)
```
