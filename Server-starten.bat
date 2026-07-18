@echo off
echo ========================================
echo    SmartCam Pro - Server starten
echo ========================================
echo.

cd /d "%~dp0backend"

if not exist venv (
    echo Erstelle Virtual Environment...
    python -m venv venv
)

echo Aktiviere Virtual Environment...
call venv\Scripts\activate

echo Installiere Dependencies...
pip install -r requirements.txt -q

echo.
echo Starte Server auf Port 8000...
echo.
echo ========================================
echo WICHTIG: Fenster offen lassen!
echo ========================================
echo.
echo Deine IP-Adresse:
ipconfig | findstr /i "IPv4"
echo.
echo ========================================
echo App-URL: http://DEINE-IP:8000
echo Login: admin / Admin123!
echo ========================================
echo.

uvicorn app.main:app --host 0.0.0.0 --port 8000
pause
