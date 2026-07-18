import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from typing import Optional
import asyncio
from datetime import datetime, timezone

from app.core.config import settings


class NotificationService:
    def __init__(self):
        self.email_enabled = bool(settings.SMTP_USER and settings.SMTP_PASSWORD)

    async def send_email(self, to: str, subject: str, body: str, html: Optional[str] = None):
        if not self.email_enabled:
            print(f"[Notification] Email disabled. Would send to {to}: {subject}")
            return False

        try:
            msg = MIMEMultipart("alternative")
            msg["From"] = settings.SMTP_USER
            msg["To"] = to
            msg["Subject"] = subject
            msg.attach(MIMEText(body, "plain"))
            if html:
                msg.attach(MIMEText(html, "html"))

            loop = asyncio.get_event_loop()
            await loop.run_in_executor(None, self._send_smtp, msg)
            return True
        except Exception as e:
            print(f"[Notification] Email error: {e}")
            return False

    def _send_smtp(self, msg):
        with smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT) as server:
            server.starttls()
            server.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
            server.send_message(msg)

    async def send_motion_alert(self, camera_name: str, event_type: str, confidence: float):
        now = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')
        subject = f"SmartCam Pro - {event_type.upper()} detected on {camera_name}"
        body = f"""
Motion Alert - SmartCam Pro

Camera: {camera_name}
Event Type: {event_type}
Confidence: {confidence * 100:.0f}%
Time: {now}

Login to your dashboard for details.
        """

        html = f"""
<html>
<body style="font-family: Arial, sans-serif; background: #1a1a2e; color: #eee; padding: 20px;">
<div style="max-width: 500px; margin: 0 auto; background: #16213e; border-radius: 12px; padding: 30px;">
<h2 style="color: #ff6b6b;">Motion Detected</h2>
<table style="width: 100%; color: #eee;">
<tr><td><strong>Camera:</strong></td><td>{camera_name}</td></tr>
<tr><td><strong>Event:</strong></td><td>{event_type}</td></tr>
<tr><td><strong>Confidence:</strong></td><td>{confidence * 100:.0f}%</td></tr>
<tr><td><strong>Time:</strong></td><td>{now}</td></tr>
</table>
</div>
</body>
</html>
        """

        if settings.ALARM_EMAIL:
            await self.send_email(settings.ALARM_EMAIL, subject, body, html)


notification_service = NotificationService()
