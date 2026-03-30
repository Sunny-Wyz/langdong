#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
验证脚本：检查 langdong 环境是否正确搭建
用法：python verify_environment.py
"""

import sys
import importlib
from pathlib import Path

def check_package(name, import_name=None):
    """检查包是否可导入"""
    import_name = import_name or name
    try:
        mod = importlib.import_module(import_name)
        version = getattr(mod, "__version__", "?.?.?")
        print(f"   ✅ {name:<20} {version}")
        return True
    except ImportError:
        print(f"   ❌ {name:<20} 未安装")
        return False

def main():
    print("=" * 60)
    print("🔍 langdong 环境验证")
    print("=" * 60)
    print()
    
    # Python 版本
    print("📋 Python 版本：")
    python_version = f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}"
    print(f"   {python_version}")
    if sys.version_info >= (3, 11):
        print("   ✅ 版本正确")
    else:
        print("   ⚠️  警告：期望 Python 3.11+")
    print()
    
    # 核心依赖
    print("📦 核心依赖检查：")
    packages = [
        ("FastAPI", "fastapi"),
        ("Uvicorn", "uvicorn"),
        ("Pydantic", "pydantic"),
        ("SQLAlchemy", "sqlalchemy"),
        ("PyMySQL", "pymysql"),
    ]
    
    core_ok = all(check_package(name, imp) for name, imp in packages)
    print()
    
    # 深度学习框架
    print("🧠 深度学习框架：")
    dl_packages = [
        ("PyTorch", "torch"),
        ("NumPy", "numpy"),
        ("Pandas", "pandas"),
        ("Scikit-learn", "sklearn"),
        ("XGBoost", "xgboost"),
        ("SciPy", "scipy"),
    ]
    
    dl_ok = all(check_package(name, imp) for name, imp in dl_packages)
    print()
    
    # PyTorch 特性检查
    print("🎯 PyTorch 特性检查：")
    try:
        import torch
        print(f"   ✅ PyTorch 版本: {torch.__version__}")
        
        # 检查 MPS（M1 Pro 加速）
        if torch.backends.mps.is_available():
            print(f"   ✅ M1 Pro MPS (Metal) 可用")
        else:
            print(f"   ℹ️  MPS 不可用，将使用 CPU（正常）")
        
        # 检查 CPU
        if torch.cuda.is_available():
            print(f"   ✅ CUDA 可用")
        else:
            print(f"   ℹ️  CUDA 不可用（预期，M1 无 NVIDIA GPU）")
    except Exception as e:
        print(f"   ❌ PyTorch 检查失败: {e}")
    print()
    
    # 模型管理工具
    print("📊 可选工具：")
    optional_packages = [
        ("MLflow", "mlflow"),
        ("pytest", "pytest"),
        ("HTTPX", "httpx"),
        ("python-dotenv", "dotenv"),
    ]
    
    for name, imp in optional_packages:
        check_package(name, imp)
    print()
    
    # 项目结构检查
    print("📁 项目结构检查：")
    required_dirs = [
        "app",
        "app/api",
        "app/api/v1",
        "app/models",
        "app/services",
        "app/utils",
        "training",
        "logs",
        "tests",
    ]
    
    project_root = Path(__file__).parent
    all_dirs_ok = True
    for dir_name in required_dirs:
        dir_path = project_root / dir_name
        if dir_path.exists():
            print(f"   ✅ {dir_name}/")
        else:
            print(f"   ❌ {dir_name}/ （缺失）")
            all_dirs_ok = False
    print()
    
    # 配置文件检查
    print("⚙️  配置文件检查：")
    env_file = project_root / ".env"
    if env_file.exists():
        print(f"   ✅ .env 文件存在")
    else:
        print(f"   ⚠️  .env 文件缺失（请复制 .env.example 并修改）")
    print()
    
    # 总体状态
    print("=" * 60)
    if core_ok and dl_ok and all_dirs_ok:
        print("✅ 环境搭建成功！可以开始开发 FastAPI 服务了。")
        print()
        print("🚀 下一步：")
        print("   1. 编辑 .env 配置数据库连接")
        print("   2. 运行: uvicorn app.main:app --reload --port 8001")
        print("   3. 访问: http://localhost:8001/docs")
        return 0
    else:
        print("⚠️  环境检查发现问题，请根据上面的提示修复。")
        return 1

if __name__ == "__main__":
    sys.exit(main())
