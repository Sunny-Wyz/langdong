"""深度学习预测模型包"""
from app.models.base import BasePredictor
from app.models.device_utils import get_device, get_accelerator

__all__ = ["BasePredictor", "get_device", "get_accelerator"]
