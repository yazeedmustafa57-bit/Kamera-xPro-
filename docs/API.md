# SmartCam Pro API Documentation

## Base URL
```
http://localhost:8000/api
```

## Authentication

All protected endpoints require a JWT token in the Authorization header:
```
Authorization: Bearer <token>
```

### Register
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "admin",
  "email": "admin@example.com",
  "password": "securepassword",
  "role": "admin"
}
```

### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "securepassword"
}
```

Response:
```json
{
  "access_token": "eyJ...",
  "token_type": "bearer",
  "user": {
    "id": "...",
    "username": "admin",
    "email": "admin@example.com",
    "role": "admin"
  }
}
```

## Cameras

### List Cameras
```http
GET /api/cameras/
Authorization: Bearer <token>
```

### Create Camera
```http
POST /api/cameras/
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Front Door",
  "location": "Entrance"
}
```

### Update Camera
```http
PUT /api/cameras/{camera_id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Updated Name",
  "status": "online",
  "battery": 85,
  "wifi_signal": 90
}
```

### Toggle Camera
```http
POST /api/cameras/{camera_id}/toggle
Authorization: Bearer <token>
```

### Delete Camera
```http
DELETE /api/cameras/{camera_id}
Authorization: Bearer <token>
```

## Events

### List Events
```http
GET /api/events/?camera_id=...&event_type=person&days=7&limit=50
Authorization: Bearer <token>
```

### Mark Event Read
```http
PUT /api/events/{event_id}/read
Authorization: Bearer <token>
```

### Delete Event
```http
DELETE /api/events/{event_id}
Authorization: Bearer <token>
```

## Recordings

### List Recordings
```http
GET /api/recordings/?camera_id=...&limit=50
Authorization: Bearer <token>
```

### Delete Recording
```http
DELETE /api/recordings/{recording_id}
Authorization: Bearer <token>
```

### Delete All Recordings
```http
DELETE /api/recordings/
Authorization: Bearer <token>
```

## Dashboard

### Get Stats
```http
GET /api/dashboard/stats
Authorization: Bearer <token>
```

Response:
```json
{
  "total_cameras": 5,
  "online_cameras": 3,
  "total_events": 142,
  "events_today": 12,
  "total_recordings": 89,
  "storage_used": "2.4 GB"
}
```

## WebSocket

### Client Connection
```
ws://localhost:8000/ws/client/{client_id}
```

Events received:
- `camera_status` - Camera status update
- `motion_detected` - Motion event
- `camera_offline` - Camera went offline

### Camera Connection
```
ws://localhost:8000/ws/camera/{camera_id}
```

Events sent:
```json
{
  "type": "status",
  "status": "streaming",
  "battery": 85,
  "wifi_signal": 90
}
```

```json
{
  "type": "motion",
  "confidence": 0.85,
  "timestamp": "2024-01-01T12:00:00Z"
}
```
