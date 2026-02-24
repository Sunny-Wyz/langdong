"""
smart_replenishment.py
======================
备件管理系统 — AI 智能补货建议模块

功能概述：
  - 基于 LSTM 时间序列模型预测未来 M 个月的备件需求量
  - 综合预测需求、当前库存、安全库存，生成采购量建议
  - 基于供应商绩效（质量 + 价格 + 及时率）自动选择最优供应商
  - 使用 SHAP 解释预测依据，使用 MC Dropout 量化置信区间
  - 预测需求 > 当前库存 × 1.5 时触发高优先级补货预警
  - 历史数据不足 6 个月时自动降级为统计平均法

集成方式：
  from smart_replenishment import suggest_replenishment
  results = suggest_replenishment(spare_part_ids=[1, 2, 3])

依赖安装：
  pip install tensorflow scikit-learn pandas numpy pymysql sqlalchemy shap

代码复用：
  本模块复用 predictive_maintenance.py 中的 DB_CONFIG 和 get_db_engine()。
  若 predictive_maintenance 模块不存在，会本地定义这些组件，保证独立可运行。
"""

# =============================================================================
# Section 1: 导入库
# =============================================================================
import os
import json
import math
import pickle
import logging
import warnings
from datetime import datetime, timedelta, date
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
from sklearn.model_selection import train_test_split

# TensorFlow / Keras
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers, callbacks, models

# 数据库
import pymysql
from sqlalchemy import create_engine, text

# SHAP 可解释性（懒加载）
try:
    import shap
    SHAP_AVAILABLE = True
except ImportError:
    SHAP_AVAILABLE = False

warnings.filterwarnings("ignore")


# =============================================================================
# Section 2: 复用 predictive_maintenance 模块的公共组件
# =============================================================================
# 优先从预测性维护模块导入数据库配置，保持单一数据源；
# 若模块不存在，在本地定义完整副本，确保独立运行。

try:
    from predictive_maintenance import DB_CONFIG, get_db_engine
    _PM_AVAILABLE = True
    logging.getLogger("smart_replenishment").info(
        "已从 predictive_maintenance 复用 DB_CONFIG 和 get_db_engine。"
    )
except ImportError:
    _PM_AVAILABLE = False

    DB_CONFIG: Dict[str, Any] = {
        "host":     "localhost",
        "port":     3306,
        "user":     "root",
        "password": "your_password",  # ← 生产环境建议: os.environ["DB_PASSWORD"]
        "database": "spare_db",
        "charset":  "utf8mb4",
    }

    def get_db_engine():
        """创建 SQLAlchemy 数据库引擎（本地独立定义）。"""
        cfg = DB_CONFIG
        url = (
            f"mysql+pymysql://{cfg['user']}:{cfg['password']}"
            f"@{cfg['host']}:{cfg['port']}/{cfg['database']}"
            f"?charset={cfg['charset']}"
        )
        return create_engine(url, pool_pre_ping=True, echo=False)


# =============================================================================
# Section 3: 全局配置
# =============================================================================

DEMAND_CONFIG: Dict[str, Any] = {
    # ----- 时间参数 -----
    "lookback_months":    12,           # 输入窗口：过去 12 个月
    "predict_months":     3,            # 输出：预测未来 3 个月
    "min_months_lstm":    6,            # LSTM 训练最少需要 6 个月数据
    "min_months_stats":   1,            # 统计平均法最少需要 1 个月数据

    # ----- 输入特征（每月一条记录的列名） -----
    "features": [
        "outbound_qty",                 # 月出库数量（核心特征）
        "repair_count",                 # 月维修工单数
        "avg_unit_price",               # 当月采购均价
        "month_sin",                    # 月份正弦编码（捕捉季节性）
        "month_cos",                    # 月份余弦编码
        "working_days",                 # 当月工作日天数
    ],

    # ----- LSTM 架构（比 RUL 模型更小，适配稀疏月度数据） -----
    "lstm_units_1":    64,
    "lstm_units_2":    32,
    "dense_units":     16,
    "dropout_rate":    0.2,

    # ----- 训练参数 -----
    "batch_size":      16,
    "epochs":          80,
    "learning_rate":   0.001,
    "validation_split": 0.15,
    "early_stopping_patience": 15,

    # ----- 推断参数 -----
    "mc_samples":      50,              # Monte Carlo Dropout 采样次数

    # ----- 业务参数 -----
    "safety_factor":   1.5,             # 安全库存系数
    "alert_ratio":     1.5,             # 预警触发：预测需求 > 库存 × 此系数
    "default_lead_time_days": 14,       # 默认供应商交货期（天）
    "default_working_days":   22,       # 默认每月工作日

    # ----- 供应商绩效权重 -----
    "supplier_weights": {
        "quality":  0.4,                # 质量权重
        "on_time":  0.3,                # 及时率权重
        "price":    0.3,                # 价格竞争力权重
    },

    # ----- 文件路径 -----
    "model_path":      "demand_model.h5",
    "scaler_path":     "demand_scaler.pkl",
}

# 特征中文描述（SHAP 解释用）
FEATURE_DESCRIPTIONS: Dict[str, str] = {
    "outbound_qty":   "月出库数量",
    "repair_count":   "月维修次数",
    "avg_unit_price": "采购均价",
    "month_sin":      "季节因素（周期项）",
    "month_cos":      "季节因素（相位项）",
    "working_days":   "工作日天数",
}


# =============================================================================
# Section 4: 日志配置
# =============================================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("smart_replenishment")


# =============================================================================
# Section 5: 数据库建表与数据加载
# =============================================================================

_CREATE_CONSUMPTION_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `spare_part_consumption_log` (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    spare_part_id   BIGINT         NOT NULL                COMMENT '关联备件 ID',
    record_month    DATE           NOT NULL                COMMENT '记录月份（YYYY-MM-01）',
    outbound_qty    INT            DEFAULT 0               COMMENT '当月出库数量',
    repair_count    INT            DEFAULT 0               COMMENT '当月维修工单数',
    avg_unit_price  DECIMAL(10,2)  DEFAULT NULL            COMMENT '当月采购均价',
    working_days    INT            DEFAULT 22              COMMENT '当月工作日天数',
    PRIMARY KEY (id),
    UNIQUE KEY uk_part_month (spare_part_id, record_month),
    KEY idx_spare_part_id (spare_part_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件月度消耗日志';
"""

_CREATE_SUPPLIER_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `supplier_performance` (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    supplier_name   VARCHAR(200)   NOT NULL                COMMENT '供应商名称',
    spare_part_id   BIGINT         DEFAULT NULL            COMMENT '关联备件（NULL=通用评分）',
    quality_score   FLOAT          DEFAULT 0.80            COMMENT '质量合格率 (0~1)',
    price_score     FLOAT          DEFAULT 0.80            COMMENT '价格竞争力 (0~1)',
    on_time_rate    FLOAT          DEFAULT 0.90            COMMENT '按时交付率 (0~1)',
    lead_time_days  INT            DEFAULT 14              COMMENT '平均交货期（天）',
    last_evaluated  DATE           DEFAULT NULL            COMMENT '最近一次评估日期',
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_part (supplier_name, spare_part_id),
    KEY idx_supplier (supplier_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商绩效评分表';
"""


def ensure_tables_exist(engine) -> None:
    """
    检查并创建消耗日志表和供应商绩效表（幂等操作）。
    """
    with engine.connect() as conn:
        conn.execute(text(_CREATE_CONSUMPTION_TABLE_SQL))
        conn.execute(text(_CREATE_SUPPLIER_TABLE_SQL))
        conn.commit()
    logger.info("补货模块数据表已就绪（spare_part_consumption_log, supplier_performance）。")


def load_spare_part_info(spare_part_id: int, engine) -> Optional[Dict[str, Any]]:
    """
    从 spare_part 表加载备件基本信息（名称、当前库存、价格、供应商）。

    返回：
        字典 {'id', 'name', 'quantity', 'price', 'supplier', 'category'}，
        备件不存在时返回 None。
    """
    sql = text("""
        SELECT id, name, quantity, price, supplier, category
        FROM spare_part WHERE id = :sid
    """)
    with engine.connect() as conn:
        row = conn.execute(sql, {"sid": spare_part_id}).mappings().fetchone()
    if row is None:
        return None
    return dict(row)


def load_consumption_data(spare_part_id: int, engine) -> pd.DataFrame:
    """
    加载指定备件的月度消耗历史，按月份升序排列。

    返回：
        DataFrame，列包含 record_month, outbound_qty, repair_count, avg_unit_price, working_days。
    """
    sql = text("""
        SELECT record_month, outbound_qty, repair_count, avg_unit_price, working_days
        FROM   spare_part_consumption_log
        WHERE  spare_part_id = :sid
        ORDER  BY record_month ASC
    """)
    with engine.connect() as conn:
        df = pd.read_sql(sql, conn, params={"sid": spare_part_id})
    logger.info(f"备件 ID={spare_part_id}：加载 {len(df)} 个月消耗记录。")
    return df


def load_supplier_performance(spare_part_id: int, engine) -> pd.DataFrame:
    """
    加载与指定备件相关的供应商绩效数据。
    先查询针对该备件的专属评分，再查询通用评分，合并后去重。
    """
    sql = text("""
        SELECT supplier_name, quality_score, price_score, on_time_rate, lead_time_days
        FROM   supplier_performance
        WHERE  spare_part_id = :sid OR spare_part_id IS NULL
        ORDER  BY spare_part_id DESC
    """)
    with engine.connect() as conn:
        df = pd.read_sql(sql, conn, params={"sid": spare_part_id})
    # 同一供应商有专属评分时优先使用（spare_part_id DESC 排序保证专属在前）
    df.drop_duplicates(subset=["supplier_name"], keep="first", inplace=True)
    return df


# =============================================================================
# Section 6: 数据预处理与特征工程
# =============================================================================

def add_cyclic_month_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    为月度数据添加周期性月份编码（正弦 + 余弦），捕捉季节性规律。

    12月和1月在特征空间中是相邻的（解决 12→1 跳变问题）：
        month_sin = sin(2π × month / 12)
        month_cos = cos(2π × month / 12)
    """
    if "record_month" in df.columns:
        months = pd.to_datetime(df["record_month"]).dt.month
    else:
        months = pd.Series(range(1, len(df) + 1)) % 12 + 1

    df["month_sin"] = np.sin(2 * np.pi * months / 12.0)
    df["month_cos"] = np.cos(2 * np.pi * months / 12.0)
    return df


def aggregate_monthly_data(
    df: pd.DataFrame,
    spare_part_id: int,
) -> pd.DataFrame:
    """
    数据预处理管线：缺失值填充 → 类型转换 → 月份编码。

    参数：
        df:             原始消耗日志 DataFrame
        spare_part_id:  备件 ID（用于日志）

    返回：
        包含所有特征列的预处理后 DataFrame。
    """
    if df.empty:
        return df

    df = df.copy()

    # 缺失值填充
    df["outbound_qty"]   = df["outbound_qty"].fillna(0).astype(float)
    df["repair_count"]   = df["repair_count"].fillna(0).astype(float)
    df["avg_unit_price"] = df["avg_unit_price"].fillna(df["avg_unit_price"].median()).astype(float)
    df["working_days"]   = df["working_days"].fillna(DEMAND_CONFIG["default_working_days"]).astype(float)

    # 添加月份周期编码
    df = add_cyclic_month_features(df)

    logger.info(f"备件 ID={spare_part_id}：预处理完成，{len(df)} 条月度记录。")
    return df


def generate_synthetic_monthly_data(
    existing_df: pd.DataFrame,
    target_months: int = 24,
) -> pd.DataFrame:
    """
    当真实月度数据不足时，基于统计特性生成合成消耗序列以补充训练集。

    生成模型：
        demand(t) = baseline
                  + trend * t
                  + amplitude * sin(2π * month / 12 + phase)
                  + N(0, σ²)

    参数：
        existing_df:   已有的真实月度数据
        target_months: 目标总月数

    返回：
        合并后的 DataFrame（真实 + 合成），按时间升序。
    """
    n_existing  = len(existing_df)
    n_synthetic = max(0, target_months - n_existing)
    if n_synthetic == 0:
        return existing_df

    logger.warning(f"月度数据不足，生成 {n_synthetic} 条合成记录补充。")

    # 从现有数据估计统计特性
    if n_existing > 0:
        avg_out = existing_df["outbound_qty"].mean()
        std_out = max(existing_df["outbound_qty"].std(), 1.0)
        avg_rep = existing_df["repair_count"].mean()
        avg_prc = existing_df["avg_unit_price"].median()
    else:
        avg_out, std_out = 10.0, 3.0
        avg_rep = 2.0
        avg_prc = 100.0

    # 起始月份
    if n_existing > 0 and "record_month" in existing_df.columns:
        last_month = pd.to_datetime(existing_df["record_month"].iloc[-1])
        start_month = last_month + pd.DateOffset(months=1)
    else:
        start_month = pd.Timestamp("2024-01-01")

    rows = []
    for i in range(n_synthetic):
        cur_month = start_month + pd.DateOffset(months=i)
        month_num = cur_month.month

        # 需求 = 基线 + 趋势 + 季节性 + 噪声
        seasonal = std_out * 0.5 * np.sin(2 * np.pi * month_num / 12.0)
        trend    = 0.02 * i * std_out  # 微弱上升趋势
        noise    = np.random.normal(0, std_out * 0.3)
        demand   = max(0, avg_out + trend + seasonal + noise)

        rows.append({
            "record_month":   cur_month.strftime("%Y-%m-01"),
            "outbound_qty":   round(demand),
            "repair_count":   max(0, round(avg_rep + np.random.normal(0, 1))),
            "avg_unit_price": round(avg_prc * (1 + np.random.normal(0, 0.05)), 2),
            "working_days":   np.random.choice([20, 21, 22, 23]),
        })

    synthetic_df = pd.DataFrame(rows)
    combined = pd.concat([existing_df, synthetic_df], ignore_index=True)
    combined.sort_values("record_month", inplace=True)
    combined.reset_index(drop=True, inplace=True)
    return combined


def preprocess_features(
    df: pd.DataFrame,
    scaler: Optional[MinMaxScaler] = None,
) -> Tuple[np.ndarray, MinMaxScaler]:
    """
    特征归一化：Min-Max 缩放到 [0, 1]。

    复用 predictive_maintenance.py 中的 preprocess_data 模式：
    训练时 scaler=None（fit_transform），推断时传入已有 scaler（transform）。

    参数：
        df:     含特征列的 DataFrame
        scaler: 已有的 MinMaxScaler（推断时传入）

    返回：
        (归一化后的 ndarray, scaler)
    """
    features = DEMAND_CONFIG["features"]
    X = df[features].copy()
    X.ffill(inplace=True)
    X.fillna(0, inplace=True)

    if scaler is None:
        scaler = MinMaxScaler(feature_range=(0, 1))
        X_scaled = scaler.fit_transform(X.values)
    else:
        X_scaled = scaler.transform(X.values)

    return X_scaled.astype(np.float32), scaler


def create_demand_sequences(
    X: np.ndarray,
    y_col: np.ndarray,
    lookback: int,
    predict_months: int,
) -> Tuple[np.ndarray, np.ndarray]:
    """
    构建需求预测的滑动窗口序列。

    与 RUL 版本的 create_sequences 的区别：
      - RUL：y 是标量（窗口末尾的 RUL）
      - 需求：y 是向量（窗口之后 predict_months 个月的需求量）

    参数：
        X:               归一化后的特征数组 (n_months, n_features)
        y_col:           原始出库量序列 (n_months,)
        lookback:        输入窗口大小
        predict_months:  输出步长

    返回：
        (X_seq, y_seq)
        X_seq 形状: (n_windows, lookback, n_features)
        y_seq 形状: (n_windows, predict_months)
    """
    X_seq, y_seq = [], []
    for i in range(len(X) - lookback - predict_months + 1):
        X_seq.append(X[i : i + lookback])
        y_seq.append(y_col[i + lookback : i + lookback + predict_months])
    return np.array(X_seq, dtype=np.float32), np.array(y_seq, dtype=np.float32)


# =============================================================================
# Section 7: 模型构建、训练、保存与加载
# =============================================================================

def build_demand_model(input_shape: Tuple[int, int], output_steps: int) -> keras.Model:
    """
    构建需求预测 LSTM 模型（多步输出）。

    架构（比 RUL 模型更轻量，适配月度稀疏数据）：
        LSTM(64) → Dropout → LSTM(32) → Dropout → Dense(16) → Dense(output_steps)

    参数：
        input_shape:  (lookback_months, n_features)
        output_steps: 预测步数（未来 M 个月）

    返回：
        编译好的 Keras 模型
    """
    cfg = DEMAND_CONFIG

    model = keras.Sequential(
        [
            # 第一层 LSTM：保留全序列，学习月度变化趋势
            layers.LSTM(
                cfg["lstm_units_1"],
                return_sequences=True,
                input_shape=input_shape,
                name="lstm_demand_1",
            ),
            layers.Dropout(cfg["dropout_rate"], name="dropout_demand_1"),

            # 第二层 LSTM：聚合为固定向量
            layers.LSTM(
                cfg["lstm_units_2"],
                return_sequences=False,
                name="lstm_demand_2",
            ),
            layers.Dropout(cfg["dropout_rate"], name="dropout_demand_2"),

            # 全连接映射
            layers.Dense(cfg["dense_units"], activation="relu", name="dense_demand_1"),

            # 多步输出：预测未来 M 个月各月需求量
            layers.Dense(output_steps, activation="relu", name="output_demand"),
            # relu 激活保证需求量非负
        ],
        name="Demand_LSTM",
    )

    optimizer = keras.optimizers.Adam(learning_rate=cfg["learning_rate"])
    model.compile(optimizer=optimizer, loss="huber", metrics=["mae"])

    logger.info(
        f"需求预测 LSTM 构建完成 — 参数量: {model.count_params():,}，"
        f"输入: {input_shape}，输出步: {output_steps}"
    )
    return model


def save_demand_model(model: keras.Model, scaler: MinMaxScaler) -> None:
    """
    保存需求预测模型和标准化器。
    复用 predictive_maintenance.py 的保存模式，使用不同文件名。
    """
    model_path  = DEMAND_CONFIG["model_path"]
    scaler_path = DEMAND_CONFIG["scaler_path"]

    model.save(model_path)
    with open(scaler_path, "wb") as f:
        pickle.dump(scaler, f)
    logger.info(f"需求模型已保存至 {model_path}，Scaler 已保存至 {scaler_path}。")


def load_demand_model() -> Tuple[Optional[keras.Model], Optional[MinMaxScaler]]:
    """
    加载已保存的需求预测模型和标准化器。
    复用 predictive_maintenance.py 的加载模式。
    """
    model_path  = DEMAND_CONFIG["model_path"]
    scaler_path = DEMAND_CONFIG["scaler_path"]

    if not (os.path.exists(model_path) and os.path.exists(scaler_path)):
        logger.warning("未找到预训练需求模型。")
        return None, None

    try:
        model = models.load_model(model_path)
        with open(scaler_path, "rb") as f:
            scaler = pickle.load(f)
        logger.info(f"需求模型已从 {model_path} 加载。")
        return model, scaler
    except Exception as exc:
        logger.error(f"需求模型加载失败：{exc}")
        return None, None


def train_demand_model(
    spare_part_id: int,
    df: pd.DataFrame,
    retrain: bool = False,
) -> Tuple[keras.Model, MinMaxScaler]:
    """
    训练（或加载）需求预测模型。

    降级策略（复用 predictive_maintenance.py 的分层思路）：
      - 已有模型且 retrain=False → 直接加载
      - 数据 ≥ 6 个月 → 正常训练 LSTM
      - 数据 3~5 个月 → 合成数据补充至 24 个月再训练
      - 数据 < 3 个月 → 加载预训练模型；若无则抛出异常

    参数：
        spare_part_id: 备件 ID
        df:            预处理后的月度消耗 DataFrame
        retrain:       是否强制重训练

    返回：
        (model, scaler)
    """
    cfg       = DEMAND_CONFIG
    lookback  = cfg["lookback_months"]
    pred_m    = cfg["predict_months"]
    min_lstm  = cfg["min_months_lstm"]

    # --- 尝试加载已有模型 ---
    if not retrain:
        model, scaler = load_demand_model()
        if model is not None:
            return model, scaler

    n_months = len(df)
    logger.info(f"备件 ID={spare_part_id}：可用月度数据 {n_months} 个月。")

    # --- 数据极少（< 3个月）：尝试预训练模型 ---
    if n_months < 3:
        model, scaler = load_demand_model()
        if model is not None:
            logger.warning(f"数据极少（{n_months}个月），使用通用预训练需求模型。")
            return model, scaler
        raise ValueError(
            f"备件 ID={spare_part_id} 仅有 {n_months} 个月数据（<3），"
            "且无预训练需求模型可用。请积累更多消耗数据。"
        )

    # --- 数据不足（< 6个月）：合成数据补充 ---
    if n_months < min_lstm:
        logger.warning(f"数据不足 {min_lstm} 个月，使用合成数据增强。")
        df = generate_synthetic_monthly_data(df, target_months=24)
        df = add_cyclic_month_features(df)

    # --- 特征归一化 ---
    X_scaled, scaler = preprocess_features(df)
    y_raw = df["outbound_qty"].values.astype(np.float32)

    # --- 构建滑动窗口序列 ---
    X_seq, y_seq = create_demand_sequences(X_scaled, y_raw, lookback, pred_m)

    if len(X_seq) < 5:
        raise ValueError(f"有效训练窗口数（{len(X_seq)}）过少，无法训练。")

    # --- 训练/验证划分（时序数据不打乱） ---
    split_idx = max(1, int(len(X_seq) * (1 - cfg["validation_split"])))
    X_train, X_val = X_seq[:split_idx], X_seq[split_idx:]
    y_train, y_val = y_seq[:split_idx], y_seq[split_idx:]

    # --- 构建并训练 ---
    input_shape = (lookback, len(cfg["features"]))
    model = build_demand_model(input_shape, pred_m)

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
            patience=7,
            min_lr=1e-6,
            verbose=0,
        ),
    ]

    logger.info("开始训练需求预测 LSTM……")
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
    logger.info(f"训练完成：最佳验证损失={best_val_loss:.2f}，最佳 MAE={best_val_mae:.2f}")

    save_demand_model(model, scaler)
    return model, scaler


# =============================================================================
# Section 8: 预测函数
# =============================================================================

def _mc_dropout_predict(
    model: keras.Model,
    X_input: np.ndarray,
    n_samples: int = 50,
) -> Tuple[np.ndarray, np.ndarray]:
    """
    Monte Carlo Dropout 多步预测（复用 predictive_maintenance.py 的 MC 模式，适配多输出）。

    RUL 版本返回标量；需求版本返回 M 维向量（每个月的预测需求）。

    参数：
        model:     Keras LSTM 模型
        X_input:   输入序列 (1, lookback, n_features)
        n_samples: MC 采样次数

    返回：
        (mean_per_month, std_per_month)，形状均为 (predict_months,)
    """
    preds = np.array(
        [model(X_input, training=True).numpy().flatten() for _ in range(n_samples)]
    )
    # preds 形状: (n_samples, predict_months)
    mean_pred = preds.mean(axis=0)
    std_pred  = preds.std(axis=0)
    return np.maximum(mean_pred, 0), std_pred


def _statistical_predict(
    df: pd.DataFrame,
    predict_months: int,
) -> Tuple[np.ndarray, np.ndarray]:
    """
    统计降级预测：当数据量不足以训练 LSTM 时，使用均值 ± 标准差预测。

    参数：
        df:              月度消耗数据
        predict_months:  预测月数

    返回：
        (mean_per_month, std_per_month)
    """
    avg = df["outbound_qty"].mean()
    std = max(df["outbound_qty"].std(), avg * 0.2)  # 至少 20% 不确定性
    mean_pred = np.full(predict_months, avg)
    std_pred  = np.full(predict_months, std)
    return mean_pred, std_pred


def predict_demand(
    spare_part_id: int,
    df: pd.DataFrame,
    model: Optional[keras.Model] = None,
    scaler: Optional[MinMaxScaler] = None,
) -> Dict[str, Any]:
    """
    预测指定备件未来 M 个月的需求量。

    自动选择预测方法：
      - 数据充足 + 模型可用 → LSTM + MC Dropout
      - 数据不足 → 统计平均法

    参数：
        spare_part_id: 备件 ID
        df:            预处理后的月度消耗数据
        model:         已训练/加载的 Keras 模型（可选）
        scaler:        对应的 MinMaxScaler（可选）

    返回：
        {
            "method":            "lstm" | "statistical" | "default",
            "monthly_demand":    [m1, m2, m3],
            "total_demand":      float,
            "confidence_interval": {"lower": [...], "upper": [...]},
            "daily_avg":         float,
        }
    """
    cfg    = DEMAND_CONFIG
    pred_m = cfg["predict_months"]

    # --- 零数据：保守默认 ---
    if len(df) == 0:
        default_demand = np.full(pred_m, 5.0)  # 保守估计每月 5 件
        return {
            "method":              "default",
            "monthly_demand":      default_demand.tolist(),
            "total_demand":        float(default_demand.sum()),
            "confidence_interval": {
                "lower": [0.0] * pred_m,
                "upper": [20.0] * pred_m,
            },
            "daily_avg":           round(5.0 / cfg["default_working_days"], 2),
        }

    # --- 数据不足 LSTM：统计降级 ---
    if len(df) < cfg["min_months_lstm"] or model is None:
        mean_pred, std_pred = _statistical_predict(df, pred_m)
        daily_avg = float(mean_pred.mean()) / cfg["default_working_days"]
        return {
            "method":              "statistical",
            "monthly_demand":      np.round(mean_pred, 1).tolist(),
            "total_demand":        round(float(mean_pred.sum()), 1),
            "confidence_interval": {
                "lower": np.maximum(0, mean_pred - 1.96 * std_pred).round(1).tolist(),
                "upper": (mean_pred + 1.96 * std_pred).round(1).tolist(),
            },
            "daily_avg":           round(daily_avg, 2),
        }

    # --- LSTM 预测 ---
    lookback = cfg["lookback_months"]

    # 若月度数据不足窗口大小，在头部用零填充
    if len(df) < lookback:
        pad_n = lookback - len(df)
        features = cfg["features"]
        pad_df = pd.DataFrame(np.zeros((pad_n, len(features))), columns=features)
        for col in df.columns:
            if col not in pad_df.columns and col != "record_month":
                pad_df[col] = 0
        df = pd.concat([pad_df, df], ignore_index=True)

    X_scaled, _ = preprocess_features(df, scaler=scaler)
    X_window = X_scaled[-lookback:].reshape(1, lookback, -1).astype(np.float32)

    mean_pred, std_pred = _mc_dropout_predict(model, X_window, n_samples=cfg["mc_samples"])
    daily_avg = float(mean_pred.mean()) / cfg["default_working_days"]

    return {
        "method":              "lstm",
        "monthly_demand":      np.round(mean_pred, 1).tolist(),
        "total_demand":        round(float(mean_pred.sum()), 1),
        "confidence_interval": {
            "lower": np.maximum(0, mean_pred - 1.96 * std_pred).round(1).tolist(),
            "upper": (mean_pred + 1.96 * std_pred).round(1).tolist(),
        },
        "daily_avg":           round(daily_avg, 2),
    }


# =============================================================================
# Section 9: SHAP 解释（复用 predictive_maintenance.py 的 KernelExplainer 模式）
# =============================================================================

def get_demand_shap_explanation(
    model: keras.Model,
    X_window: np.ndarray,
    background: np.ndarray,
    feature_names: List[str],
) -> Dict[str, Any]:
    """
    使用 SHAP 解释需求预测的关键驱动因素。

    复用 predictive_maintenance.py 的 SHAP 策略（KernelExplainer + K-means 背景），
    适配需求场景的特征名和中文描述。

    参数：
        model:          Keras 需求模型
        X_window:       待解释输入 (1, lookback, n_features)
        background:     背景数据 (N, lookback, n_features)
        feature_names:  特征名列表

    返回：
        Top-3 特征重要性字典
    """
    if not SHAP_AVAILABLE:
        return {"info": "SHAP 未安装（pip install shap），跳过特征解释。"}

    seq_len, n_features = X_window.shape[1], X_window.shape[2]
    X_flat  = X_window.reshape(1, -1)
    bg_flat = background.reshape(background.shape[0], -1)[:200]

    def model_predict_total(X_2d: np.ndarray) -> np.ndarray:
        """预测总需求量（M 个月之和），用于单输出 SHAP。"""
        X_3d = X_2d.reshape(-1, seq_len, n_features)
        preds = model(X_3d, training=False).numpy()
        return preds.sum(axis=1)  # 求和为标量

    try:
        bg_summary = shap.kmeans(bg_flat, min(30, len(bg_flat)))
        explainer  = shap.KernelExplainer(model_predict_total, bg_summary)
        shap_vals  = explainer.shap_values(X_flat, nsamples=150, silent=True)

        shap_matrix    = np.array(shap_vals).reshape(seq_len, n_features)
        importance_abs = np.abs(shap_matrix).mean(axis=0)
        importance_dir = shap_matrix[-1]

        top_indices = np.argsort(importance_abs)[::-1][:3]
        explanation: Dict[str, Any] = {}
        for rank, idx in enumerate(top_indices):
            fname  = feature_names[idx]
            imp    = float(importance_abs[idx])
            direction = "推高需求" if importance_dir[idx] > 0 else "降低需求"
            explanation[f"rank_{rank + 1}"] = {
                "feature":     fname,
                "description": FEATURE_DESCRIPTIONS.get(fname, fname),
                "importance":  round(imp, 6),
                "direction":   direction,
            }
        return explanation

    except Exception as exc:
        logger.error(f"需求 SHAP 解释失败：{exc}")
        return {"error": str(exc)}


# =============================================================================
# Section 10: 供应商选择
# =============================================================================

def select_best_supplier(
    spare_part_id: int,
    current_supplier: Optional[str],
    engine,
) -> Dict[str, Any]:
    """
    基于绩效综合评分选择最优供应商。

    评分公式：
        score = 0.4 × quality_score + 0.3 × on_time_rate + 0.3 × price_score

    参数：
        spare_part_id:    备件 ID
        current_supplier: 当前备件登记的供应商名称
        engine:           SQLAlchemy 引擎

    返回：
        {
            "name":           供应商名称,
            "score":          综合评分,
            "quality_score":  质量分,
            "on_time_rate":   及时率,
            "price_score":    价格分,
            "lead_time_days": 交货期,
            "reason":         选择原因
        }
    """
    w = DEMAND_CONFIG["supplier_weights"]

    perf_df = load_supplier_performance(spare_part_id, engine)

    if perf_df.empty:
        # 无绩效数据：回退到当前供应商
        return {
            "name":           current_supplier or "未知",
            "score":          None,
            "quality_score":  None,
            "on_time_rate":   None,
            "price_score":    None,
            "lead_time_days": DEMAND_CONFIG["default_lead_time_days"],
            "reason":         "无供应商绩效数据，使用当前登记供应商。",
        }

    # 计算综合评分
    perf_df["composite_score"] = (
        w["quality"] * perf_df["quality_score"]
        + w["on_time"] * perf_df["on_time_rate"]
        + w["price"]   * perf_df["price_score"]
    )

    # 选择最高分
    best = perf_df.loc[perf_df["composite_score"].idxmax()]

    reason_parts = []
    if best["quality_score"] >= 0.9:
        reason_parts.append(f"质量优秀（{best['quality_score']:.0%}）")
    if best["on_time_rate"] >= 0.9:
        reason_parts.append(f"交付及时（{best['on_time_rate']:.0%}）")
    if best["price_score"] >= 0.8:
        reason_parts.append(f"价格有竞争力（{best['price_score']:.0%}）")
    reason = "综合评分最高" + ("：" + "、".join(reason_parts) if reason_parts else "")

    return {
        "name":           best["supplier_name"],
        "score":          round(float(best["composite_score"]), 3),
        "quality_score":  round(float(best["quality_score"]), 3),
        "on_time_rate":   round(float(best["on_time_rate"]), 3),
        "price_score":    round(float(best["price_score"]), 3),
        "lead_time_days": int(best["lead_time_days"]),
        "reason":         reason,
    }


# =============================================================================
# Section 11: 核心接口 — suggest_replenishment
# =============================================================================

def suggest_replenishment(
    spare_part_ids: List[int],
) -> List[Dict[str, Any]]:
    """
    批量生成智能补货建议。

    对每个备件：
      1. 加载基本信息（名称、当前库存、供应商）
      2. 加载月度消耗历史 → 预测未来 M 个月需求
      3. 计算建议采购量 = 预测需求 - 当前库存 + 安全库存
      4. 计算建议采购时间 = 今天 + (库存 / 日均消耗) - 交货期
      5. 选择最优供应商（绩效评分最高者）
      6. SHAP 解释预测依据
      7. 判断是否触发高优先级预警

    参数：
        spare_part_ids: 备件 ID 列表

    返回：
        JSON 格式建议列表，每个元素包含：
        - spare_part_id, spare_part_name
        - predicted_demand（月度明细 + 总计）
        - suggested_qty, suggested_date
        - supplier（最优供应商及其评分）
        - priority（LOW / MEDIUM / HIGH）
        - alert_message, explanation（SHAP Top-3）
        - data_quality, prediction_method
    """
    cfg       = DEMAND_CONFIG
    pred_m    = cfg["predict_months"]
    threshold = cfg["alert_ratio"]
    timestamp = datetime.now().isoformat()

    # --- 连接数据库 ---
    try:
        engine = get_db_engine()
        ensure_tables_exist(engine)
    except Exception as exc:
        logger.error(f"数据库连接失败：{exc}")
        return [{
            "spare_part_id": sid,
            "error":         f"数据库连接失败：{exc}",
            "timestamp":     timestamp,
        } for sid in spare_part_ids]

    results = []

    for sid in spare_part_ids:
        logger.info(f"{'='*50}")
        logger.info(f"处理备件 ID={sid}")

        # ----------------------------------------------------------
        # Step 1: 加载备件基本信息
        # ----------------------------------------------------------
        part_info = load_spare_part_info(sid, engine)
        if part_info is None:
            results.append({
                "spare_part_id":   sid,
                "spare_part_name": None,
                "error":           f"备件 ID={sid} 不存在于 spare_part 表。",
                "timestamp":       timestamp,
            })
            continue

        current_stock = part_info.get("quantity", 0) or 0
        part_name     = part_info.get("name", f"备件#{sid}")
        part_supplier = part_info.get("supplier")

        # ----------------------------------------------------------
        # Step 2: 加载月度消耗历史并预处理
        # ----------------------------------------------------------
        raw_df   = load_consumption_data(sid, engine)
        n_months = len(raw_df)
        proc_df  = aggregate_monthly_data(raw_df, sid)

        data_quality = "sufficient"
        if n_months == 0:
            data_quality = "no_data"
        elif n_months < 3:
            data_quality = "critical_low"
        elif n_months < cfg["min_months_lstm"]:
            data_quality = "insufficient_augmented"

        # ----------------------------------------------------------
        # Step 3: 训练/加载模型 → 预测需求
        # ----------------------------------------------------------
        model, scaler = None, None
        if n_months >= 3:
            try:
                model, scaler = train_demand_model(sid, proc_df)
            except ValueError as exc:
                logger.warning(f"备件 ID={sid} 模型训练失败：{exc}")

        demand_result = predict_demand(sid, proc_df, model, scaler)

        total_demand   = demand_result["total_demand"]
        daily_avg      = demand_result["daily_avg"]
        pred_method    = demand_result["method"]

        # ----------------------------------------------------------
        # Step 4: 选择最优供应商
        # ----------------------------------------------------------
        supplier_info = select_best_supplier(sid, part_supplier, engine)
        lead_time     = supplier_info["lead_time_days"]

        # ----------------------------------------------------------
        # Step 5: 计算补货建议
        # ----------------------------------------------------------
        # 安全库存 = 日均消耗 × 交货期 × 安全系数
        if daily_avg > 0:
            safety_stock = daily_avg * lead_time * cfg["safety_factor"]
        else:
            safety_stock = 0.0

        # 建议采购量 = 预测需求 - 当前库存 + 安全库存
        suggested_qty = max(0, math.ceil(total_demand - current_stock + safety_stock))

        # 建议采购日期 = 今天 + (库存够用天数) - 交货期
        if daily_avg > 0:
            days_until_stockout = current_stock / daily_avg
            order_in_days       = days_until_stockout - lead_time
            if order_in_days < 0:
                suggested_date = date.today().isoformat()
                date_note      = "（库存已低于交货期覆盖量，建议立即采购）"
            else:
                suggested_date = (date.today() + timedelta(days=int(order_in_days))).isoformat()
                date_note      = ""
        else:
            suggested_date = "无法计算（无消耗记录）"
            date_note      = ""

        # ----------------------------------------------------------
        # Step 6: SHAP 解释
        # ----------------------------------------------------------
        explanation = {}
        if model is not None and scaler is not None and len(proc_df) >= cfg["lookback_months"]:
            X_scaled, _ = preprocess_features(proc_df, scaler=scaler)
            lookback    = cfg["lookback_months"]
            X_window    = X_scaled[-lookback:].reshape(1, lookback, -1).astype(np.float32)

            # 构建背景数据
            y_raw = proc_df["outbound_qty"].values.astype(np.float32)
            if len(X_scaled) >= lookback + pred_m:
                X_all, _ = create_demand_sequences(X_scaled, y_raw, lookback, pred_m)
                background = X_all[:min(50, len(X_all))]
            else:
                background = X_window

            explanation = get_demand_shap_explanation(
                model, X_window, background, cfg["features"]
            )

        # ----------------------------------------------------------
        # Step 7: 预警判断
        # ----------------------------------------------------------
        if total_demand > current_stock * threshold:
            priority      = "HIGH"
            alert_message = (
                f"【高优先级预警】备件「{part_name}」预测未来 {pred_m} 个月需求"
                f" {total_demand:.0f} 件，远超当前库存 {current_stock} 件"
                f"（{total_demand / max(current_stock, 1):.1f} 倍），请尽快采购！"
            )
        elif total_demand > current_stock:
            priority      = "MEDIUM"
            alert_message = (
                f"备件「{part_name}」预测需求 {total_demand:.0f} 件 > 当前库存"
                f" {current_stock} 件，建议在正常采购周期内补货。"
            )
        else:
            priority      = "LOW"
            alert_message = (
                f"备件「{part_name}」库存充足（{current_stock} 件），"
                f"预测需求 {total_demand:.0f} 件。"
            )

        # ----------------------------------------------------------
        # Step 8: 汇总结果
        # ----------------------------------------------------------
        results.append({
            "spare_part_id":       sid,
            "spare_part_name":     part_name,
            "current_stock":       current_stock,
            "predicted_demand": {
                "method":              pred_method,
                "monthly_detail":      demand_result["monthly_demand"],
                "total":               total_demand,
                "confidence_interval": demand_result["confidence_interval"],
            },
            "suggestion": {
                "suggested_qty":       suggested_qty,
                "safety_stock":        round(safety_stock, 1),
                "suggested_date":      suggested_date + date_note,
                "lead_time_days":      lead_time,
            },
            "supplier":            supplier_info,
            "priority":            priority,
            "alert_message":       alert_message,
            "explanation":         explanation,
            "data_quality":        data_quality,
            "n_months_available":  n_months,
            "timestamp":           timestamp,
        })

        logger.info(
            f"备件 ID={sid}「{part_name}」→ 建议采购 {suggested_qty} 件，"
            f"优先级: {priority}，供应商: {supplier_info['name']}"
        )

    return results


# =============================================================================
# Section 12: 主函数示例 — 模拟训练与补货建议演示
# =============================================================================

def _simulate_consumption_data(
    spare_part_id: int,
    n_months: int = 24,
    base_demand: float = 15.0,
) -> pd.DataFrame:
    """
    生成模拟月度消耗数据用于演示（无需真实数据库）。

    包含季节性波动、趋势和随机噪声。
    """
    np.random.seed(spare_part_id)  # 每个备件有不同的随机种子

    months = pd.date_range(
        end=date.today().replace(day=1),
        periods=n_months,
        freq="MS",
    )

    data = {
        "record_month": months,
        "outbound_qty": [],
        "repair_count": [],
        "avg_unit_price": [],
        "working_days": [],
    }

    for i, m in enumerate(months):
        seasonal = 3.0 * np.sin(2 * np.pi * m.month / 12.0)
        trend    = 0.2 * i
        noise    = np.random.normal(0, 2.0)
        demand   = max(0, round(base_demand + trend + seasonal + noise))

        data["outbound_qty"].append(demand)
        data["repair_count"].append(max(0, round(demand * 0.3 + np.random.normal(0, 1))))
        data["avg_unit_price"].append(round(50 + np.random.normal(0, 3), 2))
        data["working_days"].append(np.random.choice([20, 21, 22, 23]))

    return pd.DataFrame(data)


def _demo_predict_single(spare_part_id: int, n_months: int, base_demand: float):
    """单个备件的演示预测流程。"""
    logger.info(f"\n--- 备件 ID={spare_part_id}：{n_months} 个月数据演示 ---")

    # 模拟消耗数据
    demo_df = _simulate_consumption_data(spare_part_id, n_months=n_months, base_demand=base_demand)
    proc_df = aggregate_monthly_data(demo_df, spare_part_id)

    # 训练模型
    model, scaler = None, None
    try:
        model, scaler = train_demand_model(spare_part_id, proc_df, retrain=True)
    except ValueError as exc:
        logger.warning(f"模型训练失败（预期中）：{exc}")

    # 预测需求
    demand_result = predict_demand(spare_part_id, proc_df, model, scaler)

    print(f"\n备件 ID={spare_part_id} 需求预测结果：")
    print(json.dumps(demand_result, ensure_ascii=False, indent=2))

    # 补货建议计算
    current_stock = 20
    daily_avg     = demand_result["daily_avg"]
    total_demand  = demand_result["total_demand"]
    lead_time     = DEMAND_CONFIG["default_lead_time_days"]
    safety_stock  = daily_avg * lead_time * DEMAND_CONFIG["safety_factor"] if daily_avg > 0 else 0
    suggested_qty = max(0, math.ceil(total_demand - current_stock + safety_stock))

    print(f"\n补货建议：")
    print(f"  当前库存:     {current_stock} 件")
    print(f"  预测总需求:   {total_demand:.1f} 件")
    print(f"  安全库存:     {safety_stock:.1f} 件")
    print(f"  建议采购量:   {suggested_qty} 件")
    priority = "HIGH" if total_demand > current_stock * 1.5 else "MEDIUM" if total_demand > current_stock else "LOW"
    print(f"  优先级:       {priority}")
    return demand_result


def main():
    """
    演示入口：模拟训练 → 需求预测 → 补货建议生成。
    实际部署时直接调用 suggest_replenishment(spare_part_ids) 即可。
    """
    logger.info("=" * 60)
    logger.info("备件管理系统 — 智能补货建议模块演示")
    logger.info("=" * 60)

    # --- 演示 1：数据充足的正常预测（24个月） ---
    _demo_predict_single(spare_part_id=1, n_months=24, base_demand=15.0)

    # --- 演示 2：数据不足的降级预测（4个月） ---
    _demo_predict_single(spare_part_id=2, n_months=4, base_demand=8.0)

    # --- 演示 3：极少数据的统计降级（2个月） ---
    _demo_predict_single(spare_part_id=3, n_months=2, base_demand=5.0)

    # --- 演示 4：完整 suggest_replenishment 接口（需要真实数据库） ---
    # 取消下方注释后可真实运行（确保数据库已配置并运行）：
    #
    # logger.info("\n[演示 4] 完整 suggest_replenishment 接口（需真实数据库）")
    # results = suggest_replenishment(spare_part_ids=[1, 2, 3])
    # print("\n完整补货建议：")
    # print(json.dumps(results, ensure_ascii=False, indent=2))

    logger.info("\n" + "=" * 60)
    logger.info("演示完成。生产环境中请直接调用：")
    logger.info("  from smart_replenishment import suggest_replenishment")
    logger.info("  results = suggest_replenishment(spare_part_ids=[1, 2, 3])")
    logger.info("=" * 60)


if __name__ == "__main__":
    main()
