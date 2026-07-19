from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from datetime import datetime, timezone, timedelta
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.user import User
from app.models.camera import Camera
from app.models.event import Event
from app.models.recording import Recording
from app.utils.schemas import DashboardStats
import os

router = APIRouter()

@router.get("/stats", response_model=DashboardStats)
async def get_dashboard_stats(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    # Camera count
    cam_count = await db.execute(
        select(func.count(Camera.id)).where(Camera.user_id == current_user.id)
    )
    total_cameras = cam_count.scalar()
    
    online_count = await db.execute(
        select(func.count(Camera.id)).where(
            Camera.user_id == current_user.id,
            Camera.status == "online",
            Camera.is_active == True
        )
    )
    online_cameras = online_count.scalar()
    
    # Event counts
    event_count = await db.execute(
        select(func.count(Event.id)).where(Event.camera_id.in_(
            select(Camera.id).where(Camera.user_id == current_user.id)
        ))
    )
    total_events = event_count.scalar()
    
    today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    events_today_count = await db.execute(
        select(func.count(Event.id)).where(
            Event.camera_id.in_(
                select(Camera.id).where(Camera.user_id == current_user.id)
            ),
            Event.created_at >= today_start
        )
    )
    events_today = events_today_count.scalar()
    
    # Recording count
    rec_count = await db.execute(
        select(func.count(Recording.id)).where(Recording.camera_id.in_(
            select(Camera.id).where(Camera.user_id == current_user.id)
        ))
    )
    total_recordings = rec_count.scalar()
    
    # Storage used estimation
    storage = 0
    for d in ["static/recordings", "static/screenshots"]:
        if os.path.exists(d):
            for root, dirs, files in os.walk(d):
                storage += sum(os.path.getsize(os.path.join(root, f)) for f in files if os.path.isfile(os.path.join(root, f)))
    
    storage_str = format_size(storage)
    
    return DashboardStats(
        total_cameras=total_cameras,
        online_cameras=online_cameras,
        total_events=total_events,
        events_today=events_today,
        total_recordings=total_recordings,
        storage_used=storage_str,
        subscription=current_user.subscription or "free",
        max_cameras=current_user.max_cameras or 1,
        max_storage_mb=current_user.max_storage_mb or 500
    )

def format_size(bytes_val: int) -> str:
    for unit in ['B', 'KB', 'MB', 'GB']:
        if bytes_val < 1024:
            return f"{bytes_val:.1f} {unit}"
        bytes_val /= 1024
    return f"{bytes_val:.1f} TB"
