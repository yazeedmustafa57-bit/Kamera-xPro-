package com.smartcampro.app.server

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class LocalStreamingServer(
    private val port: Int = 8080,
    private val onStatusUpdate: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var latestFrame: ByteArray? = null
    private var batteryLevel: Int = 100
    private var wifiSignal: Int = 100
    private var cameraName: String = "Kamera"
    private var motionCount: Int = 0
    private var isRunning = false
    private var clientCount = 0

    // Notifications
    private val notifications = mutableListOf<Map<String, String>>()
    private val maxNotifications = 20
    private var latestNotification: String? = null

    private val clients = ConcurrentLinkedQueue<Socket>()
    private val executor = Executors.newFixedThreadPool(10)
    private var serverThread: Thread? = null

    fun start() {
        try {
            serverSocket = ServerSocket(port)
            serverSocket?.reuseAddress = true
            isRunning = true
            onStatusUpdate("Server läuft auf Port $port")
            Log.d("LocalServer", "Server started on port $port")

            serverThread = thread(isDaemon = true) {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: continue
                        clients.add(client)
                        clientCount = clients.size
                        executor.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e("LocalServer", "Accept error", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalServer", "Failed to start server", e)
            onStatusUpdate("Server-Fehler: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        try {
            clients.forEach { try { it.close() } catch (_: Exception) {} }
            clients.clear()
            serverSocket?.close()
            serverSocket = null
            serverThread?.interrupt()
            executor.shutdownNow()
            Log.d("LocalServer", "Server stopped")
        } catch (e: Exception) {
            Log.e("LocalServer", "Stop error", e)
        }
    }

    fun pushFrame(bitmap: Bitmap) {
        if (!isRunning) return
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            latestFrame = stream.toByteArray()
        } catch (e: Exception) {
            Log.e("LocalServer", "Frame push error", e)
        }
    }

    fun addNotification(message: String, type: String = "motion") {
        val notification = mapOf(
            "message" to message,
            "type" to type,
            "timestamp" to System.currentTimeMillis().toString()
        )
        synchronized(notifications) {
            notifications.add(0, notification)
            if (notifications.size > maxNotifications) {
                notifications.removeAt(notifications.size - 1)
            }
            latestNotification = message
        }
    }

    fun updateStatus(battery: Int, wifi: Int, name: String, motions: Int) {
        batteryLevel = battery
        wifiSignal = wifi
        cameraName = name
        motionCount = motions
    }

    fun getLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun getUrl(): String = "http://${getLocalIp()}:$port"

    fun getClientCount(): Int = clientCount

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val request = readRequest(input)

            when {
                request.startsWith("GET /stream") -> handleStreamClient(socket, output)
                request.startsWith("GET /status") -> handleStatus(output)
                request.startsWith("GET /notifications") -> handleNotifications(output)
                request.startsWith("GET /events") -> handleEvents(output)
                request.startsWith("GET /favicon") -> {
                    socket.close()
                    clients.remove(socket)
                    clientCount = clients.size
                }
                request.startsWith("GET / ") || request.startsWith("GET /HTTP") -> handleViewerPage(output)
                else -> handleViewerPage(output)
            }
        } catch (e: Exception) {
            Log.d("LocalServer", "Client disconnected: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
            clients.remove(socket)
            clientCount = clients.size
        }
    }

    private fun readRequest(input: InputStream): String {
        val sb = StringBuilder()
        var lastChar = ' '
        var lineCount = 0
        while (lineCount < 50) {
            val c = input.read()
            if (c == -1) break
            sb.append(c.toChar())
            val ch = c.toChar()
            if (ch == '\n' && lastChar == '\r') {
                lineCount++
                if (sb.endsWith("\r\n\r\n") || sb.endsWith("\n\n")) break
            }
            lastChar = ch
        }
        return sb.toString().trim()
    }

    private fun handleStreamClient(socket: Socket, output: OutputStream) {
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                "Cache-Control: no-cache, no-store\r\n" +
                "Connection: close\r\n" +
                "Pragma: no-cache\r\n" +
                "\r\n"
        output.write(header.toByteArray())
        output.flush()

        while (isRunning && !socket.isClosed) {
            val frame = latestFrame
            if (frame != null) {
                try {
                    val boundary = "--frame\r\n"
                    val partHeader = "Content-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                    val partFooter = "\r\n"
                    output.write(boundary.toByteArray())
                    output.write(partHeader.toByteArray())
                    output.write(frame)
                    output.write(partFooter.toByteArray())
                    output.flush()
                } catch (e: Exception) {
                    break
                }
            }
            try { Thread.sleep(33) } catch (_: InterruptedException) { break }
        }
    }

    private fun handleStatus(output: OutputStream) {
        val latest = latestNotification ?: ""
        val json = "{" +
                "\"status\":\"online\"," +
                "\"battery\":$batteryLevel," +
                "\"wifi\":$wifiSignal," +
                "\"camera\":\"$cameraName\"," +
                "\"motions\":$motionCount," +
                "\"clients\":$clientCount," +
                "\"notification\":\"$latest\"" +
                "}"
        sendJsonResponse(output, json)
    }

    private fun handleNotifications(output: OutputStream) {
        val sb = StringBuilder()
        sb.append("[")
        synchronized(notifications) {
            for (i in notifications.indices) {
                val n = notifications[i]
                if (i > 0) sb.append(",")
                sb.append("{")
                sb.append("\"message\":\"${n["message"]}\",")
                sb.append("\"type\":\"${n["type"]}\",")
                sb.append("\"timestamp\":\"${n["timestamp"]}\"")
                sb.append("}")
            }
        }
        sb.append("]")
        sendJsonResponse(output, sb.toString())
    }

    private fun handleEvents(output: OutputStream) {
        handleNotifications(output)
    }

    private fun sendJsonResponse(output: OutputStream, json: String) {
        val bytes = json.toByteArray()
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "\r\n"
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun handleViewerPage(output: OutputStream) {
        val html = getViewerHtml()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-cache\r\n" +
                "\r\n"
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun getViewerHtml(): String {
        return """
<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="mobile-web-app-capable" content="yes">
    <title>SmartCam Pro - Live</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            background: #0a0a0a;
            color: #fff;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            overflow: hidden;
            height: 100vh;
            width: 100vw;
            -webkit-user-select: none;
            user-select: none;
        }
        .header {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            z-index: 10;
            background: linear-gradient(to bottom, rgba(0,0,0,0.9), transparent);
            padding: 12px 16px;
            padding-top: calc(12px + env(safe-area-inset-top, 0px));
        }
        .title { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
        .status-bar {
            display: flex;
            gap: 12px;
            font-size: 12px;
            color: #94a3b8;
            flex-wrap: wrap;
        }
        .status-item { display: flex; align-items: center; gap: 4px; }
        .live-dot {
            width: 8px; height: 8px;
            background: #ef4444;
            border-radius: 50%;
            animation: pulse 1.5s infinite;
        }
        .live-dot.live { background: #22c55e; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
        .camera-view {
            width: 100%; height: 100%;
            display: flex; align-items: center; justify-content: center;
            background: #000;
        }
        #stream { width: 100%; height: 100%; object-fit: contain; }
        .connecting {
            position: absolute;
            display: flex; flex-direction: column; align-items: center; gap: 12px;
            color: #64748b; font-size: 14px;
        }
        .spinner {
            width: 32px; height: 32px;
            border: 3px solid #1e293b;
            border-top: 3px solid #3b82f6;
            border-radius: 50%;
            animation: spin 1s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .controls {
            position: fixed;
            bottom: 0; left: 0; right: 0;
            z-index: 10;
            background: linear-gradient(to top, rgba(0,0,0,0.85), transparent);
            padding: 16px;
            padding-bottom: calc(16px + env(safe-area-inset-bottom, 0px));
            display: flex;
            justify-content: center;
            gap: 12px;
        }
        .btn {
            width: 48px; height: 48px;
            border-radius: 50%; border: none;
            font-size: 20px;
            display: flex; align-items: center; justify-content: center;
            cursor: pointer;
        }
        .btn-screenshot { background: #3b82f6; color: #fff; }
        .btn-fullscreen { background: #64748b; color: #fff; }
        .btn-alarm { background: #ef4444; color: #fff; }
        .screenshot-flash {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: #fff; opacity: 0;
            pointer-events: none; z-index: 100;
            transition: opacity 0.1s;
        }
        .screenshot-flash.active { opacity: 0.8; }

        /* Notification toast */
        .toast {
            position: fixed;
            top: 80px;
            left: 50%;
            transform: translateX(-50%);
            background: #ef4444;
            color: #fff;
            padding: 10px 20px;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 600;
            z-index: 20;
            opacity: 0;
            transition: opacity 0.3s;
            pointer-events: none;
            text-align: center;
            max-width: 90vw;
        }
        .toast.show { opacity: 1; }

        /* Notifications panel */
        .notifications-panel {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: #CC000000;
            z-index: 30;
            display: none;
            flex-direction: column;
            padding: 20px;
        }
        .notifications-panel.open { display: flex; }
        .notif-header {
            font-size: 18px; font-weight: 600;
            margin-bottom: 12px; text-align: center;
        }
        .notif-list {
            flex: 1;
            overflow-y: auto;
            padding-bottom: 60px;
        }
        .notif-item {
            background: #1e293b;
            border-radius: 8px;
            padding: 10px 14px;
            margin-bottom: 8px;
            border-left: 3px solid #ef4444;
        }
        .notif-item.motion { border-left-color: #f59e0b; }
        .notif-item .msg { font-size: 14px; color: #fff; }
        .notif-item .time { font-size: 11px; color: #64748b; margin-top: 4px; }
        .notif-close {
            position: fixed;
            bottom: 80px;
            left: 50%;
            transform: translateX(-50%);
            background: #475569;
            color: #fff;
            border: none;
            border-radius: 8px;
            padding: 10px 30px;
            font-size: 14px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <div class="screenshot-flash" id="flash"></div>
    <div class="toast" id="toast"></div>

    <div class="notifications-panel" id="notifPanel">
        <div class="notif-header">🔔 Benachrichtigungen</div>
        <div class="notif-list" id="notifList"></div>
        <button class="notif-close" onclick="closeNotifications()">Schließen</button>
    </div>

    <div class="header">
        <div class="title">📹 SmartCam Pro - Live</div>
        <div class="status-bar">
            <div class="status-item">
                <div class="live-dot" id="liveDot"></div>
                <span id="statusText">Verbinde...</span>
            </div>
            <div class="status-item">🔋 <span id="battery">--%</span></div>
            <div class="status-item">📶 <span id="wifi">--%</span></div>
            <div class="status-item">👁 <span id="clients">0</span></div>
        </div>
    </div>

    <div class="camera-view" id="cameraView">
        <img id="stream" src="/stream" alt="Live Stream" />
        <div class="connecting" id="connecting">
            <div class="spinner"></div>
            <div>Verbinde mit Kamera...</div>
        </div>
    </div>

    <div class="controls">
        <button class="btn btn-screenshot" onclick="takeScreenshot()" title="Screenshot">📷</button>
        <button class="btn btn-fullscreen" onclick="toggleFullscreen()" title="Vollbild">⛶</button>
        <button class="btn btn-alarm" onclick="showNotifications()" title="Benachrichtigungen">🔔</button>
    </div>

    <script>
        const stream = document.getElementById('stream');
        const connecting = document.getElementById('connecting');
        const flash = document.getElementById('flash');
        const liveDot = document.getElementById('liveDot');
        const toast = document.getElementById('toast');
        let lastNotification = '';

        stream.onload = function() {
            connecting.style.display = 'none';
            document.getElementById('statusText').textContent = 'Live';
            liveDot.classList.add('live');
        };

        stream.onerror = function() {
            connecting.style.display = 'block';
            document.getElementById('statusText').textContent = 'Verbinde...';
            liveDot.classList.remove('live');
            setTimeout(() => { stream.src = '/stream?' + Date.now(); }, 2000);
        };

        // Status polling
        setInterval(async () => {
            try {
                const resp = await fetch('/status');
                const data = await resp.json();
                document.getElementById('battery').textContent = data.battery + '%';
                document.getElementById('wifi').textContent = data.wifi + '%';
                document.getElementById('clients').textContent = data.clients;
                document.getElementById('statusText').textContent = 'Live';
                liveDot.classList.add('live');
                connecting.style.display = 'none';

                // Show notification toast
                if (data.notification && data.notification !== lastNotification) {
                    lastNotification = data.notification;
                    showToast('🚨 ' + data.notification);
                    // Request notification permission
                    if ('Notification' in window && Notification.permission === 'granted') {
                        new Notification('SmartCam Pro', { body: data.notification, icon: '📹' });
                    }
                }
            } catch (e) {
                document.getElementById('statusText').textContent = 'Verbinde...';
                liveDot.classList.remove('live');
            }
        }, 2000);

        function showToast(msg) {
            toast.textContent = msg;
            toast.classList.add('show');
            setTimeout(() => toast.classList.remove('show'), 4000);
        }

        // Request notification permission
        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }

        function takeScreenshot() {
            const canvas = document.createElement('canvas');
            canvas.width = stream.naturalWidth;
            canvas.height = stream.naturalHeight;
            canvas.getContext('2d').drawImage(stream, 0, 0);
            const link = document.createElement('a');
            link.download = 'screenshot_' + new Date().toISOString().slice(0,19) + '.jpg';
            link.href = canvas.toDataURL('image/jpeg', 0.9);
            link.click();
            flash.classList.add('active');
            setTimeout(() => flash.classList.remove('active'), 200);
        }

        function toggleFullscreen() {
            if (!document.fullscreenElement) {
                document.documentElement.requestFullscreen();
            } else {
                document.exitFullscreen();
            }
        }

        async function showNotifications() {
            document.getElementById('notifPanel').classList.add('open');
            const list = document.getElementById('notifList');
            list.innerHTML = '<div style="color:#64748b;text-align:center;padding:20px">Lade...</div>';
            try {
                const resp = await fetch('/notifications');
                const data = await resp.json();
                if (data.length === 0) {
                    list.innerHTML = '<div style="color:#64748b;text-align:center;padding:20px">Keine Benachrichtigungen</div>';
                } else {
                    list.innerHTML = data.map(n => {
                        const d = new Date(parseInt(n.timestamp));
                        const time = d.toLocaleTimeString('de-DE');
                        const cls = n.type === 'motion' ? ' motion' : '';
                        return '<div class="notif-item' + cls + '">' +
                               '<div class="msg">' + n.message + '</div>' +
                               '<div class="time">' + time + '</div></div>';
                    }).join('');
                }
            } catch (e) {
                list.innerHTML = '<div style="color:#ef4444;text-align:center;padding:20px">Fehler beim Laden</div>';
            }
        }

        function closeNotifications() {
            document.getElementById('notifPanel').classList.remove('open');
        }

        let wakeLock = null;
        async function requestWakeLock() {
            try { wakeLock = await navigator.wakeLock.request('screen'); } catch (e) {}
        }
        requestWakeLock();
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') requestWakeLock();
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
}
