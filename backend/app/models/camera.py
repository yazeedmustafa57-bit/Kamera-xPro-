import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, ForeignKey, Integer, Boolean
from sqlalchemy.orm import relationship
from app.core.database import Base

class Camera(Base):
    __tablename__ = "cameras"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    name = Column(String(100), nullable=False)
    location = Column(String(255), default="")
    status = Column(String(20), default="offline")
    battery = Column(Integer, default=100)
    wifi_signal = Column(Integer, default=100)
    is_active = Column(Boolean, default=True)
    stream_url = Column(String(500), default="")
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    owner = relationship("User", back_populates="cameras")
    events = relationship("Event", back_populates="camera", cascade="all, delete-orphan")
    recordings = relationship("Recording", back_populates="camera", cascade="all, delete-orphan")
