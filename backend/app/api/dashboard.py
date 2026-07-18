from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from datetime import datetime, timezone
import os
from app.core.database import get_db
from app.core.security import get_current_user
from app.core.config import settings
from app.models.user import User
from app.models.camera import Camera
from app.models.event import Event
from app.models.recording import Recording
from app.utils.schemas import DashboardStats

router = APIRouter()

@router.get("/stats", response_model=DashboardStats)
async def get_dashboard_stats(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    total = (await db.execute(select(func.count(Camera.id)).where(Camera.user_id == current_user.id))).scalar() or 0
    online = (await db.execute(select(func.count(Camera.id)).where(Camera.user_id == current_user.id, Camera.status.in_(["online", "streaming"])))).scalar() or 0
    total_events = (await db.execute(select(func.count(Event.id)).join(Camera, Event.camera_id == Camera.id).where(Camera.user_id == current_user.id))).scalar() or 0
    today = (await db.execute(select(func.count(Event.id)).join(Camera, Event.camera_id == Camera.id).where(Camera.user_id == current_user.id, Event.created_at >= datetime.now(timezone.utc).replace(hour=0, minute=0, second=0)))).scalar() or 0
    total_rec = (await db.execute(select(func.count(Recording.id)).join(Camera, Recording.camera_id == Camera.id).where(Camera.user_id == current_user.id))).scalar() or 0
    storage = "0 MB"
    try:
        size = sum(os.path.getsize(os.path.join(d, f)) for d in [settings.RECORDING_DIR, settings.SCREENSHOT_DIR] if os.path.exists(d) for f in os.listdir(d) if os.path.isfile(os.path.join(d, f)))
        storage = f"{size/(1024**2):.1f} MB" if size > 1024**2 else f"{size/1024:.1f} KB"
    except: pass
    return DashboardStats(total_cameras=total, online_cameras=online, total_events=total_events, events_today=today, total_recordings=total_rec, storage_used=storage)
