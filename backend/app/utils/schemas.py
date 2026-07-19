from pydantic import BaseModel, EmailStr, field_validator
from typing import Optional
from datetime import datetime

class UserCreate(BaseModel):
    username: str
    email: EmailStr
    password: str
    role: str = "user"
    
    @field_validator("username")
    @classmethod
    def validate_username(cls, v):
        if len(v) < 3: raise ValueError("Username must be at least 3 characters")
        return v
    
    @field_validator("password")
    @classmethod
    def validate_password(cls, v):
        if len(v) < 6: raise ValueError("Password must be at least 6 characters")
        return v

class UserLogin(BaseModel):
    username: str
    password: str

class UserResponse(BaseModel):
    id: str
    username: str
    email: str
    role: str
    is_active: bool
    subscription: str
    subscription_expires: Optional[datetime] = None
    max_cameras: int
    max_storage_mb: int
    created_at: datetime
    
    class Config:
        from_attributes = True

class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse

class CameraCreate(BaseModel):
    name: str
    location: str = ""

class CameraUpdate(BaseModel):
    name: Optional[str] = None
    location: Optional[str] = None
    is_active: Optional[bool] = None
    status: Optional[str] = None
    battery: Optional[int] = None
    wifi_signal: Optional[int] = None

class CameraResponse(BaseModel):
    id: str
    user_id: str
    name: str
    location: str
    status: str
    battery: int
    wifi_signal: int
    is_active: bool
    stream_url: str
    created_at: datetime
    updated_at: Optional[datetime] = None
    
    class Config:
        from_attributes = True

class EventResponse(BaseModel):
    id: str
    camera_id: str
    type: str
    confidence: str
    description: str
    image_path: str
    video_path: str
    is_read: bool
    created_at: datetime
    camera_name: Optional[str] = None
    
    class Config:
        from_attributes = True

class RecordingResponse(BaseModel):
    id: str
    camera_id: str
    filename: str
    filepath: str
    duration: int
    file_size: int
    created_at: datetime
    camera_name: Optional[str] = None
    
    class Config:
        from_attributes = True

class DashboardStats(BaseModel):
    total_cameras: int
    online_cameras: int
    total_events: int
    events_today: int
    total_recordings: int
    storage_used: str
    subscription: str
    max_cameras: int
    max_storage_mb: int

# Subscription
class SubscriptionPlan(BaseModel):
    name: str
    price_monthly: float
    price_yearly: float
    max_cameras: int
    max_storage_mb: int
    max_events: int
    features: list[str]

class SubscriptionUpgrade(BaseModel):
    plan: str  # "free", "pro", "business"
    payment_method: Optional[str] = None
