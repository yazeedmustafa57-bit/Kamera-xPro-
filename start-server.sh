#!/bin/bash
cd "$(dirname "$0")/backend"

# Kill any existing server
pkill -f "node src/server.js" 2>/dev/null
sleep 1

# Kill any existing tunnel
pkill -f cloudflared 2>/dev/null
sleep 1

# Start server
echo "Starting SmartCam Pro Server..."
PORT=3000 nohup node src/server.js > /tmp/smartcam-server.log 2>&1 &
SERVER_PID=$!
sleep 2

# Check server
if curl -s http://127.0.0.1:3000/api/health > /dev/null 2>&1; then
    echo "Server running on port 3000 (PID: $SERVER_PID)"
else
    echo "ERROR: Server failed to start"
    cat /tmp/smartcam-server.log
    exit 1
fi

# Start cloudflare tunnel
echo "Starting Cloudflare Tunnel..."
nohup cloudflared tunnel --url http://127.0.0.1:3000 > /tmp/smartcam-tunnel.log 2>&1 &
TUNNEL_PID=$!
sleep 8

# Extract tunnel URL
TUNNEL_URL=$(grep -o 'https://[a-z0-9-]*.trycloudflare.com' /tmp/smartcam-tunnel.log | head -1)
echo "Tunnel URL: $TUNNEL_URL (PID: $TUNNEL_PID)"
echo ""
echo "Server: http://localhost:3000"
echo "Tunnel: $TUNNEL_URL"
echo ""
echo "Open in browser: $TUNNEL_URL/api/health"
