from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query
from typing import Dict, Set
import json

router = APIRouter()


class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, Set[WebSocket]] = {}
        self.camera_connections: Dict[str, WebSocket] = {}

    async def connect(self, websocket: WebSocket, client_id: str):
        await websocket.accept()
        if client_id not in self.active_connections:
            self.active_connections[client_id] = set()
        self.active_connections[client_id].add(websocket)

    def disconnect(self, websocket: WebSocket, client_id: str):
        if client_id in self.active_connections:
            self.active_connections[client_id].discard(websocket)
            if not self.active_connections[client_id]:
                del self.active_connections[client_id]

    async def connect_camera(self, websocket: WebSocket, camera_id: str):
        await websocket.accept()
        self.camera_connections[camera_id] = websocket

    def disconnect_camera(self, camera_id: str):
        self.camera_connections.pop(camera_id, None)

    async def broadcast_to_user(self, user_id: str, message: dict):
        if user_id in self.active_connections:
            dead = []
            for connection in self.active_connections[user_id]:
                try:
                    await connection.send_json(message)
                except Exception:
                    dead.append(connection)
            for d in dead:
                self.active_connections[user_id].discard(d)

    async def send_to_camera(self, camera_id: str, message: dict):
        ws = self.camera_connections.get(camera_id)
        if ws:
            try:
                await ws.send_json(message)
            except Exception:
                self.camera_connections.pop(camera_id, None)


manager = ConnectionManager()


def verify_ws_token(token: str) -> str:
    """Verify JWT token and return user_id. Returns empty string if invalid."""
    if not token:
        return ""
    try:
        from jose import jwt
        from app.core.config import settings
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.ALGORITHM])
        user_id = payload.get("sub", "")
        return user_id if user_id else ""
    except Exception:
        return ""


@router.websocket("/client/{client_id}")
async def client_websocket(websocket: WebSocket, client_id: str, token: str = Query(default="")):
    # Verify token matches client_id
    verified_user_id = verify_ws_token(token)
    if not verified_user_id or verified_user_id != client_id:
        await websocket.close(code=4001, reason="Unauthorized")
        return

    await manager.connect(websocket, client_id)
    try:
        while True:
            data = await websocket.receive_text()
            message = json.loads(data)
            if message.get("type") == "ping":
                await websocket.send_json({"type": "pong"})
    except WebSocketDisconnect:
        manager.disconnect(websocket, client_id)
    except Exception:
        manager.disconnect(websocket, client_id)


@router.websocket("/camera/{camera_id}")
async def camera_websocket(websocket: WebSocket, camera_id: str, token: str = Query(default="")):
    # Verify camera token
    if not verify_ws_token(token):
        await websocket.close(code=4001, reason="Unauthorized")
        return

    await manager.connect_camera(websocket, camera_id)
    try:
        while True:
            data = await websocket.receive_text()
            message = json.loads(data)

            if message.get("type") == "status":
                for user_conns in manager.active_connections.values():
                    for conn in user_conns:
                        try:
                            await conn.send_json({
                                "type": "camera_status",
                                "camera_id": camera_id,
                                "status": message.get("status"),
                                "battery": message.get("battery"),
                                "wifi_signal": message.get("wifi_signal"),
                            })
                        except Exception:
                            pass

            elif message.get("type") == "motion":
                for user_conns in manager.active_connections.values():
                    for conn in user_conns:
                        try:
                            await conn.send_json({
                                "type": "motion_detected",
                                "camera_id": camera_id,
                                "confidence": message.get("confidence"),
                                "timestamp": message.get("timestamp"),
                            })
                        except Exception:
                            pass

    except WebSocketDisconnect:
        manager.disconnect_camera(camera_id)
        for user_conns in manager.active_connections.values():
            for conn in user_conns:
                try:
                    await conn.send_json({
                        "type": "camera_offline",
                        "camera_id": camera_id,
                    })
                except Exception:
                    pass
    except Exception:
        manager.disconnect_camera(camera_id)
