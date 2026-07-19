from pydantic_settings import BaseSettings
from typing import List

class Settings(BaseSettings):
    PROJECT_NAME: str = "SmartCam Pro"
    VERSION: str = "2.0.0"
    
    # Database - supports SQLite for local, PostgreSQL for cloud
    DATABASE_URL: str = "sqlite+aiosqlite:///./smartcampro.db"
    
    # Security
    SECRET_KEY: str = "smartcampro-change-this-in-production-2024"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 43200  # 30 days
    
    # CORS
    CORS_ORIGINS: List[str] = ["*"]
    
    # Email (optional)
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USER: str = ""
    SMTP_PASSWORD: str = ""
    ALARM_EMAIL: str = ""
    
    # Storage
    RECORDING_DIR: str = "static/recordings"
    SCREENSHOT_DIR: str = "static/screenshots"
    MAX_RECORDING_DAYS: int = 30
    
    # AI
    MOTION_SENSITIVITY: float = 0.5
    DETECTION_CLASSES: list = ["person", "car", "animal"]
    
    # Subscription Limits
    FREE_MAX_CAMERAS: int = 1
    FREE_MAX_STORAGE_MB: int = 500
    FREE_MAX_EVENTS: int = 100
    PRO_MAX_CAMERAS: int = 10
    PRO_MAX_STORAGE_MB: int = 10000
    PRO_MAX_EVENTS: int = 10000
    BUSINESS_MAX_CAMERAS: int = 100
    BUSINESS_MAX_STORAGE_MB: int = 100000
    BUSINESS_MAX_EVENTS: int = 100000

    class Config:
        env_file = ".env"
        case_sensitive = True

settings = Settings()
