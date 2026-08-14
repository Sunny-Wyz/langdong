/**
 * 论文《酒企配套厂备件智能管理系统设计与实现》第三章实验表数据。
 */

export const paperMeta = {
  title: '酒企配套厂备件智能管理系统设计与实现',
  chapter: '第三章 间歇性备件的两阶段概率预测与库存优化算法',
  note: '样本：36 种分层备件；代表件 E01=C0070003；九组合库存回测。指标无事后校准。',
  sampleNote36: '九组合各 4 件共 36 种；2023-01～2026-06 共 42 月，前 36 月训练、后 6 月（2026-01～06）滚动。',
  cslRule: '目标周期服务水平 CSL：A 类 0.99 / B 类 0.95 / C 类 0.90',
  mcParams: '算法 3-2：M=3000，W=22，提前期 L 按备件；库存为 (R,Q) 连续盘点'
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

/** 表 3-4 E01 滚动预测 */
export const table3_4 = {
  title: '表 3-4 E01（C0070003）滚动预测：本文方法与基准方法对比',
  months: ['2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06'],
  columns: [
    { prop: 'method', label: '方法', width: 180 },
    { prop: 'm01', label: '2026-01' },
    { prop: 'm02', label: '2026-02' },
    { prop: 'm03', label: '2026-03' },
    { prop: 'm04', label: '2026-04' },
    { prop: 'm05', label: '2026-05' },
    { prop: 'm06', label: '2026-06' },
    { prop: 'wmape', label: 'wMAPE(%)' }
  ],
  rows: [
    { method: '实际需求', m01: 85, m02: 105, m03: 126, m04: 115, m05: 0, m06: 0, wmape: '—' },
    { method: '两阶段模型（本文）', m01: 97.49, m02: 76.87, m03: 103.25, m04: 108.84, m05: 17.44, m06: 3.44, wmape: '20.98', highlight: true },
    { method: '单阶段 XGBoost 回归', m01: 76.48, m02: 75.67, m03: 105.08, m04: 93.38, m05: 17.49, m06: 0.0, wmape: '22.71' },
    { method: 'Standard RF', m01: 93.1, m02: 90.24, m03: 103.6, m04: 82.3, m05: 28.15, m06: 0.2, wmape: '24.67' },
    { method: 'SBA', m01: 84.17, m02: 83.88, m03: 85.27, m04: 88.33, m05: 90.2, m06: 90.2, wmape: '62.59' },
    { method: 'Croston', m01: 88.6, m02: 88.29, m03: 89.76, m04: 92.97, m05: 94.95, m06: 94.95, wmape: '62.29' },
    { method: '指数平滑(α=0.3)', m01: 89.85, m02: 88.39, m03: 93.38, m04: 103.16, m05: 106.71, m06: 74.7, wmape: '57.39' },
    { method: '简单移动平均(W=3)', m01: 93.33, m02: 89.0, m03: 93.0, m04: 105.33, m05: 115.33, m06: 80.33, wmape: '60.94' }
  ]
}

/** 表 3-5 分层 wMAPE */
export const table3_5 = {
  title: '表 3-5 36 种分层样本的分维度平均 wMAPE 汇总（%）',
  note: '36 件分层样本（九组合各 4 件）；n 为备件数。'
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
    { dim: 'ABC', group: 'A', n: 12, twoStage: 18.25, xgb: 22.98, rf: 25.24, sma: 46.16 },
    { dim: 'ABC', group: 'B', n: 12, twoStage: 34.15, xgb: 50.34, rf: 53.15, sma: 54.91 },
    { dim: 'ABC', group: 'C', n: 12, twoStage: 21.19, xgb: 30.17, rf: 33.97, sma: 35.94 },
    { dim: 'XYZ', group: 'X', n: 12, twoStage: 11.54, xgb: 13.42, rf: 13.64, sma: 12.55 },
    { dim: 'XYZ', group: 'Y', n: 12, twoStage: 19.65, xgb: 30.84, rf: 36.51, sma: 64.77 },
    { dim: 'XYZ', group: 'Z', n: 12, twoStage: 64.87, xgb: 79.1, rf: 78.41, sma: 95.43 },
    { dim: '整体', group: '36 种', n: 36, twoStage: 21.95, xgb: 29.61, rf: 32.19, sma: 46.68, highlight: true }
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
  // CRPS：两阶段 ZIG；LightGBM 多分位；NGBoost 截断正态；DeepAR 零膨胀对数正态；TFT 门控残差；点预测 Dirac≡MAE
  note: 'CRPS 对各方法采用统一 empirical 公式；概率方法基于完整预测分布样本计算。',
  rows: [
    { method: '两阶段模型（本文）', category: '—', wmape: 21.95, mase: 0.55, crps: 3.87, cov90: 99.3, brier: 0.077, prob: '是', highlight: true },
    { method: 'LightGBM 分位数', category: '概率树', wmape: 33.33, mase: 0.84, crps: 6.02, cov90: 87.7, brier: 0.211, prob: '是(分位数)' },
    { method: 'NGBoost', category: '概率树', wmape: 36.98, mase: 0.93, crps: 6.83, cov90: 88.4, brier: 0.234, prob: '是' },
    { method: 'TFT', category: '深度概率', wmape: 44.13, mase: 1.11, crps: 7.16, cov90: 85.6, brier: 0.172, prob: '是(分位数)' },
    { method: 'DeepAR', category: '深度概率', wmape: 48.07, mase: 1.20, crps: 7.83, cov90: 87.7, brier: 0.172, prob: '是' },
    { method: 'MAPA', category: '间歇专用', wmape: 48.04, mase: 1.20, crps: 11.79, cov90: '—', brier: '—', prob: '否' },
    { method: 'TSB', category: '间歇专用', wmape: 48.50, mase: 1.22, crps: 11.90, cov90: '—', brier: '—', prob: '否' },
    { method: 'ADIDA', category: '间歇专用', wmape: 48.52, mase: 1.22, crps: 11.91, cov90: '—', brier: '—', prob: '否' },
    { method: 'N-HiTS', category: '深度概率', wmape: 60.86, mase: 1.53, crps: 14.94, cov90: '—', brier: '—', prob: '是(分位数)' }
  ]
}

/** 表 3-7 条件 90% 覆盖率 */
export const table3_7 = {
  title: '表 3-7 正需求条件 Gamma 分布 90% 预测区间覆盖率统计',
  note: '仅对正需求测试点统计；零月由 Brier 评价，总需求由 CRPS 评价。'
  columns: [
    { prop: 'scope', label: '样本范围' },
    { prop: 'n', label: '预测点数' },
    { prop: 'covered', label: '覆盖点数' },
    { prop: 'miss', label: '未覆盖' },
    { prop: 'rate', label: '条件经验覆盖率' },
    { prop: 'width', label: '平均条件区间宽度(件)' }
  ],
  rows: [
    { scope: '36 种分层备件', n: 146, covered: 145, miss: 1, rate: '99.3%', width: 33.3, highlight: true },
    { scope: 'X 类稳定型', n: 69, covered: 69, miss: 0, rate: '100.0%', width: 29.8 },
    { scope: 'Y 类中等波动', n: 46, covered: 45, miss: 1, rate: '97.8%', width: 46.7 },
    { scope: 'Z 类高波动', n: 31, covered: 31, miss: 0, rate: '100.0%', width: 21.0 }
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
    { vs: 'vs 单阶段 XGBoost 回归', category: '点预测', p: '<0.001', holm: '<0.001', r: 0.80, sig: '是' },
    { vs: 'vs Standard RF', category: '点预测', p: '<0.001', holm: '<0.001', r: 0.81, sig: '是' },
    { vs: 'vs SMA', category: '点预测', p: '0.0001', holm: '0.0002', r: 0.67, sig: '是' },
    { vs: 'vs SBA', category: '间歇专用', p: '<0.001', holm: '<0.001', r: 0.80, sig: '是' },
    { vs: 'vs Croston', category: '间歇专用', p: '<0.001', holm: '<0.001', r: 0.79, sig: '是' },
    { vs: 'vs 指数平滑(α=0.3)', category: '点预测', p: '0.0001', holm: '0.0002', r: 0.67, sig: '是' },
    { vs: 'vs DeepAR', category: '深度概率', p: '<0.001', holm: '<0.001', r: 0.79, sig: '是' },
    { vs: 'vs TFT', category: '深度概率', p: '<0.001', holm: '0.0001', r: 0.73, sig: '是' },
    { vs: 'vs N-HiTS', category: '深度概率', p: '<0.001', holm: '0.0001', r: 0.74, sig: '是' },
    { vs: 'vs TSB', category: '间歇专用', p: '<0.001', holm: '<0.001', r: 0.75, sig: '是' },
    { vs: 'vs ADIDA', category: '间歇专用', p: '<0.001', holm: '<0.001', r: 0.76, sig: '是' },
    { vs: 'vs MAPA', category: '间歇专用', p: '<0.001', holm: '0.0001', r: 0.74, sig: '是' },
    { vs: 'vs NGBoost', category: '概率树', p: '<0.001', holm: '<0.001', r: 0.83, sig: '是' },
    { vs: 'vs LightGBM 分位数', category: '概率树', p: '0.0001', holm: '0.0002', r: 0.64, sig: '是' }
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
    { scene: '基线（无扰动）', setting: '原始 36 种样本', twoStage: 21.95, single: 29.61 },
    { scene: '噪声注入', setting: '5% 需求峰值噪声', twoStage: 22.45, single: 29.99 },
    { scene: '噪声注入', setting: '10% 需求峰值噪声', twoStage: 23.53, single: 30.38 },
    { scene: '低零膨胀', setting: '零值占比 <20%（15 件）', twoStage: 12.56, single: 15.32 },
    { scene: '中零膨胀', setting: '零值占比 20%～50%（15 件）', twoStage: 31.28, single: 44.62 },
    { scene: '高零膨胀', setting: '零值占比 >50%（6 件）', twoStage: 118.82, single: 162.54 }
  ]
}

/** 表 3-10 消融 */
export const table3_10 = {
  title: '表 3-10 两阶段概率预测模型消融实验（36 种分层样本）',
  columns: [
    { prop: 'config', label: '模型配置' },
    { prop: 'wmape', label: 'wMAPE(%)' },
    { prop: 'delta', label: '相对单阶段降幅' },
    { prop: 'note', label: '说明' }
  ],
  rows: [
    { config: '单阶段 XGBoost 回归（基准）', wmape: 29.61, delta: '—', note: '直接回归需求量' },
    { config: '仅第一阶段（概率×历史均值）', wmape: 33.05, delta: '升 3.44 个百分点', note: '缺少第二阶段回归' },
    { config: '仅第二阶段（正样本回归）', wmape: 50.33, delta: '升 20.72 个百分点', note: '不区分零需求月' },
    { config: '两阶段完整模型', wmape: 21.95, delta: '降 7.66 个百分点', note: 'p×μ', highlight: true }
  ]
}

/** 表 3-11 C0050002 提前期 MC */
export const table3_11 = {
  title: '表 3-11 BY 类备件 C0050002 提前期需求蒙特卡洛模拟结果',
  note: 'B 类，L=30 天，α=0.95，M=3000。ROP=⌈Q0.95⌉。',
  columns: [
    { prop: 'stat', label: '统计量' },
    { prop: 'value', label: '数值' },
    { prop: 'use', label: '用途' }
  ],
  rows: [
    { stat: '提前期需求均值 E[DL]', value: '28.73 件', use: '用于计算安全库存' },
    { stat: '提前期需求标准差 σL', value: '10.77 件', use: '反映分布波动' },
    { stat: '95%分位数 Q0.95(DL)', value: '44.59 件', use: 'B 类对应补货点' },
    { stat: '99%分位数 Q0.99(DL)', value: '51.31 件', use: '供对比参考' },
    { stat: '补货点 ROP=⌈Q0.95⌉', value: '45 件', use: '本文方法该月取值', highlight: true },
    { stat: '安全库存 SS=ROP−⌈E[DL]⌉', value: '16 件', use: '波动缓冲量', highlight: true }
  ]
}

/** 表 3-12 C0050002 三法回测 */
export const table3_12 = {
  title: '表 3-12 备件 C0050002 三种补货方法回测对比',
  note: 'BY，L=30 天，α=95%；2026-01～06。',
  columns: [
    { prop: 'method', label: '方法' },
    { prop: 'src', label: '补货点来源' },
    { prop: 'stockoutMonths', label: '缺货月数' },
    { prop: 'stockoutQty', label: '缺货量(件)' },
    { prop: 'fillRate', label: '需求满足率' },
    { prop: 'avgInv', label: '平均月末库存' }
  ],
  rows: [
    { method: '经验法', src: '历史月均×提前期+分级余量', stockoutMonths: 3, stockoutQty: 36.22, fillRate: '67.1%', avgInv: 15.92 },
    { method: '本文方法', src: '算法3-2 MC Qα', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 33.4, highlight: true },
    { method: '正态解析法', src: 'E[DL]+zα×σL', stockoutMonths: 2, stockoutQty: 2.85, fillRate: '97.4%', avgInv: 22.77 }
  ]
}

/** 表 3-13 组合回测 */
export const table3_13 = {
  title: '表 3-13 ABC×XYZ 组合安全库存回测结果汇总',
  note: '训练 2023-01～2025-12，回测 2026-01～06。汇总：缺货月 8→0，缺货量 75.91→0，满足率 89.2%→100%，平均月末库存 30.58→31.59。',
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
    { combo: 'AX', part: 'M01', method: '经验法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 60.06 },
    { combo: 'AX', part: 'M01', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 43.76 },
    { combo: 'AX', part: 'M01', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 69.68 },
    { combo: 'AY', part: 'E02', method: '经验法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 103.18 },
    { combo: 'AY', part: 'E02', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 101.1 },
    { combo: 'AY', part: 'E02', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 199.64 },
    { combo: 'AZ', part: 'E03', method: '经验法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 26.46 },
    { combo: 'AZ', part: 'E03', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 22.7 },
    { combo: 'AZ', part: 'E03', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 65.87 },
    { combo: 'BX', part: 'H01', method: '经验法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 37.09 },
    { combo: 'BX', part: 'H01', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 16.52 },
    { combo: 'BX', part: 'H01', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 44.98 },
    { combo: 'BY', part: 'M02', method: '经验法', stockoutMonths: 3, stockoutQty: 36.22, fillRate: '67.1%', avgInv: 15.92 },
    { combo: 'BY', part: 'M02', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 33.4 },
    { combo: 'BY', part: 'M02', method: '正态解析法', stockoutMonths: 2, stockoutQty: 2.85, fillRate: '97.4%', avgInv: 22.77 },
    { combo: 'BZ', part: 'E04', method: '经验法', stockoutMonths: 2, stockoutQty: 9.44, fillRate: '71.4%', avgInv: 5.69 },
    { combo: 'BZ', part: 'E04', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 23.84 },
    { combo: 'BZ', part: 'E04', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 14.47 },
    { combo: 'CX', part: 'M03', method: '经验法', stockoutMonths: 2, stockoutQty: 29.0, fillRate: '68.1%', avgInv: 7.71 },
    { combo: 'CX', part: 'M03', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 24.96 },
    { combo: 'CX', part: 'M03', method: '正态解析法', stockoutMonths: 2, stockoutQty: 27.03, fillRate: '70.3%', avgInv: 8.27 },
    { combo: 'CY', part: 'E05', method: '经验法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 10.84 },
    { combo: 'CY', part: 'E05', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 8.65 },
    { combo: 'CY', part: 'E05', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 16.83 },
    { combo: 'CZ', part: 'H02', method: '经验法', stockoutMonths: 1, stockoutQty: 1.25, fillRate: '95.8%', avgInv: 8.23 },
    { combo: 'CZ', part: 'H02', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 9.34 },
    { combo: 'CZ', part: 'H02', method: '正态解析法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 12.62 },
    { combo: '汇总', part: '9 种', method: '经验法', stockoutMonths: 8, stockoutQty: 75.91, fillRate: '89.2%', avgInv: 30.58, highlight: true },
    { combo: '汇总', part: '9 种', method: '本文方法', stockoutMonths: 0, stockoutQty: 0, fillRate: '100.0%', avgInv: 31.59, highlight: true },
    { combo: '汇总', part: '9 种', method: '正态解析法', stockoutMonths: 4, stockoutQty: 29.88, fillRate: '96.4%', avgInv: 50.57, highlight: true }
  ]
}

/** 表 3-14 正态性检验 */
export const table3_14 = {
  title: '表 3-14 历史月度消耗数据的正态性拟合优度检验',
  note: '9 种回测备件中 8 种拒绝正态；仅 AX 件 M01（C0100002）未拒绝。'
  columns: [
    { prop: 'part', label: '备件（组合）' },
    { prop: 'sw', label: 'Shapiro-Wilk p' },
    { prop: 'ks', label: 'KS p' },
    { prop: 'ad', label: 'AD 统计量' },
    { prop: 'reject', label: '是否拒绝正态' }
  ],
  rows: [
    { part: 'M01（AX）C0100002', sw: '0.874', ks: '0.597', ad: 0.292, reject: '否' },
    { part: 'E02（AY）C0020002', sw: '<0.001', ks: '<0.001', ad: 7.34, reject: '是' },
    { part: 'E05（CY）C0070006', sw: '<0.001', ks: '<0.001', ad: 6.88, reject: '是' }
  ]
}

/** 表 3-15 CSL 对照 */
export const table3_15 = {
  title: '表 3-15 目标 CSL 与实测服务水平对照（本文方法，2026-01～06）',
  note: '实测 CSL=无缺货周期/6。'
  columns: [
    { prop: 'combo', label: '组合' },
    { prop: 'target', label: '目标 CSL' },
    { prop: 'actual', label: '实测 CSL（无缺货周期/12）' },
    { prop: 'fill', label: '实测满足率' }
  ],
  rows: [
    { combo: 'AX', target: '0.99', actual: '100.0%（6/6）', fill: '100.0%' },
    { combo: 'AY', target: '0.99', actual: '100.0%（6/6）', fill: '100.0%' },
    { combo: 'AZ', target: '0.99', actual: '100.0%（6/6）', fill: '100.0%' },
    { combo: 'BX', target: '0.95', actual: '100.0%（6/6）', fill: '100.0%' },
    { combo: 'BY', target: '0.95', actual: '100.0%（6/6）', fill: '100.0%' },
    { combo: 'BZ', target: '0.95', actual: '100.0%（6/6）', fill: '100.0%' },
    { combo: 'CX', target: '0.90', actual: '100.0%（6/6）', fill: '100.0%' },
    { combo: 'CY', target: '0.90', actual: '100.0%（6/6）', fill: '100.0%' },
    { combo: 'CZ', target: '0.90', actual: '100.0%（6/6）', fill: '100.0%' }
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
