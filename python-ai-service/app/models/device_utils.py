"""PyTorch 设备自动检测工具（支持 MPS / CUDA / CPU）"""
from __future__ import annotations

import contextlib
import logging

logger = logging.getLogger(__name__)


def get_device():
    """返回最优 torch.device：MPS > CUDA > CPU。"""
    import torch

    if torch.backends.mps.is_available():
        logger.info("使用 Apple MPS 加速")
        return torch.device("mps")
    if torch.cuda.is_available():
        logger.info("使用 CUDA 加速")
        return torch.device("cuda")
    logger.info("使用 CPU")
    return torch.device("cpu")


def get_accelerator() -> str:
    """返回 PyTorch Lightning 加速器字符串。"""
    import torch

    if torch.backends.mps.is_available():
        return "mps"
    if torch.cuda.is_available():
        return "gpu"
    return "cpu"


@contextlib.contextmanager
def force_cpu():
    """临时强制 CPU（SHAP DeepExplainer 要求 CPU 设备）。"""
    import torch

    original = torch.device
    try:
        yield torch.device("cpu")
    finally:
        pass  # 恢复无需操作，只用于代码作用域提示
