from __future__ import annotations

import logging
from typing import Any
import numpy as np

logger = logging.getLogger(__name__)


def simulate_lead_time_demand(
    p_t: float,
    mu_t: float,
    k: float,
    L: float,
    W: int = 22,
    M: int = 10000,
    alpha: float = 0.95
) -> dict[str, Any]:
    """
    实现论文 3.3.2 节算法 3-2 的蒙特卡洛模拟逻辑。
    通过“工作日比例分配”模拟提前期内的累计需求，进而推算出 ROP（补货点）和 SS（安全库存）。

    - p_t: 需求发生概率
    - mu_t: 正需求预测均值
    - k: 形状参数
    - L: 采购提前期（天）
    - W: 月工作天数（默认 22 天）
    - M: 模拟次数（默认 10000 次）
    - alpha: 目标服务水平（如 0.95）
    """
    # 异常输入防御
    if L <= 0 or k <= 0 or mu_t <= 0:
        return {
            "rop": 0,
            "ss": 0,
            "mean_demand": 0.0
        }

    p_t = float(np.clip(p_t, 0.0, 1.0))

    # 1. 随机抽取起始工作日 s ~ Uniform(1, W) 
    # numpy.random.randint 的 high 参数是开区间，所以用 W + 1
    s = np.random.randint(1, W + 1, size=M)

    # 2. 提前期需求累计容器
    D_L = np.zeros(M, dtype=float)

    # Month 1 有效天数计算：d1 = min(L, W - s + 1)
    d1 = np.minimum(L, W - s + 1.0)

    # 采样 Month 1 需求并按天数比例分配累计值
    I1 = np.random.binomial(n=1, p=p_t, size=M)
    Y1 = np.random.gamma(shape=k, scale=mu_t / k, size=M)
    D_L += (d1 / W) * I1 * Y1

    # 剩余天数
    L_rem = np.maximum(0.0, L - d1)

    # 3. 跨月循环（支持泛化的大于 W 天的提前期 L）
    while np.any(L_rem > 0.0):
        mask = L_rem > 0.0
        n_active = np.sum(mask)

        # 当前月天数
        d_next = np.minimum(L_rem[mask], float(W))

        # 采样当前月独立需求并比例分配
        I_next = np.random.binomial(n=1, p=p_t, size=n_active)
        Y_next = np.random.gamma(shape=k, scale=mu_t / k, size=n_active)

        D_L[mask] += (d_next / W) * I_next * Y_next

        # 减去采样过的天数
        L_rem[mask] -= d_next

    # 4. 指标结算
    # 补货点 ROP = 向上取整( Quantile(samples, alpha) )
    quantile_val = float(np.percentile(D_L, alpha * 100))
    rop = int(np.ceil(quantile_val))

    # 安全库存 SS = ROP - 向上取整( Mean(samples) )
    mean_demand = float(np.mean(D_L))
    ss = rop - int(np.ceil(mean_demand))

    return {
        "rop": rop,
        "ss": ss,
        "mean_demand": mean_demand
    }
