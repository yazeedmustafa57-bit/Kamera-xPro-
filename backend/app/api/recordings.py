from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
import os
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.user import User
from app.models.camera import Camera
from app.models.recording import Recording
from app.utils.schemas import RecordingResponse

router = APIRouter()

@router.get("/", response_model=list[RecordingResponse])
async def list_recordings(camera_id: str = None, limit: int = Query(default=50, ge=1, le=200), db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    query = select(Recording, Camera.name.label("camera_name")).join(Camera, Recording.camera_id == Camera.id).where(Camera.user_id == current_user.id)
    if camera_id: query = query.where(Recording.camera_id == camera_id)
    query = query.order_by(Recording.created_at.desc()).limit(limit)
    result = await db.execute(query)
    return [RecordingResponse(id=r.Recording.id, camera_id=r.Recording.camera_id, filename=r.Recording.filename, filepath=r.Recording.filepath, duration=r.Recording.duration, file_size=r.Recording.file_size, created_at=r.Recording.created_at, camera_name=r.camera_name) for r in result.all()]

@router.delete("/{recording_id}")
async def delete_recording(recording_id: str, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(Recording).join(Camera, Recording.camera_id == Camera.id).where(Recording.id == recording_id, Camera.user_id == current_user.id))
    recording = result.scalar_one_or_none()
    if not recording: raise HTTPException(status_code=404, detail="Recording not found")
    if recording.filepath and os.path.exists(recording.filepath):
        try: os.remove(recording.filepath)
        except: pass
    await db.delete(recording)
    await db.commit()
    return {"message": "Recording deleted"}

@router.delete("/all")
async def delete_all_recordings(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(Recording).join(Camera, Recording.camera_id == Camera.id).where(Camera.user_id == current_user.id))
    for rec in result.scalars().all():
        await db.delete(rec)
    await db.commit()
    return {"message": "All recordings deleted"}
