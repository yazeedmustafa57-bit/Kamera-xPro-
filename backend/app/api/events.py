from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from datetime import datetime, timedelta, timezone
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.user import User
from app.models.camera import Camera
from app.models.event import Event
from app.utils.schemas import EventResponse

router = APIRouter()

@router.get("/", response_model=list[EventResponse])
async def list_events(camera_id: str = None, event_type: str = None, days: int = Query(default=7, ge=1, le=90), limit: int = Query(default=50, ge=1, le=200), db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    query = select(Event, Camera.name.label("camera_name")).join(Camera, Event.camera_id == Camera.id).where(Camera.user_id == current_user.id)
    if camera_id: query = query.where(Event.camera_id == camera_id)
    if event_type: query = query.where(Event.type == event_type)
    since = datetime.now(timezone.utc) - timedelta(days=days)
    query = query.where(Event.created_at >= since).order_by(Event.created_at.desc()).limit(limit)
    result = await db.execute(query)
    return [EventResponse(id=r.Event.id, camera_id=r.Event.camera_id, type=r.Event.type, confidence=r.Event.confidence, description=r.Event.description, image_path=r.Event.image_path, video_path=r.Event.video_path, is_read=r.Event.is_read, created_at=r.Event.created_at, camera_name=r.camera_name) for r in result.all()]

@router.put("/{event_id}/read")
async def mark_event_read(event_id: str, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(Event).join(Camera, Event.camera_id == Camera.id).where(Event.id == event_id, Camera.user_id == current_user.id))
    event = result.scalar_one_or_none()
    if not event: raise HTTPException(status_code=404, detail="Event not found")
    event.is_read = True
    await db.commit()
    return {"message": "Event marked as read"}

@router.delete("/{event_id}")
async def delete_event(event_id: str, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(Event).join(Camera, Event.camera_id == Camera.id).where(Event.id == event_id, Camera.user_id == current_user.id))
    event = result.scalar_one_or_none()
    if not event: raise HTTPException(status_code=404, detail="Event not found")
    await db.delete(event)
    await db.commit()
    return {"message": "Event deleted"}

@router.post("/create")
async def create_event(camera_id: str, event_type: str, confidence: float = 0.0, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    cam_result = await db.execute(select(Camera).where(Camera.id == camera_id, Camera.user_id == current_user.id))
    camera = cam_result.scalar_one_or_none()
    if not camera: raise HTTPException(status_code=404, detail="Camera not found")
    event = Event(camera_id=camera_id, type=event_type, confidence=str(confidence))
    db.add(event)
    await db.commit()
    return {"message": "Event created", "id": event.id}
