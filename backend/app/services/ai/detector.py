import cv2
import numpy as np
from typing import List, Dict, Optional
from datetime import datetime, timezone
from dataclasses import dataclass, field
import asyncio

from app.services.ai.yolo_setup import yolo_manager, SECURITY_CLASSES


@dataclass
class Detection:
    label: str
    confidence: float
    bbox: Dict[str, int]
    class_group: str  # "person", "vehicle", "animal", etc.
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


class SmartDetector:
    def __init__(self):
        self.min_confidence = 0.4
        self.nms_threshold = 0.4
        self.target_classes: Optional[List[str]] = None  # None = all classes
        self.active = True

    def set_target_classes(self, classes: List[str]):
        self.target_classes = classes

    def set_min_confidence(self, confidence: float):
        self.min_confidence = max(0.1, min(1.0, confidence))

    def get_class_group(self, class_id: int) -> str:
        for group_name, class_ids in SECURITY_CLASSES.items():
            if class_id in class_ids:
                return group_name
        return "other"

    def is_target_class(self, class_id: int) -> bool:
        if self.target_classes is None:
            return True
        group = self.get_class_group(class_id)
        return group in self.target_classes

    def detect(self, frame: np.ndarray) -> List[Detection]:
        if not self.active or not yolo_manager.model_loaded:
            return []

        detections = []
        height, width = frame.shape[:2]

        blob = cv2.dnn.blobFromImage(frame, 0.00392, (416, 416), (0, 0, 0), True, crop=False)
        yolo_manager.net.setInput(blob)
        outs = yolo_manager.net.forward(yolo_manager.output_layers)

        class_ids = []
        confidences = []
        boxes = []

        for out in outs:
            for detection in out:
                scores = detection[5:]
                class_id = int(np.argmax(scores))
                confidence = float(scores[class_id])

                if confidence > self.min_confidence and self.is_target_class(class_id):
                    center_x = int(detection[0] * width)
                    center_y = int(detection[1] * height)
                    w = int(detection[2] * width)
                    h = int(detection[3] * height)
                    x = int(center_x - w / 2)
                    y = int(center_y - h / 2)

                    class_ids.append(class_id)
                    confidences.append(confidence)
                    boxes.append([x, y, w, h])

        if boxes:
            indices = cv2.dnn.NMSBoxes(boxes, confidences, self.min_confidence, self.nms_threshold)
            if len(indices) > 0:
                for i in indices.flatten():
                    class_id = class_ids[i]
                    label = yolo_manager.classes[class_id] if class_id < len(yolo_manager.classes) else "unknown"
                    detections.append(Detection(
                        label=label,
                        confidence=round(confidences[i], 3),
                        bbox={
                            "x": max(0, boxes[i][0]),
                            "y": max(0, boxes[i][1]),
                            "w": boxes[i][2],
                            "h": boxes[i][3],
                        },
                        class_group=self.get_class_group(class_id),
                    ))

        return detections

    def detect_and_draw(self, frame: np.ndarray) -> tuple:
        detections = self.detect(frame)
        annotated = frame.copy()

        colors = {
            "person": (0, 255, 0),
            "vehicle": (255, 165, 0),
            "animal": (0, 255, 255),
            "bicycle": (255, 255, 0),
            "other": (128, 128, 128),
        }

        for det in detections:
            color = colors.get(det.class_group, (255, 255, 255))
            x, y, w, h = det.bbox["x"], det.bbox["y"], det.bbox["w"], det.bbox["h"]
            cv2.rectangle(annotated, (x, y), (x + w, y + h), color, 2)

            label = f"{det.label} {det.confidence:.0%}"
            (label_w, label_h), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 1)
            cv2.rectangle(annotated, (x, y - label_h - 10), (x + label_w, y), color, -1)
            cv2.putText(annotated, label, (x, y - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1)

        return annotated, detections

    def to_dict(self, detections: List[Detection]) -> List[dict]:
        return [
            {
                "label": d.label,
                "confidence": d.confidence,
                "bbox": d.bbox,
                "class_group": d.class_group,
                "timestamp": d.timestamp,
            }
            for d in detections
        ]


smart_detector = SmartDetector()
