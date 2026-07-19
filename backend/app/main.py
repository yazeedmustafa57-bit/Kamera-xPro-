from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from contextlib import asynccontextmanager
import os
from app.core.config import settings
from app.core.database import engine, Base
from app.api import auth, cameras, events, recordings, websocket, dashboard

@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    os.makedirs("static/recordings", exist_ok=True)
    os.makedirs("static/screenshots", exist_ok=True)
    yield

app = FastAPI(
    title="SmartCam Pro API",
    description="Professionelles Sicherheitskamera-System mit Cloud-Unterstuetzung",
    version="2.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)

app.mount("/static", StaticFiles(directory="static"), name="static")

app.include_router(auth.router, prefix="/api/auth", tags=["Auth"])
app.include_router(cameras.router, prefix="/api/cameras", tags=["Kameras"])
app.include_router(events.router, prefix="/api/events", tags=["Ereignisse"])
app.include_router(recordings.router, prefix="/api/recordings", tags=["Aufnahmen"])
app.include_router(websocket.router, prefix="/ws", tags=["WebSocket"])
app.include_router(dashboard.router, prefix="/api/dashboard", tags=["Dashboard"])

@app.get("/api/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "SmartCam Pro",
        "version": "2.0.0",
        "features": [
            "Cloud-Streaming",
            "Benutzerverwaltung",
            "Subscription-System",
            "KI-Bewegungserkennung",
            "Push-Benachrichtigungen"
        ]
    }

@app.get("/")
async def root():
    return {
        "name": "SmartCam Pro API",
        "version": "2.0.0",
        "docs": "/docs",
        "health": "/api/health"
    }
