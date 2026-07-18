import json
import cv2
import numpy as np
from typing import List, Dict, Optional
from pathlib import Path
from dataclasses import dataclass


@dataclass
class Zone:
    id: str
    name: str
    camera_id: str
    points: List[List[int]]  # [[x1,y1], [x2,y2], ...]
    sensitivity: float = 0.5
    target_classes: List[str] = None  # None = all classes
    active: bool = True
    cooldown_seconds: int = 30  # Min time between alerts
    last_triggered: float = 0

    def __post_init__(self):
        if self.target_classes is None:
            self.target_classes = ["person", "vehicle", "animal"]


class DetectionZoneManager:
    def __init__(self, config_dir: str = "config/zones"):
        self.config_dir = Path(config_dir)
        self.config_dir.mkdir(parents=True, exist_ok=True)
        self.zones: Dict[str, Zone] = {}
        self._load_all()

    def _load_all(self):
        for f in self.config_dir.glob("*.json"):
            try:
                with open(f) as fp:
                    data = json.load(fp)
                    for z in data.get("zones", []):
                        zone = Zone(**z)
                        self.zones[zone.id] = zone
            except Exception:
                pass

    def _save_camera(self, camera_id: str):
        camera_zones = [z for z in self.zones.values() if z.camera_id == camera_id]
        filepath = self.config_dir / f"{camera_id}.json"
        with open(filepath, "w") as f:
            json.dump({"camera_id": camera_id, "zones": [
                {
                    "id": z.id, "name": z.name, "camera_id": z.camera_id,
                    "points": z.points, "sensitivity": z.sensitivity,
                    "target_classes": z.target_classes, "active": z.active,
                    "cooldown_seconds": z.cooldown_seconds,
                }
                for z in camera_zones
            ]}, f, indent=2)

    def create_zone(self, camera_id: str, name: str, points: List[List[int]],
                    sensitivity: float = 0.5, target_classes: List[str] = None,
                    cooldown_seconds: int = 30) -> Zone:
        import uuid
        zone_id = str(uuid.uuid4())[:8]
        zone = Zone(
            id=zone_id, name=name, camera_id=camera_id,
            points=points, sensitivity=sensitivity,
            target_classes=target_classes or ["person", "vehicle", "animal"],
            cooldown_seconds=cooldown_seconds,
        )
        self.zones[zone_id] = zone
        self._save_camera(camera_id)
        return zone

    def update_zone(self, zone_id: str, **kwargs) -> Optional[Zone]:
        zone = self.zones.get(zone_id)
        if not zone:
            return None
        for key, value in kwargs.items():
            if hasattr(zone, key):
                setattr(zone, key, value)
        self._save_camera(zone.camera_id)
        return zone

    def delete_zone(self, zone_id: str) -> bool:
        zone = self.zones.pop(zone_id, None)
        if zone:
            self._save_camera(zone.camera_id)
            return True
        return False

    def get_camera_zones(self, camera_id: str) -> List[Zone]:
        return [z for z in self.zones.values() if z.camera_id == camera_id]

    def point_in_zones(self, x: int, y: int, camera_id: str) -> List[Zone]:
        matches = []
        for zone in self.get_camera_zones(camera_id):
            if not zone.active:
                continue
            pts = np.array(zone.points, np.int32)
            result = cv2.pointPolygonTest(pts, (float(x), float(y)), False)
            if result >= 0:
                matches.append(zone)
        return matches

    def should_trigger(self, zone: Zone, class_group: str) -> bool:
        import time
        if class_group not in zone.target_classes:
            return False
        now = time.time()
        if now - zone.last_triggered < zone.cooldown_seconds:
            return False
        zone.last_triggered = now
        return True


zone_manager = DetectionZoneManager()
