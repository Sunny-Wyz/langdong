#!/bin/bash

###############################################################################
# 自动化部署脚本：搭建 langdong Conda 环境（M1 Pro 优化）
# 用途：为 Python AI 微服务创建轻量级推理环境
# 环境名：langdong
# Python：3.11
# PyTorch：CPU/MPS（M1 原生优化，无 CUDA）
###############################################################################

set -e  # 任何命令失败都中止脚本

echo "=========================================="
echo "🚀 开始搭建 langdong 环境（M1 Pro 推理优化）"
echo "=========================================="
echo ""

# ==================== 1. 检查 Conda 是否安装 ====================
echo "📋 [步骤 1] 检查 Conda 环境..."
if ! command -v conda &> /dev/null; then
    echo "❌ Conda 未安装，请先安装 Anaconda 或 Miniconda"
    exit 1
fi

CONDA_VERSION=$(conda --version)
echo "✅ $CONDA_VERSION"
echo ""

# ==================== 2. 检查是否已存在同名环境 ====================
echo "📋 [步骤 2] 检查 langdong 环境是否已存在..."
if conda env list | grep -q "^langdong"; then
    echo "⚠️  环境 'langdong' 已存在"
    read -p "是否删除并重建？(y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🗑️  删除旧环境..."
        conda remove -n langdong --all -y > /dev/null 2>&1
        echo "✅ 已删除"
    else
        echo "⏭️  跳过创建，激活现有环境..."
        conda activate langdong
        echo "✅ 已激活 langdong 环境"
        exit 0
    fi
fi
echo ""

# ==================== 3. 检查磁盘空间 ====================
echo "📋 [步骤 3] 检查磁盘空间..."
AVAILABLE_SPACE=$(($(df / | tail -1 | awk '{print $4}') / 1024 / 1024))  # GB
if [ $AVAILABLE_SPACE -lt 20 ]; then
    echo "⚠️  警告：磁盘剩余空间 ${AVAILABLE_SPACE}GB < 20GB"
    echo "    环境安装可能会失败。建议清理磁盘后重试。"
else
    echo "✅ 磁盘空间充足 (${AVAILABLE_SPACE}GB)"
fi
echo ""

# ==================== 4. 获取脚本所在目录 ====================
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CONDA_YAML="${SCRIPT_DIR}/environment.yml"

if [ ! -f "$CONDA_YAML" ]; then
    echo "❌ 错误：找不到 environment.yml 文件"
    echo "   期望路径：$CONDA_YAML"
    exit 1
fi
echo "✅ 找到 environment.yml"
echo ""

# ==================== 5. 创建 Conda 环境 ====================
echo "📋 [步骤 4] 创建 Conda 环境 (langdong, Python 3.11)..."
echo "   这可能需要 5-10 分钟，请耐心等待..."
conda env create -f "$CONDA_YAML" -q

if [ $? -eq 0 ]; then
    echo "✅ 环境创建成功"
else
    echo "❌ 环境创建失败，请检查网络连接和磁盘空间"
    exit 1
fi
echo ""

# ==================== 6. 激活环境 ====================
echo "📋 [步骤 5] 激活 langdong 环境..."
source $(conda info --base)/etc/profile.d/conda.sh
conda activate langdong
echo "✅ 已激活 langdong 环境"
echo ""

# ==================== 7. 验证 Python 版本 ====================
echo "📋 [步骤 6] 验证 Python 版本..."
PYTHON_VERSION=$(python --version 2>&1)
echo "   $PYTHON_VERSION"
if [[ "$PYTHON_VERSION" == *"3.11"* ]]; then
    echo "✅ Python 版本正确"
else
    echo "⚠️  警告：期望 Python 3.11，但得到 $PYTHON_VERSION"
fi
echo ""

# ==================== 8. 验证关键包 ====================
echo "📋 [步骤 7] 验证关键包安装..."
PACKAGES=("fastapi" "uvicorn" "torch" "scipy" "pandas" "numpy" "sklearn" "xgboost")
FAILED=0

for pkg in "${PACKAGES[@]}"; do
    if python -c "import ${pkg}" 2>/dev/null; then
        echo "   ✅ $pkg"
    else
        echo "   ❌ $pkg （未安装）"
        FAILED=$((FAILED + 1))
    fi
done

if [ $FAILED -gt 0 ]; then
    echo "⚠️  警告：有 $FAILED 个包未正确安装"
else
    echo "✅ 所有关键包均已安装"
fi
echo ""

# ==================== 9. 验证 PyTorch 配置 ====================
echo "📋 [步骤 8] 验证 PyTorch 配置..."
python << 'EOF'
import torch
print(f"   PyTorch 版本: {torch.__version__}")
print(f"   是否支持 MPS（Metal）: {torch.backends.mps.is_available()}")
if torch.backends.mps.is_available():
    print("   ✅ M1 Pro MPS 加速可用（推理时自动使用）")
else:
    print("   ℹ️  将使用 CPU 推理（速度较慢，但仍可用）")
EOF
echo ""

# ==================== 10. 创建项目目录结构 ====================
echo "📋 [步骤 9] 创建项目目录结构..."
DIRS=(
    "${SCRIPT_DIR}/app"
    "${SCRIPT_DIR}/app/api"
    "${SCRIPT_DIR}/app/api/v1"
    "${SCRIPT_DIR}/app/models"
    "${SCRIPT_DIR}/app/services"
    "${SCRIPT_DIR}/app/utils"
    "${SCRIPT_DIR}/training"
    "${SCRIPT_DIR}/logs"
    "${SCRIPT_DIR}/tests"
    "${SCRIPT_DIR}/data"
)

for dir in "${DIRS[@]}"; do
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir"
        echo "   📁 创建 $dir"
    fi
done
echo "✅ 目录结构完成"
echo ""

# ==================== 11. 创建 .env 文件 ====================
echo "📋 [步骤 10] 配置环境变量..."
if [ ! -f "${SCRIPT_DIR}/.env" ]; then
    if [ -f "${SCRIPT_DIR}/.env.example" ]; then
        cp "${SCRIPT_DIR}/.env.example" "${SCRIPT_DIR}/.env"
        echo "✅ 已从 .env.example 创建 .env 文件"
    fi
else
    echo "ℹ️  .env 文件已存在，跳过创建"
fi
echo ""

# ==================== 12. 完成 ====================
echo "=========================================="
echo "🎉 环境搭建完成！"
echo "=========================================="
echo ""
echo "📝 后续步骤："
echo ""
echo "1️⃣  激活环境（如果窗口已关闭）："
echo "    conda activate langdong"
echo ""
echo "2️⃣  进入项目目录："
echo "    cd python-ai-service"
echo ""
echo "3️⃣  配置数据库连接（编辑 .env）："
echo "    nano .env"
echo ""
echo "4️⃣  运行快速测试："
echo "    python -c \"import torch; print(f'PyTorch: {torch.__version__}')\" "
echo ""
echo "5️⃣  启动 FastAPI 开发服务器（后续）："
echo "    uvicorn app.main:app --reload --port 8001"
echo ""
echo "✨ 准备就绪！开始构建 FastAPI 微服务吧！"
echo ""
