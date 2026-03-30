#!/bin/bash

# langdong 环境快速启动脚本

echo "🚀 langdong 环境快速启动"
echo "========================"
echo ""

# 激活 langdong 环境
eval "$(conda shell.bash hook)"
conda activate langdong

if [ $? -ne 0 ]; then
    echo "❌ 无法激活 langdong 环境"
    exit 1
fi

echo "✅ 已激活 langdong 环境"
echo ""

# 显示环境信息
echo "📋 环境信息："
python --version
python -c "import torch; print(f'PyTorch: {torch.__version__}')" 2>/dev/null || echo "PyTorch: 检查中..."
python -c "import fastapi; print(f'FastAPI: {fastapi.__version__}')" 2>/dev/null || echo "FastAPI: 检查中..."
echo ""

echo "🎯 可用命令："
echo ""
echo "1️⃣  启动 FastAPI 开发服务器："
echo "   uvicorn app.main:app --reload --port 8001"
echo ""
echo "2️⃣  访问 API 文档："
echo "   http://localhost:8001/docs"
echo ""
echo "3️⃣  运行单元测试："
echo "   pytest tests/"
echo ""
echo "4️⃣  查看已安装的包："
echo "   pip list"
echo ""
echo "✨ 开始开发吧！"
