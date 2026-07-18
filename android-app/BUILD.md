# SmartCam Pro APK - Build Anleitung

## Systemanforderung

| Komponente | Version |
|------------|---------|
| OS | Linux (x86_64) / macOS / Windows |
| JDK | 17+ |
| Android SDK | API 34, Build-Tools 34.0.0 |
| RAM | Mindestens 4 GB |

## Methode 1: Android Studio (Empfohlen)

1. Android Studio herunterladen: https://developer.android.com/studio
2. Projekt öffnen: `android-app/` Ordner auswählen
3. SDK installieren: File > Settings > Android SDK > SDK 34 installieren
4. Build: Build > Build Bundle(s) / APK(s) > Build APK(s)
5. APK finden: `app/build/outputs/apk/release/app-release.apk`

## Methode 2: Gradle Command Line

```bash
cd android-app

# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (mit Signing)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

## Methode 3: Docker Build (x86_64)

```bash
cd android-app

docker build -f Dockerfile.build -t smartcam-builder .

# APK aus Container kopieren
docker create --name builder smartcam-builder
docker cp builder:/app/app/build/outputs/apk/release/app-release.apk ./app-release.apk
docker rm builder
```

## Methode 4: GitHub Actions CI/CD

Kopiere die `.github/workflows/build.yml` in dein Repository.
Der Build läuft automatisch bei jedem Push.

## Signing

### Debug (automatisch)
```bash
./gradlew assembleDebug
```

### Release (manuell)
```bash
# Keystore wurde erstellt unter: app/keystore/smartcam-release.jks
# Passwort: smartcam123

# Alternativ: Eigenen Keystore erstellen
keytool -genkeypair -alias smartcam -keyalg RSA -keysize 2048 \
  -validity 36500 -keystore my-release.jks

# In app/build.gradle anpassen:
# signingConfigs.release.storeFile = file("keystore/my-release.jks")
```

## Installation auf Android Gerät

1. APK-Datei auf das Gerät übertragen
2. In den Einstellungen > Sicherheit > "Unbekannte Quellen" aktivieren
3. APK-Datei öffnen und installieren
4. SmartCam Pro starten und Server-URL konfigurieren

## Troubleshooting

| Problem | Lösung |
|---------|--------|
| `SDK not found` | `ANDROID_HOME` setzen oder `local.properties` anpassen |
| `Out of memory` | `org.gradle.jvmargs=-Xmx4096m` in `gradle.properties` |
| `AAPT2 failed` | JDK 17 verwenden, `./gradlew clean` ausführen |
| `Signing failed` | Keystore-Passwort prüfen |
