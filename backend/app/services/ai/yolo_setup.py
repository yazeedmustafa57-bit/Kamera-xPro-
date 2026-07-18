import os
import json
import urllib.request
import hashlib
from typing import Optional
from pathlib import Path


YOLO_MODEL_URLS = {
    "yolov4-tiny": "https://github.com/AlexeyAB/darknet/releases/download/darknet_yolo_v4_pre/yolov4-tiny.weights",
    "yolov4-tiny-cfg": "https://raw.githubusercontent.com/AlexeyAB/darknet/master/cfg/yolov4-tiny.cfg",
    "coco-names": "https://raw.githubusercontent.com/AlexeyAB/darknet/master/data/coco.names",
}

YOLO_MODELS_DIR = Path("models/yolo")

COCO_CLASSES = [
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
    "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
    "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
    "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
    "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
    "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
    "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
    "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
    "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
    "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
    "toothbrush"
]

SECURITY_CLASSES = {
    "person": [0],
    "vehicle": [2, 3, 5, 7],  # car, motorcycle, bus, truck
    "animal": [15, 16, 17],  # bird, cat, dog
    "bicycle": [1],
}


class YOLOModelManager:
    def __init__(self):
        self.models_dir = YOLO_MODELS_DIR
        self.models_dir.mkdir(parents=True, exist_ok=True)
        self.weights_path: Optional[Path] = None
        self.config_path: Optional[Path] = None
        self.names_path: Optional[Path] = None
        self.model_loaded = False
        self.net = None
        self.output_layers = None
        self.classes = COCO_CLASSES
        self.download_progress: dict = {}

    def check_models_exist(self) -> bool:
        weights = self.models_dir / "yolov4-tiny.weights"
        config = self.models_dir / "yolov4-tiny.cfg"
        names = self.models_dir / "coco.names"
        return weights.exists() and config.exists() and names.exists()

    async def download_missing_models(self, progress_callback=None):
        """Download YOLO model files if they don't exist."""
        self.download_progress = {}
        tasks = []

        for name, url in [
            ("yolov4-tiny.weights", YOLO_MODEL_URLS["yolov4-tiny"]),
            ("yolov4-tiny.cfg", YOLO_MODEL_URLS["yolov4-tiny-cfg"]),
            ("coco.names", YOLO_MODEL_URLS["coco-names"]),
        ]:
            dest = self.models_dir / name
            if not dest.exists():
                tasks.append(self._download_file(url, dest, name, progress_callback))

        await asyncio.gather(*tasks) if tasks else None

    async def _download_file(self, url: str, dest: Path, name: str, progress_callback=None):
        import asyncio
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, self._download_sync, url, dest, name, progress_callback)

    def _download_sync(self, url: str, dest: Path, name: str, progress_callback=None):
        def report(block_count, block_size, total_size):
            if total_size > 0:
                downloaded = block_count * block_size
                progress = min(100, int(downloaded * 100 / total_size))
                self.download_progress[name] = progress
                if progress_callback:
                    progress_callback(name, progress)

        print(f"[YOLO] Downloading {name} from {url}")
        try:
            urllib.request.urlretrieve(url, str(dest), report)
            print(f"[YOLO] Downloaded {name}")
            self.download_progress[name] = 100
        except Exception as e:
            print(f"[YOLO] Download failed for {name}: {e}")

    def load_model(self) -> bool:
        if self.model_loaded:
            return True

        weights = self.models_dir / "yolov4-tiny.weights"
        config = self.models_dir / "yolov4-tiny.cfg"
        names = self.models_dir / "coco.names"

        if not weights.exists() or not config.exists():
            print("[YOLO] Model files not found. Call download_missing_models() first.")
            return False

        try:
            self.net = cv2.dnn.readNet(str(weights), str(config))

            if cv2.cuda.getCudaEnabledDeviceCount() > 0:
                self.net.setPreferableBackend(cv2.dnn.DNN_BACKEND_CUDA)
                self.net.setPreferableTarget(cv2.dnn.DNN_TARGET_CUDA)
            else:
                self.net.setPreferableBackend(cv2.dnn.DNN_BACKEND_OPENCV)
                self.net.setPreferableTarget(cv2.dnn.DNN_TARGET_CPU)

            layer_names = self.net.getLayerNames()
            unconnected = self.net.getUnconnectedOutLayers()
            self.output_layers = [layer_names[i - 1] for i in unconnected.flatten()]

            if names.exists():
                with open(names, "r") as f:
                    self.classes = [line.strip() for line in f.readlines()]

            self.model_loaded = True
            print(f"[YOLO] Model loaded successfully ({'CUDA' if cv2.cuda.getCudaEnabledDeviceCount() > 0 else 'CPU'})")
            return True
        except Exception as e:
            print(f"[YOLO] Failed to load model: {e}")
            return False

    def unload_model(self):
        self.net = None
        self.output_layers = None
        self.model_loaded = False
        print("[YOLO] Model unloaded")


yolo_manager = YOLOModelManager()
