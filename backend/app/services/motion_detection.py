import cv2
import numpy as np
from typing import List, Dict
from datetime import datetime, timezone


class MotionDetector:
    def __init__(self, sensitivity: float = 0.5):
        self.sensitivity = sensitivity
        self.background_subtractor = cv2.createBackgroundSubtractorMOG2(
            history=500, varThreshold=50, detectShadows=True
        )
        self.min_contour_area = 500
        self.motion_zones: List[Dict] = []
        self.detection_classes = ["person", "car", "animal", "motion"]

    def set_sensitivity(self, sensitivity: float):
        self.sensitivity = max(0.1, min(1.0, sensitivity))
        self.min_contour_area = int(1000 * (1 - self.sensitivity))

    def set_motion_zones(self, zones: List[Dict]):
        self.motion_zones = zones

    def process_frame(self, frame: np.ndarray) -> Dict:
        if frame is None:
            return {"motion": False, "confidence": 0, "contours": [], "bbox": None}

        fg_mask = self.background_subtractor.apply(frame)
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
        fg_mask = cv2.morphologyEx(fg_mask, cv2.MORPH_OPEN, kernel)
        fg_mask = cv2.morphologyEx(fg_mask, cv2.MORPH_CLOSE, kernel)
        fg_mask = cv2.GaussianBlur(fg_mask, (5, 5), 0)

        contours, _ = cv2.findContours(fg_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        motion_detected = False
        max_confidence = 0
        motion_bbox = None

        for contour in contours:
            area = cv2.contourArea(contour)
            if area < self.min_contour_area:
                continue

            if self.motion_zones and not self._check_zone(contour):
                continue

            x, y, w, h = cv2.boundingRect(contour)
            confidence = min(1.0, area / 10000)

            if confidence > max_confidence:
                max_confidence = confidence
                motion_bbox = {"x": int(x), "y": int(y), "w": int(w), "h": int(h)}

            motion_detected = True

        return {
            "motion": motion_detected,
            "confidence": round(max_confidence, 2),
            "bbox": motion_bbox,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

    def _check_zone(self, contour) -> bool:
        if not self.motion_zones:
            return True

        M = cv2.moments(contour)
        if M["m00"] == 0:
            return False
        cx = int(M["m10"] / M["m00"])
        cy = int(M["m01"] / M["m00"])

        for zone in self.motion_zones:
            if not zone.get("active", True):
                continue
            points = zone.get("points", [])
            if len(points) >= 3:
                pts = np.array(points, np.int32)
                result = cv2.pointPolygonTest(pts, (float(cx), float(cy)), False)
                if result >= 0:
                    return True

        return False

    def detect_objects_yolo(self, frame: np.ndarray, net, output_layers) -> List[Dict]:
        height, width = frame.shape[:2]
        blob = cv2.dnn.blobFromImage(frame, 0.00392, (416, 416), (0, 0, 0), True, crop=False)
        net.setInput(blob)
        outs = net.forward(output_layers)

        class_ids = []
        confidences = []
        boxes = []
        labels = ["person", "bicycle", "car", "motorcycle", "airplane", "bus",
                  "train", "truck", "boat", "traffic light", "fire hydrant",
                  "stop sign", "parking meter", "bench", "bird", "cat", "dog"]

        for out in outs:
            for detection in out:
                scores = detection[5:]
                class_id = int(np.argmax(scores))
                confidence = float(scores[class_id])
                if confidence > 0.5 and class_id < len(labels):
                    center_x = int(detection[0] * width)
                    center_y = int(detection[1] * height)
                    w = int(detection[2] * width)
                    h = int(detection[3] * height)
                    x = int(center_x - w / 2)
                    y = int(center_y - h / 2)
                    class_ids.append(class_id)
                    confidences.append(confidence)
                    boxes.append([x, y, w, h])

        indices = cv2.dnn.NMSBoxes(boxes, confidences, 0.5, 0.4)
        detections = []

        if len(indices) > 0:
            for i in indices.flatten():
                detections.append({
                    "label": labels[class_ids[i]] if class_ids[i] < len(labels) else "unknown",
                    "confidence": round(confidences[i], 2),
                    "bbox": {
                        "x": boxes[i][0],
                        "y": boxes[i][1],
                        "w": boxes[i][2],
                        "h": boxes[i][3],
                    },
                })

        return detections


detector = MotionDetector()
