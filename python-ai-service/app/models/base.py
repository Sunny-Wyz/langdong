"""所有预测模型的抽象基类"""
from __future__ import annotations

from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any

import pandas as pd


class BasePredictor(ABC):
    """定义 train / predict / explain / save / load 统一接口"""

    model_name: str = "base"
    model_version: str = "0.0.0"

    @abstractmethod
    def train(self, df: pd.DataFrame, **kwargs) -> dict[str, float]:
        """训练模型，返回评估指标字典。"""

    @abstractmethod
    def predict(self, df: pd.DataFrame, **kwargs) -> list[dict[str, Any]]:
        """推理，返回预测结果列表。"""

    @abstractmethod
    def explain(self, df: pd.DataFrame, **kwargs) -> dict[str, Any]:
        """可解释性分析，返回特征重要度等。"""

    @abstractmethod
    def save(self, path: Path) -> None:
        """持久化模型到指定路径。"""

    @abstractmethod
    def load(self, path: Path) -> None:
        """从指定路径加载模型。"""

    def is_trained(self) -> bool:
        return getattr(self, "_trained", False)
