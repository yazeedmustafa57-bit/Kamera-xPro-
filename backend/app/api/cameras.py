from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.user import User
from app.models.camera import Camera
from app.utils.schemas import CameraCreate, CameraUpdate, CameraResponse

router = APIRouter()

@router.get("/", response_model=list[CameraResponse])
async def list_cameras(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(
        select(Camera).where(Camera.user_id == current_user.id).order_by(Camera.created_at.desc())
    )
    return [CameraResponse.model_validate(c) for c in result.scalars().all()]

@router.post("/", response_model=CameraResponse, status_code=201)
async def create_camera(
    camera_data: CameraCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    # Check camera limit
    count_result = await db.execute(
        select(func.count(Camera.id)).where(Camera.user_id == current_user.id)
    )
    camera_count = count_result.scalar()
    
    if camera_count >= current_user.max_cameras:
        raise HTTPException(
            status_code=403,
            detail=f"Kamera-Limit erreicht ({current_user.max_cameras}). Upgrade auf Pro für mehr Kameras."
        )
    
    camera = Camera(
        user_id=current_user.id,
        name=camera_data.name,
        location=camera_data.location
    )
    db.add(camera)
    await db.commit()
    await db.refresh(camera)
    return CameraResponse.model_validate(camera)

@router.get("/{camera_id}", response_model=CameraResponse)
async def get_camera(
    camera_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    result = await db.execute(
        select(Camera).where(Camera.id == camera_id, Camera.user_id == current_user.id)
    )
    camera = result.scalar_one_or_none()
    if not camera:
        raise HTTPException(status_code=404, detail="Kamera nicht gefunden")
    return CameraResponse.model_validate(camera)

@router.put("/{camera_id}", response_model=CameraResponse)
async def update_camera(
    camera_id: str,
    update_data: CameraUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    result = await db.execute(
        select(Camera).where(Camera.id == camera_id, Camera.user_id == current_user.id)
    )
    camera = result.scalar_one_or_none()
    if not camera:
        raise HTTPException(status_code=404, detail="Kamera nicht gefunden")
    
    for field, value in update_data.model_dump(exclude_unset=True).items():
        setattr(camera, field, value)
    
    await db.commit()
    await db.refresh(camera)
    return CameraResponse.model_validate(camera)

@router.delete("/{camera_id}")
async def delete_camera(
    camera_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    result = await db.execute(
        select(Camera).where(Camera.id == camera_id, Camera.user_id == current_user.id)
    )
    camera = result.scalar_one_or_none()
    if not camera:
        raise HTTPException(status_code=404, detail="Kamera nicht gefunden")
    await db.delete(camera)
    await db.commit()
    return {"message": "Kamera gelöscht"}

@router.post("/{camera_id}/toggle")
async def toggle_camera(
    camera_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    result = await db.execute(
        select(Camera).where(Camera.id == camera_id, Camera.user_id == current_user.id)
    )
    camera = result.scalar_one_or_none()
    if not camera:
        raise HTTPException(status_code=404, detail="Kamera nicht gefunden")
    camera.is_active = not camera.is_active
    camera.status = "online" if camera.is_active else "offline"
    await db.commit()
    await db.refresh(camera)
    return CameraResponse.model_validate(camera)
