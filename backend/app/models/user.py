import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, Boolean, Integer
from sqlalchemy.orm import relationship
import enum
from app.core.database import Base

class UserRole(str, enum.Enum):
    ADMIN = "admin"
    USER = "user"

class SubscriptionTier(str, enum.Enum):
    FREE = "free"
    PRO = "pro"
    BUSINESS = "business"

class User(Base):
    __tablename__ = "users"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    username = Column(String(50), unique=True, nullable=False, index=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    role = Column(String(10), default="user")
    is_active = Column(Boolean, default=True)
    
    # Subscription
    subscription = Column(String(20), default="free")  # free, pro, business
    subscription_expires = Column(DateTime, nullable=True)
    stripe_customer_id = Column(String(255), nullable=True)
    stripe_subscription_id = Column(String(255), nullable=True)
    
    # Limits
    max_cameras = Column(Integer, default=1)
    max_storage_mb = Column(Integer, default=500)
    
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    
    cameras = relationship("Camera", back_populates="owner", cascade="all, delete-orphan")
