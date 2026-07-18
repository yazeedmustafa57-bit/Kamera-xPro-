import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, ForeignKey, Integer
from sqlalchemy.orm import relationship
from app.core.database import Base

class Recording(Base):
    __tablename__ = "recordings"
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    camera_id = Column(String(36), ForeignKey("cameras.id", ondelete="CASCADE"), nullable=False)
    filename = Column(String(255), nullable=False)
    filepath = Column(String(500), nullable=False)
    duration = Column(Integer, default=0)
    file_size = Column(Integer, default=0)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    camera = relationship("Camera", back_populates="recordings")
