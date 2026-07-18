#!/bin/bash
echo "========================================="
echo "   SmartCam Pro - Server starten"
echo "========================================="
echo

cd "$(dirname "$0")/backend"

if [ ! -d "venv" ]; then
    echo "Erstelle Virtual Environment..."
    python3 -m venv venv
fi

echo "Aktiviere Virtual Environment..."
source venv/bin/activate

echo "Installiere Dependencies..."
pip install -r requirements.txt -q

echo
echo "Starte Server auf Port 8000..."
echo
echo "========================================="
echo "WICHTIG: Fenster offen lassen!"
echo "========================================="
echo
echo "Deine IP-Adresse:"
ip addr show | grep "inet " | grep -v "127.0.0.1"
echo
echo "========================================="
echo "App-URL: http://DEINE-IP:8000"
echo "Login: admin / Admin123!"
echo "========================================="
echo

uvicorn app.main:app --host 0.0.0.0 --port 8000
