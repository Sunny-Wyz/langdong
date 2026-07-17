/**
 * 论文《酒企配套厂备件智能管理系统设计与实现》实验表静态数据。
 * 数字与正文一致，禁止用运行时重算覆盖。
 * 源：Desktop/论文定稿/酒企配套厂备件智能管理系统设计与实现.pdf
 */

export const paperMeta = {
  title: '酒企配套厂备件智能管理系统设计与实现',
  chapter: '第三章 间歇性备件的两阶段概率预测与库存优化算法',
  note: '本页为论文实验固定结果，非当前库存全量重算。样本：36 种分层备件 / D01 单备件滚动 / 9 组合库存回测。',
  sampleNote36: '从 420 种备件按 ABC×XYZ 与品类分层抽样 36 种（9 组合各 4 件），前 30 月训练、后 6 月滚动。',
  cslRule: '目标周期服务水平 CSL：A 类 0.99 / B 类 0.95 / C 类 0.90',
  mcParams: '蒙特卡洛 M=10000，月工作日 W=22（默认），提前期 L 按备件'
}

/** 表 3-3 超参数 */
export const table3_3 = {
  title: '表 3-3 两阶段模型超参数搜索空间与最终选取值',
  columns: [
    { prop: 'name', label: '超参数' },
    { prop: 'search', label: '搜索空间' },
    { prop: 'stage1', label: '第一阶段' },
    { prop: 'stage2', label: '第二阶段' }
  ],
  rows: [
    { name: 'n_estimators', search: '{50,100,150,200}', stage1: '100', stage2: '150' },
    { name: 'max_depth', search: '{3,4,5,6}', stage1: '4', stage2: '5' },
    { name: 'learning_rate', search: '{0.05,0.08,0.1,0.15}', stage1: '0.1', stage2: '0.08' },
    { name: 'min_child_weight', search: '{1,2,3,5}', stage1: '3', stage2: '2' },
    { name: 'subsample', search: '{0.7,0.8,0.9,1.0}', stage1: '0.8', stage2: '0.8' },
    { name: 'colsample_bytree', search: '{0.7,0.8,0.9,1.0}', stage1: '0.8', stage2: '0.8' },
    { name: 'reg_alpha', search: '{0,0.01,0.1}', stage1: '0', stage2: '0.01' },
    { name: 'reg_lambda', search: '{0.5,1.0,1.5}', stage1: '1.0', stage2: '1.0' }
  ]
}

/** 表 3-4 D01 滚动预测 */
export const table3_4 = {
  title: '表 3-4 D01 滚动预测：本文方法与基准方法对比',
  months: ['2025-07', '2025-08', '2025-09', '2025-10', '2025-11', '2025-12'],
  columns: [
    { prop: 'method', label: '方法', width: 180 },
    { prop: 'm07', label: '2025-07' },
    { prop: 'm08', label: '2025-08' },
    { prop: 'm09', label: '2025-09' },
    { prop: 'm10', label: '2025-10' },
    { prop: 'm11', label: '2025-11' },
    { prop: 'm12', label: '2025-12' },
    { prop: 'wmape', label: 'wMAPE(%)' }
  ],
  rows: [
    { method: '实际需求', m07: 111, m08: 140, m09: 0, m10: 0, m11: 133, m12: 145, wmape: '—' },
    { method: '两阶段模型（本文）', m07: 124.6, m08: 121.8, m09: 14.5, m10: 12.2, m11: 115.3, m12: 158.7, wmape: '16.99', highlight: true },
    { method: '单阶段 XGBoost 回归', m07: 88.6, m08: 105.4, m09: 56.8, m10: 48.5, m11: 96.2, m12: 110.4, wmape: '44.18' },
    { method: 'Standard RF', m07: 85.2, m08: 102.8, m09: 62.5, m10: 56.8, m11: 95.4, m12: 105.6, wmape: '49.02' },
    { method: 'SBA', m07: 79.4, m08: 81.3, m09: 85.1, m10: 85.1, m11: 85.1, m12: 76.6, wmape: '71.22' },
    { method: 'Croston', m07: 83.6, m08: 85.5, m09: 89.5, m10: 89.5, m11: 89.5, m12: 80.7, wmape: '69.70' },
    { method: '指数平滑(α=0.3)', m07: 76.4, m08: 85.3, m09: 121.2, m10: 89.5, m11: 65.7, m12: 78.4, wmape: '82.02' },
    { method: '简单移动平均(W=3)', m07: 42.3, m08: 88.6, m09: 109.7, m10: 80.5, m11: 41.8, m12: 57.6, wmape: '92.42' }
  ]
}

/** 表 3-5 分层 wMAPE */
export const table3_5 = {
  title: '表 3-5 36 种分层样本的分维度平均 wMAPE 汇总（%）',
  note: '论文实验 36 件分层样本，非当前库全量重算；预测/α 使用论文权重 0.40/0.25/0.20/0.15 + 帕累托 70/90。',
  columns: [
    { prop: 'dim', label: '分维度' },
    { prop: 'group', label: '分组' },
    { prop: 'n', label: '样本数' },
    { prop: 'twoStage', label: '两阶段' },
    { prop: 'xgb', label: '单阶段 XGB' },
    { prop: 'rf', label: 'Std RF' },
    { prop: 'sma', label: 'SMA' }
  ],
  rows: [
    { dim: 'ABC', group: 'A', n: 12, twoStage: 12.89, xgb: 25.21, rf: 31.18, sma: 49.5 },
    { dim: 'ABC', group: 'B', n: 12, twoStage: 14.27, xgb: 28.07, rf: 33.81, sma: 49.41 },
    { dim: 'ABC', group: 'C', n: 12, twoStage: 13.87, xgb: 29.5, rf: 36.25, sma: 47.59 },
    { dim: 'XYZ', group: 'X', n: 12, twoStage: 4.53, xgb: 7.61, rf: 9.32, sma: 29.5 },
    { dim: 'XYZ', group: 'Y', n: 12, twoStage: 12.78, xgb: 24.85, rf: 30.2, sma: 50.0 },
    { dim: 'XYZ', group: 'Z', n: 12, twoStage: 23.72, xgb: 50.32, rf: 61.73, sma: 66.99 },
    { dim: '整体', group: '36 种', n: 36, twoStage: 13.68, xgb: 27.59, rf: 33.75, sma: 48.83, highlight: true }
  ]
}

/** 表 3-6 近年方法对比 */
export const table3_6 = {
  title: '表 3-6 近 5 年代表性算法对比结果',
  columns: [
    { prop: 'method', label: '方法', width: 140 },
    { prop: 'category', label: '类别', width: 100 },
    { prop: 'wmape', label: 'wMAPE(%)' },
    { prop: 'mase', label: 'MASE' },
    { prop: 'crps', label: 'CRPS' },
    { prop: 'cov90', label: '条件90%覆盖率(%)' },
    { prop: 'brier', label: 'Brier' },
    { prop: 'prob', label: '输出概率分布' }
  ],
  rows: [
    { method: '两阶段模型（本文）', category: '—', wmape: 13.68, mase: 0.61, crps: 8.23, cov90: 90.9, brier: 0.15, prob: '是', highlight: true },
    { method: 'DeepAR', category: '深度概率', wmape: 26.7, mase: 1.19, crps: 16.41, cov90: 85.8, brier: 0.2, prob: '是' },
    { method: 'TFT', category: '深度概率', wmape: 25.3, mase: 1.13, crps: 15.72, cov90: 87.0, brier: 0.18, prob: '是(分位数)' },
    { method: 'N-HiTS', category: '深度概率', wmape: 29.6, mase: 1.32, crps: 18.84, cov90: 83.4, brier: 0.23, prob: '是(分位数)' },
    { method: 'TSB', category: '间歇专用', wmape: 39.8, mase: 1.77, crps: 24.51, cov90: '—', brier: '—', prob: '否' },
    { method: 'ADIDA', category: '间歇专用', wmape: 37.9, mase: 1.69, crps: 23.17, cov90: '—', brier: '—', prob: '否' },
    { method: 'MAPA', category: '间歇专用', wmape: 35.8, mase: 1.59, crps: 21.84, cov90: '—', brier: '—', prob: '否' },
    { method: 'NGBoost', category: '概率树', wmape: 23.8, mase: 1.06, crps: 14.37, cov90: 88.1, brier: 0.17, prob: '是' },
    { method: 'LightGBM 分位数', category: '概率树', wmape: 22.9, mase: 1.02, crps: 13.91, cov90: 88.9, brier: '—', prob: '是(分位数)' }
  ]
}

/** 表 3-7 条件 90% 覆盖率 */
export const table3_7 = {
  title: '表 3-7 正需求条件 Gamma 分布 90% 预测区间覆盖率统计',
  note: '仅对正需求测试点统计；零月由 Brier 评价，总需求由 CRPS 评价。Wilson 95% 整体 [85.3%,94.5%]。',
  columns: [
    { prop: 'scope', label: '样本范围' },
    { prop: 'n', label: '预测点数' },
    { prop: 'covered', label: '覆盖点数' },
    { prop: 'miss', label: '未覆盖' },
    { prop: 'rate', label: '条件经验覆盖率' },
    { prop: 'width', label: '平均条件区间宽度(件)' }
  ],
  rows: [
    { scope: '36 种分层备件', n: 154, covered: 140, miss: 14, rate: '90.9%', width: 32.4, highlight: true },
    { scope: 'X 类稳定型', n: 66, covered: 62, miss: 4, rate: '93.9%', width: 18.6 },
    { scope: 'Y 类中等波动', n: 52, covered: 48, miss: 4, rate: '92.3%', width: 35.2 },
    { scope: 'Z 类高波动', n: 36, covered: 30, miss: 6, rate: '83.3%', width: 48.6 }
  ]
}

/** 表 3-8 显著性检验 */
export const table3_8 = {
  title: '表 3-8 本文方法与基准方法的显著性检验结果',
  columns: [
    { prop: 'vs', label: '对比方法' },
    { prop: 'category', label: '方法类别' },
    { prop: 'p', label: 'Wilcoxon p' },
    { prop: 'holm', label: 'Holm 校正后 p' },
    { prop: 'r', label: '效应量 r' },
    { prop: 'sig', label: '是否显著(α=0.05)' }
  ],
  rows: [
    { vs: 'vs 单阶段 XGBoost 回归', category: '点预测', p: '0.001', holm: '≤0.014', r: 0.71, sig: '是' },
    { vs: 'vs Standard RF', category: '点预测', p: '<0.001', holm: '<0.014', r: 0.76, sig: '是' },
    { vs: 'vs SMA', category: '点预测', p: '<0.001', holm: '<0.014', r: 0.83, sig: '是' },
    { vs: 'vs SBA', category: '点预测', p: '<0.001', holm: '<0.014', r: 0.79, sig: '是' },
    { vs: 'vs Croston', category: '点预测', p: '<0.001', holm: '<0.014', r: 0.78, sig: '是' },
    { vs: 'vs 指数平滑(α=0.3)', category: '点预测', p: '<0.001', holm: '<0.014', r: 0.81, sig: '是' },
    { vs: 'vs DeepAR', category: '深度概率', p: '0.031', holm: '0.031', r: 0.52, sig: '是' },
    { vs: 'vs TFT', category: '深度概率', p: '0.004', holm: '0.016', r: 0.61, sig: '是' },
    { vs: 'vs N-HiTS', category: '深度概率', p: '0.001', holm: '≤0.014', r: 0.69, sig: '是' },
    { vs: 'vs TSB', category: '间歇专用', p: '0.002', holm: '≤0.014', r: 0.65, sig: '是' },
    { vs: 'vs ADIDA', category: '间歇专用', p: '<0.001', holm: '<0.014', r: 0.79, sig: '是' },
    { vs: 'vs MAPA', category: '间歇专用', p: '<0.001', holm: '<0.014', r: 0.77, sig: '是' },
    { vs: 'vs NGBoost', category: '概率树', p: '0.006', holm: '0.018', r: 0.58, sig: '是' },
    { vs: 'vs LightGBM 分位数', category: '概率树', p: '0.013', holm: '0.026', r: 0.46, sig: '是' }
  ]
}

/** 表 3-9 鲁棒性 */
export const table3_9 = {
  title: '表 3-9 鲁棒性与泛化测试结果',
  columns: [
    { prop: 'scene', label: '测试场景' },
    { prop: 'setting', label: '设置' },
    { prop: 'twoStage', label: '两阶段 wMAPE(%)' },
    { prop: 'single', label: '单阶段 wMAPE(%)' }
  ],
  rows: [
    { scene: '基线（无扰动）', setting: '原始 36 种样本', twoStage: 13.68, single: 27.59 },
    { scene: '噪声注入', setting: '5% 需求峰值噪声', twoStage: 14.7, single: 27.9 },
    { scene: '噪声注入', setting: '10% 需求峰值噪声', twoStage: 15.2, single: 31.5 },
    { scene: '低零膨胀', setting: '零值占比 <20%', twoStage: 9.8, single: 18.4 },
    { scene: '中零膨胀', setting: '零值占比 20%～50%', twoStage: 13.5, single: 27.0 },
    { scene: '高零膨胀', setting: '零值占比 >50%', twoStage: 22.6, single: 49.8 }
  ]
}

/** 表 3-10 消融 */
export const table3_10 = {
  title: '表 3-10 两阶段概率预测模型消融实验结果（基于定制件 D01）',
  columns: [
    { prop: 'config', label: '模型配置' },
    { prop: 'wmape', label: 'wMAPE(%)' },
    { prop: 'delta', label: '相对单阶段降幅' },
    { prop: 'note', label: '说明' }
  ],
  rows: [
    { config: '单阶段 XGBoost 回归（基准）', wmape: 44.18, delta: '—', note: '直接回归正需求量' },
    { config: '仅第一阶段（概率×历史均值）', wmape: 32.65, delta: '降 11.53 个百分点', note: '概率过滤消除零月偏差' },
    { config: '仅第二阶段（正样本回归）', wmape: 28.43, delta: '降 15.75 个百分点', note: '正样本纯净建模' },
    { config: '两阶段完整模型', wmape: 16.99, delta: '降 27.19 个百分点', note: '联合降幅接近两部分之和', highlight: true }
  ]
}

/** 表 3-11 形状 k 策略 */
export const table3_11 = {
  title: '表 3-11 形状参数估计策略对正需求条件区间的影响',
  note: '工程选型：XYZ 共享（与论文一致）。独立估计 Z 类覆盖仅 61.1%。',
  columns: [
    { prop: 'scheme', label: '方案' },
    { prop: 'overall', label: '整体覆盖率' },
    { prop: 'x', label: 'X 类' },
    { prop: 'y', label: 'Y 类' },
    { prop: 'z', label: 'Z 类' },
    { prop: 'brier', label: '第一阶段 Brier' }
  ],
  rows: [
    { scheme: '(a) 独立估计（不共享）', overall: '78.6%', x: '91.8%', y: '86.5%', z: '61.1%', brier: 0.15 },
    { scheme: '(b) XYZ 共享（本文）', overall: '90.9%', x: '93.9%', y: '92.3%', z: '83.3%', brier: 0.15, highlight: true },
    { scheme: '(c) 数据驱动聚类共享', overall: '90.2%', x: '93.1%', y: '91.0%', z: '81.9%', brier: 0.15 }
  ]
}

/** 表 3-12 D01 MC */
export const table3_12 = {
  title: '表 3-12 D01 提前期需求蒙特卡洛模拟结果',
  note: 'D01（AY，L=14 天，α=99%），M=10000。回测：经验法缺货 272 / 本文 65 / 满足率 94.0%；月末库存 16.6→85.9。',
  columns: [
    { prop: 'stat', label: '统计量' },
    { prop: 'value', label: '数值' },
    { prop: 'use', label: '用途' }
  ],
  rows: [
    { stat: '提前期需求均值 E[DL]', value: '52.31 件', use: '用于计算安全库存' },
    { stat: '提前期需求标准差 σL', value: '31.69 件', use: '反映分布波动' },
    { stat: '95%分位数 Q0.95(DL)', value: '94.85 件', use: '对应 B 类补货点' },
    { stat: '99%分位数 Q0.99(DL)', value: '106.09 件', use: '对应 A 类补货点' },
    { stat: '补货点 ROP=⌈Q0.99⌉', value: '107 件', use: '本文方法回测取值', highlight: true },
    { stat: '安全库存 SS=ROP−⌈E[DL]⌉', value: '54 件', use: '波动缓冲量', highlight: true }
  ]
}

/** 表 3-13 组合回测 */
export const table3_13 = {
  title: '表 3-13 ABC×XYZ 组合安全库存回测结果汇总',
  note: '2023 训练 / 2024 回测。汇总：缺货月 30→6，缺货量 683→98，满足率 85.0%→98.1%，平均月末库存 15.9→38.8。',
  columns: [
    { prop: 'combo', label: '组合' },
    { prop: 'part', label: '备件' },
    { prop: 'method', label: '方法' },
    { prop: 'stockoutMonths', label: '缺货月数' },
    { prop: 'stockoutQty', label: '缺货量(件)' },
    { prop: 'fillRate', label: '需求满足率' },
    { prop: 'avgInv', label: '平均月末库存' }
  ],
  rows: [
    { combo: 'AX', part: 'M01', method: '经验法', stockoutMonths: 5, stockoutQty: 30, fillRate: '89.2%', avgInv: 13.3 },
    { combo: 'AX', part: 'M01', method: '本文方法', stockoutMonths: 1, stockoutQty: 6, fillRate: '97.8%', avgInv: 29.3 },
    { combo: 'AX', part: 'M01', method: '正态解析法', stockoutMonths: 1, stockoutQty: 10, fillRate: '96.4%', avgInv: 33.1 },
    { combo: 'AY', part: 'D01', method: '经验法', stockoutMonths: 4, stockoutQty: 272, fillRate: '74.9%', avgInv: 16.6 },
    { combo: 'AY', part: 'D01', method: '本文方法', stockoutMonths: 1, stockoutQty: 65, fillRate: '94.0%', avgInv: 85.9 },
    { combo: 'AY', part: 'D01', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 112.4 },
    { combo: 'AZ', part: 'D02', method: '经验法', stockoutMonths: 2, stockoutQty: 29, fillRate: '79.3%', avgInv: 11.2 },
    { combo: 'AZ', part: 'D02', method: '本文方法', stockoutMonths: 1, stockoutQty: 3, fillRate: '97.9%', avgInv: 19.4 },
    { combo: 'AZ', part: 'D02', method: '正态解析法', stockoutMonths: 2, stockoutQty: 18, fillRate: '87.5%', avgInv: 16.8 },
    { combo: 'BX', part: 'F01', method: '经验法', stockoutMonths: 4, stockoutQty: 65, fillRate: '85.8%', avgInv: 23.8 },
    { combo: 'BX', part: 'F01', method: '本文方法', stockoutMonths: 1, stockoutQty: 14, fillRate: '96.9%', avgInv: 37.5 },
    { combo: 'BX', part: 'F01', method: '正态解析法', stockoutMonths: 1, stockoutQty: 12, fillRate: '97.3%', avgInv: 43.9 },
    { combo: 'BY', part: 'E04', method: '经验法', stockoutMonths: 2, stockoutQty: 20, fillRate: '88.9%', avgInv: 13.2 },
    { combo: 'BY', part: 'E04', method: '本文方法', stockoutMonths: 1, stockoutQty: 5, fillRate: '97.2%', avgInv: 24.4 },
    { combo: 'BY', part: 'E04', method: '正态解析法', stockoutMonths: 1, stockoutQty: 6, fillRate: '96.7%', avgInv: 27.2 },
    { combo: 'BZ', part: 'E05', method: '经验法', stockoutMonths: 1, stockoutQty: 5, fillRate: '94.6%', avgInv: 4.2 },
    { combo: 'BZ', part: 'E05', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 11.8 },
    { combo: 'BZ', part: 'E05', method: '正态解析法', stockoutMonths: 1, stockoutQty: 9, fillRate: '90.3%', avgInv: 9.6 },
    { combo: 'CX', part: 'F03', method: '经验法', stockoutMonths: 4, stockoutQty: 88, fillRate: '85.5%', avgInv: 29.5 },
    { combo: 'CX', part: 'F03', method: '本文方法', stockoutMonths: 1, stockoutQty: 5, fillRate: '99.2%', avgInv: 58.9 },
    { combo: 'CX', part: 'F03', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 66.3 },
    { combo: 'CY', part: 'F04', method: '经验法', stockoutMonths: 3, stockoutQty: 36, fillRate: '90.0%', avgInv: 18.3 },
    { combo: 'CY', part: 'F04', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 46.6 },
    { combo: 'CY', part: 'F04', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 52.1 },
    { combo: 'CZ', part: 'H05', method: '经验法', stockoutMonths: 5, stockoutQty: 138, fillRate: '76.9%', avgInv: 13.3 },
    { combo: 'CZ', part: 'H05', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 34.9 },
    { combo: 'CZ', part: 'H05', method: '正态解析法', stockoutMonths: 3, stockoutQty: 52, fillRate: '91.3%', avgInv: 27.4 },
    { combo: '汇总', part: '9 种', method: '经验法', stockoutMonths: 30, stockoutQty: 683, fillRate: '85.0%', avgInv: 15.9, highlight: true },
    { combo: '汇总', part: '9 种', method: '本文方法', stockoutMonths: 6, stockoutQty: 98, fillRate: '98.1%', avgInv: 38.8, highlight: true },
    { combo: '汇总', part: '9 种', method: '正态解析法', stockoutMonths: 9, stockoutQty: 107, fillRate: '95.5%', avgInv: 43.2, highlight: true }
  ]
}

/** 表 3-14 正态性检验 */
export const table3_14 = {
  title: '表 3-14 历史月度消耗数据的正态性拟合优度检验',
  note: '9 种回测备件全部拒绝正态假设（9/9）；表中列代表性 3 件。',
  columns: [
    { prop: 'part', label: '备件（组合）' },
    { prop: 'sw', label: 'Shapiro-Wilk p' },
    { prop: 'ks', label: 'KS p' },
    { prop: 'ad', label: 'AD 统计量' },
    { prop: 'reject', label: '是否拒绝正态' }
  ],
  rows: [
    { part: '定制件 D01(AY)', sw: '0.002', ks: '0.009', ad: 1.58, reject: '是' },
    { part: '机械件 M01(AX)', sw: '0.036', ks: '0.041', ad: 0.87, reject: '是' },
    { part: '五金件 H05(CZ)', sw: '<0.001', ks: '0.002', ad: 2.35, reject: '是' }
  ]
}

/** 表 3-15 CSL 对照 */
export const table3_15 = {
  title: '表 3-15 目标 CSL 与实测服务水平对照（本文方法，2024 年回测）',
  note: '实测 CSL=无缺货周期/12；与目标差异受 12 周期小样本限制（Wilson 区间宽）。',
  columns: [
    { prop: 'combo', label: '组合' },
    { prop: 'target', label: '目标 CSL' },
    { prop: 'actual', label: '实测 CSL（无缺货周期/12）' },
    { prop: 'fill', label: '实测满足率' }
  ],
  rows: [
    { combo: 'AX', target: '0.99', actual: '91.7%（11/12）', fill: '97.8%' },
    { combo: 'AY', target: '0.99', actual: '91.7%（11/12）', fill: '94.0%' },
    { combo: 'AZ', target: '0.99', actual: '91.7%（11/12）', fill: '97.9%' },
    { combo: 'BX', target: '0.95', actual: '91.7%（11/12）', fill: '96.9%' },
    { combo: 'BY', target: '0.95', actual: '91.7%（11/12）', fill: '97.2%' },
    { combo: 'BZ', target: '0.95', actual: '100.0%（12/12）', fill: '100.0%' },
    { combo: 'CX', target: '0.90', actual: '91.7%（11/12）', fill: '99.2%' },
    { combo: 'CY', target: '0.90', actual: '100.0%（12/12）', fill: '100.0%' },
    { combo: 'CZ', target: '0.90', actual: '100.0%（12/12）', fill: '100.0%' }
  ]
}

/** 表 4-5 功能测试（摘要） */
export const table4_5 = {
  title: '表 4-5 系统功能测试核心用例与执行结果',
  note: '共 17 个用例，全部通过。',
  columns: [
    { prop: 'id', label: '编号' },
    { prop: 'item', label: '测试项' },
    { prop: 'expect', label: '操作与预期结果' },
    { prop: 'result', label: '结果' }
  ],
  rows: [
    { id: 'TC-IN-01', item: '正常采购入库', expect: '确认入库，生成入库单、库存增加、订单已入库', result: '通过' },
    { id: 'TC-IN-02', item: '入库超量拦截', expect: '超量入库提示并拦截', result: '通过' },
    { id: 'TC-OUT-01', item: 'FIFO 出库', expect: '按最早批次扣减，库存减少', result: '通过' },
    { id: 'TC-OUT-02', item: '库存不足拦截', expect: '超库存申请拦截并提示', result: '通过' },
    { id: 'TC-AP-01', item: '正常领用申请', expect: '生成领用单，状态待审批', result: '通过' },
    { id: 'TC-APR-01', item: 'A 类备件审批', expect: '审批后状态已审批，记录审批人', result: '通过' },
    { id: 'TC-REQ-E2E-01', item: '领用全流程闭环', expect: '申请→审批→出库→安装，状态与库存正确', result: '通过' },
    { id: 'TC-RP-01', item: '故障报修', expect: '生成唯一编号工单，状态报修', result: '通过' },
    { id: 'TC-CP-01', item: '完工确认与 MTTR', expect: '计算 MTTR 并回写', result: '通过' },
    { id: 'TC-WO-E2E-02', item: '工单完工特征联动', expect: '故障数与换件数同步增加', result: '通过' },
    { id: 'TC-LG-01', item: '错误密码拦截', expect: '登录失败提示', result: '通过' },
    { id: 'TC-LG-02', item: 'BCrypt 密码存储', expect: '密码为 $2a$10$ 哈希，非明文', result: '通过' },
    { id: 'TC-SC-02', item: '接口越权拦截', expect: '返回 403', result: '通过' },
    { id: 'TC-FC-01', item: '月度预测定时触发', expect: '生成预测值与 90% 区间，无未来信息泄露', result: '通过' },
    { id: 'TC-FC-04', item: '新备件无数据处理', expect: '跳过并标注数据不足，不报错', result: '通过' },
    { id: 'TC-PO-01', item: 'ROP 触发补货建议', expect: '库存≤ROP 时自动补货建议', result: '通过' },
    { id: 'TC-PO-03', item: '验收自动入库', expect: '验收通过自动生成入库单', result: '通过' }
  ]
}

/** 表 4-6 并发测试 */
export const table4_6 = {
  title: '表 4-6 系统并发测试聚合报告',
  note: '100 并发、10s Ramp-up、5 分钟、思考时间 1s；错误率 0.00%。',
  columns: [
    { prop: 'api', label: '接口' },
    { prop: 'samples', label: '样本数' },
    { prop: 'avg', label: '平均(ms)' },
    { prop: 'med', label: '中位(ms)' },
    { prop: 'max', label: '最大(ms)' },
    { prop: 'p90', label: 'P90(ms)' },
    { prop: 'p95', label: 'P95(ms)' },
    { prop: 'p99', label: 'P99(ms)' },
    { prop: 'err', label: '错误率' },
    { prop: 'tps', label: '吞吐(次/s)' }
  ],
  rows: [
    { api: '分页查询 AI 预测结果', samples: 9542, avg: 34.46, med: 25, max: 246, p90: 66, p95: 83, p99: 119, err: '0.00%', tps: 31.93 },
    { api: '获取备件列表', samples: 9574, avg: 18.89, med: 13, max: 156, p90: 38, p95: 47, p99: 70, err: '0.00%', tps: 32.04 },
    { api: '获取采购订单', samples: 9511, avg: 21.03, med: 15, max: 183, p90: 43, p95: 53, p99: 78, err: '0.00%', tps: 31.83 },
    { api: '合计/平均', samples: 28627, avg: 24.79, med: 19, max: 246, p90: 49, p95: 64, p99: 98, err: '0.00%', tps: 95.8, highlight: true }
  ]
}

export const metricCards = [
  { name: 'wMAPE', def: 'Σ|ŷ−y|/Σ|y|，点预测精度（窗口总量归一）' },
  { name: 'MASE', def: '以朴素季节性为尺度的绝对误差比' },
  { name: 'Brier Score', def: '第一阶段需求发生概率校准' },
  { name: '条件90%覆盖率', def: '正需求月落入 Gamma 0.05–0.95 区间的比例' },
  { name: 'CRPS', def: '总需求混合分布概率评分（越低越好）' }
]
