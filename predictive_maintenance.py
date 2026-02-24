"""
predictive_maintenance.py
=========================
备件管理系统 — AI 预测性维护模块

功能概述：
  - 基于 LSTM 时间序列模型预测备件剩余寿命（RUL）
  - 使用 Monte Carlo Dropout 生成 95% 置信区间
  - 使用 SHAP KernelExplainer 解释关键特征
  - 数据不足时自动降级（合成数据 / 预训练模型 / 默认估计）
  - RUL < 安全阈值时触发预警

集成方式：
  from predictive_maintenance import predict_rul
  result = predict_rul(spare_part_id=42)

依赖安装：
  pip install tensorflow scikit-learn pandas numpy pymysql sqlalchemy shap scipy
"""

# =============================================================================
# Section 1: 导入库
# =============================================================================
import os
import json
import pickle
import logging
import warnings
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple, Any

import numpy as np
import pandas as pd
from scipy import stats
from sklearn.preprocessing import MinMaxScaler
from sklearn.model_selection import train_test_split

# TensorFlow / Keras（抑制冗余日志）
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers, callbacks, models

# 数据库
import pymysql
from sqlalchemy import create_engine, text

# SHAP 可解释性（懒加载，避免启动时间过长）
try:
    import shap
    SHAP_AVAILABLE = True
except ImportError:
    SHAP_AVAILABLE = False

warnings.filterwarnings("ignore")

# =============================================================================
# Section 2: 全局配置
# =============================================================================

# 数据库连接参数（根据实际环境修改）
DB_CONFIG: Dict[str, Any] = {
    "host":     "localhost",
    "port":     3306,
    "user":     "root",
    "password": "your_password",   # ← 生产环境从环境变量读取：os.environ["DB_PASSWORD"]
    "database": "spare_db",
    "charset":  "utf8mb4",
}

# 模型与训练超参数
MODEL_CONFIG: Dict[str, Any] = {
    # 时序窗口
    "sequence_length": 30,          # 滑动窗口大小（时间步数）
    # 输入特征列（与数据库字段对应）
    "features": [
        "operating_hours",          # 累计运行小时（最重要）
        "temperature",              # 温度 (°C)
        "vibration",                # 振动幅度 (mm/s)
        "pressure",                 # 压力 (MPa)
        "current_load",             # 电流负载 (A)
        "rpm",                      # 转速 (r/min)
        "error_code",               # 错误码（0=正常）
    ],
    # LSTM 架构
    "lstm_units_1":    128,
    "lstm_units_2":    64,
    "dense_units":     32,
    "dropout_rate":    0.2,
    # 训练参数
    "batch_size":      32,
    "epochs":          50,
    "learning_rate":   0.001,
    "validation_split": 0.15,
    "early_stopping_patience": 10,
    # 推断参数
    "mc_samples":      100,         # Monte Carlo Dropout 采样次数
    # 业务阈值
    "rul_threshold":   500.0,       # RUL 安全阈值（小时）
    "min_data_points": 100,         # 正常训练所需最少数据量
    # 文件路径
    "model_path":      "rul_model.h5",
    "scaler_path":     "rul_scaler.pkl",
    # 模拟数据中的备件预期总寿命（小时）
    "simulated_total_life": 2000.0,
}

# =============================================================================
# Section 3: 日志配置
# =============================================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("predictive_maintenance")


# =============================================================================
# Section 4: 数据库连接与数据加载
# =============================================================================

# 传感器日志表的 DDL（首次运行时自动创建）
_CREATE_SENSOR_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `spare_part_sensor_log` (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    spare_part_id  BIGINT      NOT NULL                COMMENT '关联备件 ID',
    recorded_at    DATETIME    NOT NULL                COMMENT '数据采集时间',
    operating_hours FLOAT      DEFAULT 0.0             COMMENT '累计运行小时',
    temperature    FLOAT       DEFAULT NULL            COMMENT '温度 (°C)',
    vibration      FLOAT       DEFAULT NULL            COMMENT '振动幅度 (mm/s)',
    pressure       FLOAT       DEFAULT NULL            COMMENT '压力 (MPa)',
    current_load   FLOAT       DEFAULT NULL            COMMENT '电流负载 (A)',
    rpm            FLOAT       DEFAULT NULL            COMMENT '转速 (r/min)',
    error_code     INT         DEFAULT 0               COMMENT '错误码（0=正常）',
    is_failed      TINYINT(1)  DEFAULT 0               COMMENT '是否故障（1=是）',
    PRIMARY KEY (id),
    KEY idx_spare_part (spare_part_id),
    KEY idx_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件传感器历史数据';
"""


def get_db_engine():
    """
    创建 SQLAlchemy 数据库引擎。
    连接参数从 DB_CONFIG 读取，生产环境建议通过环境变量注入密码。
    """
    cfg = DB_CONFIG
    url = (
        f"mysql+pymysql://{cfg['user']}:{cfg['password']}"
        f"@{cfg['host']}:{cfg['port']}/{cfg['database']}"
        f"?charset={cfg['charset']}"
    )
    engine = create_engine(url, pool_pre_ping=True, echo=False)
    return engine


def ensure_sensor_table_exists(engine) -> None:
    """
    检查并创建传感器日志表（如果不存在）。
    幂等操作，多次调用安全。
    """
    with engine.connect() as conn:
        conn.execute(text(_CREATE_SENSOR_TABLE_SQL))
        conn.commit()
    logger.info("传感器日志表已就绪。")


def load_sensor_data(spare_part_id: int, engine) -> pd.DataFrame:
    """
    从数据库加载指定备件的历史传感器数据，按时间升序排列。

    参数：
        spare_part_id: 备件主键 ID
        engine:        SQLAlchemy 引擎

    返回：
        DataFrame，列包含 MODEL_CONFIG['features'] 以及 'recorded_at'。
        若备件不存在，返回空 DataFrame。
    """
    sql = text("""
        SELECT recorded_at,
               operating_hours, temperature, vibration,
               pressure, current_load, rpm, error_code
        FROM   spare_part_sensor_log
        WHERE  spare_part_id = :sid
        ORDER  BY recorded_at ASC
    """)
    with engine.connect() as conn:
        df = pd.read_sql(sql, conn, params={"sid": spare_part_id})

    logger.info(f"备件 ID={spare_part_id}：从数据库加载 {len(df)} 条传感器记录。")
    return df


# =============================================================================
# Section 5: 数据预处理
# =============================================================================

def generate_synthetic_data(existing_df: pd.DataFrame, target_count: int = 200) -> pd.DataFrame:
    """
    当真实数据不足 100 条时，基于物理退化模型生成合成传感器数据以补全训练集。

    退化模型：
        x(t) = baseline + slope * t + A * sin(2π * f * t) + N(0, σ²)

    参数：
        existing_df:  已有的真实数据（可为空 DataFrame）
        target_count: 目标总数据量

    返回：
        合并后的 DataFrame（真实数据 + 合成数据），按 operating_hours 升序。
    """
    features = MODEL_CONFIG["features"]
    total_life = MODEL_CONFIG["simulated_total_life"]
    n_synthetic = max(0, target_count - len(existing_df))

    if n_synthetic == 0:
        return existing_df

    logger.warning(f"数据不足，生成 {n_synthetic} 条合成数据补充训练集。")

    # 从已有数据估计统计特性，若无数据则使用工程默认值
    if len(existing_df) > 0:
        means  = existing_df[features].mean().to_dict()
        stds   = existing_df[features].std().fillna(1.0).to_dict()
        max_oh = existing_df["operating_hours"].max() if "operating_hours" in existing_df else 0.0
    else:
        means  = dict(zip(features, [0, 75, 2.0, 1.0, 10, 1500, 0]))
        stds   = dict(zip(features, [1, 5,  0.5, 0.1, 1,  100,  0.1]))
        max_oh = 0.0

    # 生成时间序列（从当前运行小时向后延伸）
    t = np.linspace(max_oh, max_oh + n_synthetic, n_synthetic)

    rows = []
    for i, ti in enumerate(t):
        # 退化进度（0~1）
        degradation_ratio = min(ti / total_life, 1.0)
        row = {}

        # operating_hours：单调递增
        row["operating_hours"] = ti

        # 其他特征：随退化进度线性漂移 + 周期波动 + 随机噪声
        for feat in features:
            if feat == "operating_hours":
                continue
            if feat == "error_code":
                # 错误码：退化后期出现概率上升
                row[feat] = float(np.random.choice([0, 1], p=[1 - degradation_ratio * 0.3,
                                                               degradation_ratio * 0.3]))
            else:
                baseline  = means.get(feat, 0)
                slope     = stds.get(feat, 1) * 0.3 * degradation_ratio
                amplitude = stds.get(feat, 1) * 0.2
                frequency = 0.05
                noise     = np.random.normal(0, stds.get(feat, 1) * 0.1)
                row[feat] = baseline + slope + amplitude * np.sin(2 * np.pi * frequency * i) + noise

        rows.append(row)

    synthetic_df = pd.DataFrame(rows)
    synthetic_df["recorded_at"] = pd.date_range(
        start=datetime.now() - timedelta(hours=n_synthetic),
        periods=n_synthetic,
        freq="h",
    )

    combined = pd.concat([existing_df, synthetic_df], ignore_index=True)
    combined.sort_values("operating_hours", inplace=True)
    combined.reset_index(drop=True, inplace=True)
    return combined


def compute_rul_labels(df: pd.DataFrame, total_life: float) -> pd.Series:
    """
    计算每个时间步的 RUL 标签（小时）。

    RUL_t = max(0, total_life - operating_hours_t)

    参数：
        df:         传感器数据 DataFrame
        total_life: 备件预期总寿命（小时）

    返回：
        RUL 序列（pandas Series）
    """
    rul = (total_life - df["operating_hours"]).clip(lower=0.0)
    return rul


def preprocess_data(
    df: pd.DataFrame,
    scaler: Optional[MinMaxScaler] = None,
) -> Tuple[np.ndarray, MinMaxScaler]:
    """
    数据预处理：填充缺失值 → Min-Max 归一化。

    参数：
        df:     传感器数据 DataFrame
        scaler: 已有的 MinMaxScaler（推断时传入，训练时传 None）

    返回：
        (归一化后的特征数组, scaler)
    """
    features = MODEL_CONFIG["features"]

    # 1. 选取特征列，用前向填充 + 均值填充处理缺失值
    X = df[features].copy()
    X.ffill(inplace=True)
    X.fillna(X.mean(), inplace=True)

    # 2. 归一化
    if scaler is None:
        scaler = MinMaxScaler(feature_range=(0, 1))
        X_scaled = scaler.fit_transform(X.values)
    else:
        X_scaled = scaler.transform(X.values)

    return X_scaled, scaler


def create_sequences(
    X: np.ndarray,
    y: np.ndarray,
    seq_len: int,
) -> Tuple[np.ndarray, np.ndarray]:
    """
    将平铺的时序数据切割为滑动窗口序列（供 LSTM 使用）。

    输入形状：(n_samples, n_features)
    输出形状：X_seq=(n_windows, seq_len, n_features)，y_seq=(n_windows,)

    参数：
        X:       归一化后的特征数组
        y:       RUL 标签数组
        seq_len: 滑动窗口大小

    返回：
        (X_sequences, y_sequences)
    """
    X_seq, y_seq = [], []
    for i in range(len(X) - seq_len):
        X_seq.append(X[i : i + seq_len])
        y_seq.append(y[i + seq_len])   # 预测窗口末尾时刻的 RUL
    return np.array(X_seq, dtype=np.float32), np.array(y_seq, dtype=np.float32)


# =============================================================================
# Section 6: 模型构建、训练、保存与加载
# =============================================================================

def build_lstm_model(input_shape: Tuple[int, int]) -> keras.Model:
    """
    构建双层 LSTM 回归模型。

    架构：
        LSTM(128) → Dropout → LSTM(64) → Dropout → Dense(32) → Dense(1)

    参数：
        input_shape: (sequence_length, n_features)

    返回：
        编译好的 Keras 模型
    """
    cfg = MODEL_CONFIG

    model = keras.Sequential(
        [
            # 第一层 LSTM：保留序列输出，捕捉局部时序模式
            layers.LSTM(
                cfg["lstm_units_1"],
                return_sequences=True,
                input_shape=input_shape,
                name="lstm_1",
            ),
            layers.Dropout(cfg["dropout_rate"], name="dropout_1"),

            # 第二层 LSTM：汇聚为固定维度向量
            layers.LSTM(cfg["lstm_units_2"], return_sequences=False, name="lstm_2"),
            layers.Dropout(cfg["dropout_rate"], name="dropout_2"),

            # 全连接层
            layers.Dense(cfg["dense_units"], activation="relu", name="dense_1"),

            # 输出层：RUL 回归（线性激活）
            layers.Dense(1, activation="linear", name="output"),
        ],
        name="RUL_LSTM",
    )

    optimizer = keras.optimizers.Adam(learning_rate=cfg["learning_rate"])

    # Huber Loss 对异常值鲁棒，适合 RUL 回归
    model.compile(optimizer=optimizer, loss="huber", metrics=["mae"])

    logger.info(f"LSTM 模型构建完成，参数量：{model.count_params():,}")
    return model


def save_model(model: keras.Model, scaler: MinMaxScaler) -> None:
    """
    保存模型权重（.h5）和特征标准化器（.pkl）。
    两者必须配套保存，否则推断时特征尺度不一致。
    """
    model_path  = MODEL_CONFIG["model_path"]
    scaler_path = MODEL_CONFIG["scaler_path"]

    model.save(model_path)
    with open(scaler_path, "wb") as f:
        pickle.dump(scaler, f)

    logger.info(f"模型已保存至 {model_path}，Scaler 已保存至 {scaler_path}。")


def load_model_and_scaler() -> Tuple[Optional[keras.Model], Optional[MinMaxScaler]]:
    """
    加载已保存的模型和标准化器。
    若文件不存在，返回 (None, None)。
    """
    model_path  = MODEL_CONFIG["model_path"]
    scaler_path = MODEL_CONFIG["scaler_path"]

    if not (os.path.exists(model_path) and os.path.exists(scaler_path)):
        logger.warning("未找到预训练模型文件，将重新训练或使用默认估计。")
        return None, None

    try:
        model = models.load_model(model_path)
        with open(scaler_path, "rb") as f:
            scaler = pickle.load(f)
        logger.info(f"预训练模型已从 {model_path} 加载。")
        return model, scaler
    except Exception as exc:
        logger.error(f"模型加载失败：{exc}")
        return None, None


def train_model(
    spare_part_id: int,
    df: pd.DataFrame,
    retrain: bool = False,
) -> Tuple[keras.Model, MinMaxScaler]:
    """
    训练（或加载）RUL 预测模型。

    策略：
      - 若已有模型文件且 retrain=False，直接加载返回。
      - 若数据 ≥ 100 条，正常训练。
      - 若数据 10~99 条，生成合成数据补充至 200 条再训练。
      - 若数据 < 10 条，加载预训练模型；若无预训练模型则报错。

    参数：
        spare_part_id: 备件 ID（用于日志）
        df:            传感器历史数据
        retrain:       是否强制重训练

    返回：
        (model, scaler)
    """
    cfg        = MODEL_CONFIG
    seq_len    = cfg["sequence_length"]
    min_points = cfg["min_data_points"]
    total_life = cfg["simulated_total_life"]

    # --- 尝试加载已有模型 ---
    if not retrain:
        model, scaler = load_model_and_scaler()
        if model is not None:
            return model, scaler

    n_records = len(df)
    logger.info(f"备件 ID={spare_part_id}：当前历史记录 {n_records} 条。")

    # --- 数据不足处理 ---
    if n_records < 10:
        # 极度不足：尝试加载预训练模型
        model, scaler = load_model_and_scaler()
        if model is not None:
            logger.warning(f"数据极少（{n_records} 条），使用通用预训练模型。")
            return model, scaler
        raise ValueError(
            f"备件 ID={spare_part_id} 历史数据仅 {n_records} 条（<10），"
            "且无预训练模型可用，无法预测。请先积累更多传感器数据。"
        )

    if n_records < min_points:
        # 数据不足但可补充：生成合成数据
        logger.warning(f"数据不足 {min_points} 条，使用合成数据增强。")
        df = generate_synthetic_data(df, target_count=200)

    # --- 数据预处理 ---
    X_scaled, scaler = preprocess_data(df)
    y = compute_rul_labels(df, total_life).values

    X_seq, y_seq = create_sequences(X_scaled, y, seq_len)

    if len(X_seq) < 10:
        raise ValueError(f"有效训练样本数（{len(X_seq)}）过少，无法训练。")

    X_train, X_val, y_train, y_val = train_test_split(
        X_seq, y_seq,
        test_size=cfg["validation_split"],
        shuffle=False,  # 时序数据不打乱
    )

    # --- 构建并训练模型 ---
    input_shape = (seq_len, len(cfg["features"]))
    model = build_lstm_model(input_shape)

    cb_list = [
        callbacks.EarlyStopping(
            monitor="val_loss",
            patience=cfg["early_stopping_patience"],
            restore_best_weights=True,
            verbose=0,
        ),
        callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=5,
            min_lr=1e-6,
            verbose=0,
        ),
    ]

    logger.info("开始训练 LSTM 模型……")
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=cfg["epochs"],
        batch_size=cfg["batch_size"],
        callbacks=cb_list,
        verbose=0,
    )

    best_val_loss = min(history.history["val_loss"])
    best_val_mae  = min(history.history["val_mae"])
    logger.info(
        f"训练完成：最佳验证损失={best_val_loss:.2f}，"
        f"最佳验证 MAE={best_val_mae:.2f} h"
    )

    # --- 保存模型 ---
    save_model(model, scaler)
    return model, scaler


# =============================================================================
# Section 7: 预测与解释
# =============================================================================

def monte_carlo_predict(
    model: keras.Model,
    X_input: np.ndarray,
    n_samples: int = 100,
) -> Tuple[float, float, float]:
    """
    使用 Monte Carlo Dropout 进行不确定性量化预测。

    推断时保持 training=True（激活 Dropout），多次前向传播采样后验分布。

    参数：
        model:    Keras LSTM 模型
        X_input:  输入序列，形状 (1, seq_len, n_features)
        n_samples: 采样次数（默认 100）

    返回：
        (mean_rul, ci_lower_95, ci_upper_95)
    """
    preds = np.array(
        [model(X_input, training=True).numpy().flatten()[0] for _ in range(n_samples)]
    )
    mean_rul  = float(np.mean(preds))
    std_rul   = float(np.std(preds))

    # 95% 置信区间（正态近似）
    ci_lower  = float(max(0.0, mean_rul - 1.96 * std_rul))
    ci_upper  = float(mean_rul + 1.96 * std_rul)

    return mean_rul, ci_lower, ci_upper


def get_shap_explanation(
    model: keras.Model,
    X_seq: np.ndarray,
    background: np.ndarray,
    feature_names: List[str],
) -> Dict[str, Any]:
    """
    使用 SHAP KernelExplainer 解释预测结果，返回 Top-3 关键特征。

    策略：
      1. 将 3D 输入展平为 2D（KernelExplainer 要求）
      2. 用 K-means 聚类背景数据降低计算量
      3. 对每个特征跨时间步求均值，汇总为特征级重要性

    参数：
        model:        Keras 模型
        X_seq:        待解释样本，形状 (1, seq_len, n_features)
        background:   背景数据集，形状 (N, seq_len, n_features)
        feature_names: 特征名列表

    返回：
        特征重要性字典（Top-3）
    """
    if not SHAP_AVAILABLE:
        return {"error": "SHAP 未安装，请执行 pip install shap"}

    seq_len, n_features = X_seq.shape[1], X_seq.shape[2]
    X_flat  = X_seq.reshape(1, -1)
    bg_flat = background.reshape(background.shape[0], -1)

    # 取最多 200 个背景样本避免计算超时
    bg_flat = bg_flat[: min(200, len(bg_flat))]

    # 模型预测包装器（输入 2D，输出 1D）
    def model_predict(X_2d: np.ndarray) -> np.ndarray:
        X_3d = X_2d.reshape(-1, seq_len, n_features)
        # 使用 training=False 保证解释时的确定性
        return model(X_3d, training=False).numpy().flatten()

    try:
        # K-means 摘要降低背景数据量
        bg_summary = shap.kmeans(bg_flat, min(50, len(bg_flat)))
        explainer  = shap.KernelExplainer(model_predict, bg_summary)
        shap_vals  = explainer.shap_values(X_flat, nsamples=200, silent=True)

        # shap_vals 形状: (1, seq_len * n_features)
        # 重塑为 (seq_len, n_features) 并跨时间步求绝对均值
        shap_matrix      = np.array(shap_vals).reshape(seq_len, n_features)
        importance_abs   = np.abs(shap_matrix).mean(axis=0)
        importance_dir   = shap_matrix[-1]  # 以最新时间步的方向为准

        top_indices = np.argsort(importance_abs)[::-1][:3]

        explanation: Dict[str, Any] = {}
        for rank, idx in enumerate(top_indices):
            fname     = feature_names[idx]
            imp       = float(importance_abs[idx])
            direction = "正向影响（缩短寿命）" if importance_dir[idx] > 0 else "负向影响（延长寿命）"
            explanation[f"rank_{rank + 1}"] = {
                "feature":    fname,
                "importance": round(imp, 6),
                "direction":  direction,
            }

        return explanation

    except Exception as exc:
        logger.error(f"SHAP 计算失败：{exc}")
        return {"error": str(exc)}


def predict_rul(spare_part_id: int) -> Dict[str, Any]:
    """
    核心预测接口：给定备件 ID，预测其剩余使用寿命（RUL）。

    返回字典包含：
      - spare_part_id:   备件 ID
      - predicted_rul:   预测 RUL（小时）
      - confidence_interval: 95% 置信区间 [lower, upper]
      - alert:           预警级别（OK / WARNING / CRITICAL）
      - alert_message:   预警说明（中文）
      - top_features:    SHAP Top-3 关键特征
      - data_quality:    数据质量说明
      - timestamp:       预测时间

    参数：
        spare_part_id: 备件主键 ID（对应 spare_part 表的 id 字段）

    返回：
        结构化预测结果字典
    """
    cfg       = MODEL_CONFIG
    threshold = cfg["rul_threshold"]
    seq_len   = cfg["sequence_length"]
    features  = cfg["features"]
    timestamp = datetime.now().isoformat()

    result_base = {
        "spare_part_id": spare_part_id,
        "timestamp":     timestamp,
    }

    # ------------------------------------------------------------------
    # Step 1: 连接数据库，加载传感器历史数据
    # ------------------------------------------------------------------
    try:
        engine = get_db_engine()
        ensure_sensor_table_exists(engine)
        df = load_sensor_data(spare_part_id, engine)
    except Exception as exc:
        logger.error(f"数据库连接/查询失败：{exc}")
        return {
            **result_base,
            "predicted_rul":       None,
            "confidence_interval": None,
            "alert":               "ERROR",
            "alert_message":       f"数据库访问失败：{exc}",
            "top_features":        {},
            "data_quality":        "database_error",
        }

    n_records = len(df)

    # ------------------------------------------------------------------
    # Step 2: 处理零数据场景（无任何历史记录）
    # ------------------------------------------------------------------
    if n_records == 0:
        logger.warning(f"备件 ID={spare_part_id} 无历史传感器数据，返回保守默认估计。")
        default_rul = cfg["simulated_total_life"] * 0.5  # 保守估计：50% 总寿命

        return {
            **result_base,
            "predicted_rul":       default_rul,
            "confidence_interval": [0.0, cfg["simulated_total_life"]],
            "alert":               "WARNING",
            "alert_message":       (
                f"备件 ID={spare_part_id} 无历史传感器数据，"
                f"返回保守默认估计 RUL={default_rul:.0f}h。"
                "请尽快接入传感器采集设备以获得准确预测。"
            ),
            "top_features":        {},
            "data_quality":        "no_data_default_estimate",
        }

    # ------------------------------------------------------------------
    # Step 3: 训练或加载模型
    # ------------------------------------------------------------------
    data_quality = "sufficient" if n_records >= cfg["min_data_points"] else "insufficient_augmented"
    if n_records < 10:
        data_quality = "critical_low_pretrained"

    try:
        model, scaler = train_model(spare_part_id, df)
    except ValueError as exc:
        logger.error(str(exc))
        return {
            **result_base,
            "predicted_rul":       None,
            "confidence_interval": None,
            "alert":               "ERROR",
            "alert_message":       str(exc),
            "top_features":        {},
            "data_quality":        data_quality,
        }

    # ------------------------------------------------------------------
    # Step 4: 数据预处理 → 构造最新窗口序列
    # ------------------------------------------------------------------
    # 若数据不足 seq_len，用零填充（pad）
    if n_records < seq_len:
        pad_rows    = seq_len - n_records
        pad_df      = pd.DataFrame(
            np.zeros((pad_rows, len(features))), columns=features
        )
        pad_df["recorded_at"] = pd.NaT
        df = pd.concat([pad_df, df], ignore_index=True)
        logger.warning(f"数据量 {n_records} < 窗口大小 {seq_len}，已在头部填充零值。")

    X_scaled, _ = preprocess_data(df, scaler=scaler)

    # 取最后 seq_len 个时间步作为输入窗口
    X_window = X_scaled[-seq_len:].reshape(1, seq_len, len(features)).astype(np.float32)

    # ------------------------------------------------------------------
    # Step 5: Monte Carlo Dropout 预测 + 置信区间
    # ------------------------------------------------------------------
    predicted_rul, ci_lower, ci_upper = monte_carlo_predict(
        model, X_window, n_samples=cfg["mc_samples"]
    )
    logger.info(
        f"备件 ID={spare_part_id} 预测 RUL={predicted_rul:.1f}h，"
        f"95% CI=[{ci_lower:.1f}, {ci_upper:.1f}]"
    )

    # ------------------------------------------------------------------
    # Step 6: SHAP 特征重要性解释
    # ------------------------------------------------------------------
    # 背景数据：取训练集前 100 条序列（若足够）
    if n_records >= seq_len + 1:
        y_dummy  = np.zeros(len(X_scaled))
        X_all, _ = create_sequences(X_scaled, y_dummy, seq_len)
        background = X_all[: min(100, len(X_all))]
    else:
        background = X_window  # 数据太少时用自身作背景

    top_features = get_shap_explanation(model, X_window, background, features)

    # ------------------------------------------------------------------
    # Step 7: 预警判断
    # ------------------------------------------------------------------
    if predicted_rul < threshold:
        alert         = "CRITICAL"
        alert_message = (
            f"【紧急预警】备件 ID={spare_part_id} 预测剩余寿命 {predicted_rul:.1f}h，"
            f"已低于安全阈值 {threshold:.0f}h，请立即安排维修或更换！"
        )
    elif predicted_rul < threshold * 2:
        alert         = "WARNING"
        alert_message = (
            f"【预警提示】备件 ID={spare_part_id} 预测剩余寿命 {predicted_rul:.1f}h，"
            f"建议在近期计划维修窗口内安排检修。"
        )
    else:
        alert         = "OK"
        alert_message = (
            f"备件 ID={spare_part_id} 运行状态正常，"
            f"预测剩余寿命 {predicted_rul:.1f}h。"
        )

    # ------------------------------------------------------------------
    # Step 8: 返回结构化结果
    # ------------------------------------------------------------------
    return {
        "spare_part_id":       spare_part_id,
        "predicted_rul":       round(predicted_rul, 2),
        "confidence_interval": {
            "lower_95": round(ci_lower, 2),
            "upper_95": round(ci_upper, 2),
        },
        "alert":               alert,
        "alert_message":       alert_message,
        "top_features":        top_features,
        "data_quality":        data_quality,
        "n_records_used":      n_records,
        "timestamp":           timestamp,
    }


# =============================================================================
# Section 8: 主函数示例（模拟训练和预测演示）
# =============================================================================

def _simulate_sensor_data(spare_part_id: int, n: int = 150) -> pd.DataFrame:
    """
    生成一段模拟传感器数据用于演示，无需真实数据库。
    实际使用时此函数可删除，数据直接从 MySQL 读取。
    """
    np.random.seed(42)
    total_life = MODEL_CONFIG["simulated_total_life"]
    t          = np.linspace(0, total_life * 0.75, n)

    data = {
        "recorded_at":    pd.date_range(end=datetime.now(), periods=n, freq="h"),
        "operating_hours": t,
        "temperature":     75  + 0.005 * t + 3  * np.sin(0.01 * t) + np.random.normal(0, 2, n),
        "vibration":       2.0 + 0.002 * t + 0.3 * np.sin(0.02 * t) + np.random.normal(0, 0.1, n),
        "pressure":        1.0 + 0.001 * t                           + np.random.normal(0, 0.05, n),
        "current_load":    10  + 0.003 * t + 0.5 * np.sin(0.01 * t) + np.random.normal(0, 0.5, n),
        "rpm":             1500 - 0.1 * t                            + np.random.normal(0, 30, n),
        "error_code":      np.random.choice([0, 0, 0, 1], size=n),
    }
    return pd.DataFrame(data)


def main():
    """
    演示入口：模拟训练 → 预测 → 打印结果。
    实际部署时直接调用 predict_rul(spare_part_id) 即可。
    """
    logger.info("=" * 60)
    logger.info("备件管理系统 — 预测性维护模块演示")
    logger.info("=" * 60)

    # --- 演示 1：数据充足的正常预测 ---
    spare_part_id = 42
    logger.info(f"\n[演示 1] 备件 ID={spare_part_id}：正常数据量预测")

    # 用模拟数据替代数据库查询（仅演示用）
    demo_df = _simulate_sensor_data(spare_part_id, n=150)

    # 直接训练模型（跳过数据库步骤）
    try:
        model, scaler = train_model(spare_part_id, demo_df)

        # 构造预测窗口
        X_scaled, _ = preprocess_data(demo_df, scaler=scaler)
        seq_len      = MODEL_CONFIG["sequence_length"]
        X_window     = X_scaled[-seq_len:].reshape(1, seq_len, -1).astype(np.float32)

        rul, ci_lo, ci_hi = monte_carlo_predict(model, X_window)

        features   = MODEL_CONFIG["features"]
        y_dummy    = np.zeros(len(X_scaled))
        X_all, _   = create_sequences(X_scaled, y_dummy, seq_len)
        background = X_all[:100]
        shap_info  = get_shap_explanation(model, X_window, background, features)

        result = {
            "spare_part_id":       spare_part_id,
            "predicted_rul_hours": round(rul, 2),
            "confidence_interval": {"lower_95": round(ci_lo, 2), "upper_95": round(ci_hi, 2)},
            "alert":               "CRITICAL" if rul < MODEL_CONFIG["rul_threshold"] else "OK",
            "top_features":        shap_info,
        }

        print("\n预测结果（演示模式）：")
        print(json.dumps(result, ensure_ascii=False, indent=2))

    except Exception as exc:
        logger.error(f"演示失败：{exc}")

    # --- 演示 2：数据极少场景 ---
    logger.info(f"\n[演示 2] 备件 ID=99：数据极少场景")
    sparse_df = _simulate_sensor_data(99, n=5)
    try:
        model2, scaler2 = train_model(99, sparse_df)
    except ValueError as exc:
        logger.warning(f"预期异常（无预训练模型）：{exc}")

    # --- 演示 3：完整 predict_rul 接口（连接真实数据库）---
    # 注意：需要先在 DB_CONFIG 中填写正确的数据库密码，并确保数据库运行中。
    # 取消下方注释后可真实运行：
    #
    # logger.info("\n[演示 3] 调用完整 predict_rul 接口（需真实数据库）")
    # result = predict_rul(spare_part_id=1)
    # print(json.dumps(result, ensure_ascii=False, indent=2))

    logger.info("\n演示完成。生产环境中请直接调用 predict_rul(spare_part_id)。")


if __name__ == "__main__":
    main()
