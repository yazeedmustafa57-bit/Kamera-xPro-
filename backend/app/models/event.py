import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, ForeignKey, Text, Boolean
from sqlalchemy.orm import relationship
from app.core.database import Base

class Event(Base):
    __tablename__ = "events"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    camera_id = Column(String(36), ForeignKey("cameras.id", ondelete="CASCADE"), nullable=False)
    type = Column(String(50), nullable=False)
    confidence = Column(String(10), default="0")
    description = Column(Text, default="")
    image_path = Column(String(500), default="")
    video_path = Column(String(500), default="")
    is_read = Column(Boolean, default=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    camera = relationship("Camera", back_populates="events")
