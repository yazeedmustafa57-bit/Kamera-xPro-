from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.core.database import get_db
from app.core.security import verify_password, get_password_hash, create_access_token, get_current_user
from app.models.user import User
from app.utils.schemas import UserCreate, UserLogin, UserResponse, Token, SubscriptionUpgrade

router = APIRouter()

@router.post("/register", response_model=Token, status_code=201)
async def register(user_data: UserCreate, db: AsyncSession = Depends(get_db)):
    # Check existing
    existing = await db.execute(
        select(User).where((User.username == user_data.username) | (User.email == user_data.email))
    )
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="Benutzername oder E-Mail bereits vergeben")
    
    # Create user with free tier
    user = User(
        username=user_data.username,
        email=user_data.email,
        password_hash=get_password_hash(user_data.password),
        role=user_data.role,
        subscription="free",
        max_cameras=1,
        max_storage_mb=500
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    
    token = create_access_token(data={"sub": user.id})
    return Token(access_token=token, user=UserResponse.model_validate(user))

@router.post("/login", response_model=Token)
async def login(credentials: UserLogin, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(User).where(User.username == credentials.username))
    user = result.scalar_one_or_none()
    
    if not user or not verify_password(credentials.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Ungültige Anmeldedaten")
    
    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account deaktiviert")
    
    token = create_access_token(data={"sub": user.id})
    return Token(access_token=token, user=UserResponse.model_validate(user))

@router.get("/me", response_model=UserResponse)
async def get_me(current_user: User = Depends(get_current_user)):
    return UserResponse.model_validate(current_user)

@router.get("/users", response_model=list[UserResponse])
async def list_users(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    if current_user.role != "admin":
        raise HTTPException(status_code=403, detail="Admin-Zugang erforderlich")
    result = await db.execute(select(User).order_by(User.created_at.desc()))
    return [UserResponse.model_validate(u) for u in result.scalars().all()]

# Subscription endpoints
@router.get("/subscription/plans")
async def get_plans():
    return [
        {
            "name": "free",
            "price_monthly": 0,
            "price_yearly": 0,
            "max_cameras": 1,
            "max_storage_mb": 500,
            "max_events": 100,
            "features": ["1 Kamera", "500 MB Speicher", "100 Ereignisse", "Basis-Benachrichtigungen"]
        },
        {
            "name": "pro",
            "price_monthly": 9.99,
            "price_yearly": 99.99,
            "max_cameras": 10,
            "max_storage_mb": 10000,
            "max_events": 10000,
            "features": ["10 Kameras", "10 GB Speicher", "10.000 Ereignisse", "KI-Erkennung", "Prioritäts-Support"]
        },
        {
            "name": "business",
            "price_monthly": 29.99,
            "price_yearly": 299.99,
            "max_cameras": 100,
            "max_storage_mb": 100000,
            "max_events": 100000,
            "features": ["100 Kameras", "100 GB Speicher", "100.000 Ereignisse", "KI-Erkennung", "API-Zugang", "24/7 Support"]
        }
    ]

@router.post("/subscription/upgrade")
async def upgrade_subscription(
    upgrade: SubscriptionUpgrade,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    limits = {
        "free": (1, 500),
        "pro": (10, 10000),
        "business": (100, 100000)
    }
    
    plan = upgrade.plan
    if plan not in limits:
        raise HTTPException(status_code=400, detail="Ungültiger Plan")
    
    max_cameras, max_storage = limits[plan]
    
    current_user.subscription = plan
    current_user.max_cameras = max_cameras
    current_user.max_storage_mb = max_storage
    
    await db.commit()
    await db.refresh(current_user)
    
    return {"message": f"Upgrade auf {plan} erfolgreich", "user": UserResponse.model_validate(current_user)}

@router.delete("/delete-account")
async def delete_account(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    await db.delete(current_user)
    await db.commit()
    return {"message": "Account gelöscht"}
