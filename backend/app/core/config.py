from pydantic_settings import BaseSettings
from typing import List

class Settings(BaseSettings):
    PROJECT_NAME: str = "SmartCam Pro"
    VERSION: str = "1.1.0"
    
    DATABASE_URL: str = "sqlite+aiosqlite:///./smartcampro.db"
    REDIS_URL: str = "redis://localhost:6379/0"
    
    SECRET_KEY: str = "super-secret-key-change-me-2024-production"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440
    
    CORS_ORIGINS: List[str] = ["http://localhost:3000", "http://127.0.0.1:3000", "*"]
    
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USER: str = ""
    SMTP_PASSWORD: str = ""
    ALARM_EMAIL: str = ""
    
    RECORDING_DIR: str = "static/recordings"
    SCREENSHOT_DIR: str = "static/screenshots"
    MAX_RECORDING_DAYS: int = 30
    
    MOTION_SENSITIVITY: float = 0.5
    DETECTION_CLASSES: list = ["person", "car", "animal"]

    class Config:
        env_file = ".env"
        case_sensitive = True

settings = Settings()
