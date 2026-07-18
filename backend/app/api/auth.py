from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.core.database import get_db
from app.core.security import verify_password, get_password_hash, create_access_token, get_current_user
from app.models.user import User
from app.utils.schemas import UserCreate, UserLogin, UserResponse, Token

router = APIRouter()

@router.post("/register", response_model=Token, status_code=201)
async def register(user_data: UserCreate, db: AsyncSession = Depends(get_db)):
    if user_data.role == "admin": user_data.role = "user"
    existing = await db.execute(select(User).where((User.username == user_data.username) | (User.email == user_data.email)))
    if existing.scalar_one_or_none(): raise HTTPException(status_code=400, detail="Username or email already exists")
    user = User(username=user_data.username, email=user_data.email, password_hash=get_password_hash(user_data.password), role=user_data.role)
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
        raise HTTPException(status_code=401, detail="Invalid credentials")
    if not user.is_active: raise HTTPException(status_code=403, detail="Account disabled")
    token = create_access_token(data={"sub": user.id})
    return Token(access_token=token, user=UserResponse.model_validate(user))

@router.get("/me", response_model=UserResponse)
async def get_me(current_user: User = Depends(get_current_user)):
    return UserResponse.model_validate(current_user)

@router.get("/users", response_model=list[UserResponse])
async def list_users(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    if current_user.role != "admin": raise HTTPException(status_code=403, detail="Admin access required")
    result = await db.execute(select(User).order_by(User.created_at.desc()))
    return [UserResponse.model_validate(u) for u in result.scalars().all()]
