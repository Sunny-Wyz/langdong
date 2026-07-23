<template>
  <div class="page-container real-exp-page">
    <el-card shadow="hover">
      <template #header>
        <div class="phead header">
          <span class="header-icon">🧪</span>
          <div class="title">真实实验</div>
          <div class="head-btn-group">
            <el-form :inline="true" size="small" class="run-form">
              <el-form-item label="测试月数">
                <el-input-number v-model="testMonths" :min="3" :max="12" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="running" @click="startRun">运行论文叙事回测</el-button>
                <el-button :loading="loading" @click="loadLatest">刷新结果</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb"
        title="评估协议：9×4 分层滚动多基线对比 + 库存三方法回测 + 消融与形状参数策略分析。"
      />

      <el-alert
        v-if="runStatus"
        :type="statusAlertType"
        :closable="false"
        show-icon
        class="mb"
        :title="statusTitle"
      >
        <div v-if="runStatus.status === 'RUNNING'" style="margin-top: 8px">
          <el-progress :percentage="runStatus.percent || 0" :stroke-width="12" />
        </div>
      </el-alert>

      <template v-if="result && result.available">
        <!-- 总览 KPI -->
        <div class="kpi-row mb">
          <div class="kpi-item highlight">
            <div class="kpi-label">两阶段 wMAPE</div>
            <div class="kpi-val">{{ fmt(wmapeTwo) }}%</div>
            <div class="kpi-sub">论文 13.68%</div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">SMA-3 wMAPE</div>
            <div class="kpi-val muted">{{ fmt(wmapeSma) }}%</div>
          </div>
          <div class="kpi-item highlight">
            <div class="kpi-label">优于 SMA</div>
            <div class="kpi-val" :class="advantageClass">{{ fmt(advantage) }} pt</div>
            <div class="kpi-sub">目标 ≥8 pt</div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">Brier</div>
            <div class="kpi-val">{{ fmt(result.overall?.brier) }}</div>
            <div class="kpi-sub">论文 0.15</div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">条件90%覆盖</div>
            <div class="kpi-val">{{ fmt(covRate) }}%</div>
            <div class="kpi-sub">论文 90.9%</div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">样本 / 备件</div>
            <div class="kpi-val small">{{ result.sampleCount }} / {{ result.partCount }}</div>
          </div>
        </div>

        <el-descriptions :column="3" border size="small" class="mb">
          <el-descriptions-item label="测试月份">{{ (result.testMonths || []).join(', ') }}</el-descriptions-item>
          <el-descriptions-item label="代表件">{{ result.focusPartCode || '—' }}（表 3-4 角色）</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ formatMs(result.elapsedMs) }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="tab">
          <el-tab-pane label="分层指标" name="group">
            <h4>按 ABC（表 3-5 结构）</h4>
            <el-table :data="result.byAbc || []" border size="small" class="mb" style="width:100%">
              <el-table-column prop="group" label="分组" width="80" />
              <el-table-column prop="n" label="样本" width="70" />
              <el-table-column prop="wmape_two_stage" label="两阶段 wMAPE" />
              <el-table-column prop="wmape_sma3" label="SMA-3" />
              <el-table-column prop="wmape_single_xgb" label="单阶段 XGB" />
              <el-table-column prop="wmape_rf" label="RF" />
              <el-table-column prop="wmape_sba" label="SBA" />
              <el-table-column prop="brier" label="Brier" />
              <el-table-column prop="cov_coverageRate" label="覆盖率%" />
            </el-table>
            <h4>按 XYZ（期望 X&lt;Y&lt;Z）</h4>
            <el-table :data="result.byXyz || []" border size="small" class="mb" style="width:100%">
              <el-table-column prop="group" label="分组" width="80" />
              <el-table-column prop="n" label="样本" width="70" />
              <el-table-column prop="wmape_two_stage" label="两阶段 wMAPE" />
              <el-table-column prop="wmape_sma3" label="SMA-3" />
              <el-table-column prop="wmape_single_xgb" label="单阶段 XGB" />
              <el-table-column prop="wmape_rf" label="RF" />
              <el-table-column prop="brier" label="Brier" />
              <el-table-column prop="cov_coverageRate" label="覆盖率%" />
            </el-table>
            <h4>按测试月</h4>
            <el-table :data="result.byMonth || []" border size="small" style="width:100%">
              <el-table-column prop="month" label="月份" width="100" />
              <el-table-column prop="n" label="n" width="60" />
              <el-table-column prop="wmape_two_stage" label="两阶段" />
              <el-table-column prop="wmape_sma3" label="SMA-3" />
              <el-table-column prop="wmape_single_xgb" label="单阶段" />
              <el-table-column prop="wmape_sba" label="SBA" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="多方法对比" name="methods" lazy>
            <div v-if="tab === 'methods'">
              <h4>整体方法对比（表 3-4/3-6 结构，含 CRPS 对照）</h4>
              <p class="note">
                CRPS 统一 empirical 公式；两阶段=零膨胀 Gamma；LightGBM=多分位采样；NGBoost=截断正态；
                DeepAR=零膨胀对数正态；TFT=门控残差正态；点预测=Dirac（CRPS≡MAE）。
                {{ result.meta?.crpsProtocol ? '' : '' }}
              </p>
              <el-table :data="result.table36 || []" border size="small" class="mb" style="width:100%" :row-class-name="methodRowClass">
                <el-table-column prop="method" label="方法" min-width="160" />
                <el-table-column prop="category" label="类别" width="100" />
                <el-table-column prop="wmape" label="wMAPE(%)" width="100" />
                <el-table-column prop="mase" label="MASE" width="90" />
                <el-table-column prop="crps" label="CRPS↓" width="90" />
                <el-table-column prop="coverage" label="90%覆盖" width="100" />
                <el-table-column prop="brier" label="Brier↓" width="90" />
                <el-table-column prop="crpsSource" label="CRPS口径" min-width="140" show-overflow-tooltip />
              </el-table>

              <h4>代表件 {{ result.focusPartCode }} 滚动序列（表 3-4 角色）</h4>
              <p class="note">各方法 wMAPE：
                <span v-for="(v, k) in (result.focusWmape || {})" :key="k" class="tag">
                  {{ labelOf(k) }}={{ v }}%
                </span>
              </p>
              <el-table :data="focusTableRows" border size="small" style="width:100%" class="mb">
                <el-table-column prop="month" label="月份" width="100" fixed />
                <el-table-column prop="actual" label="实际" width="80" />
                <el-table-column prop="two_stage" label="两阶段" width="90" />
                <el-table-column prop="single_xgb" label="单阶段XGB" width="100" />
                <el-table-column prop="rf" label="RF" width="80" />
                <el-table-column prop="sba" label="SBA" width="80" />
                <el-table-column prop="croston" label="Croston" width="90" />
                <el-table-column prop="es" label="ES" width="80" />
                <el-table-column prop="sma3" label="SMA3" width="80" />
                <el-table-column prop="tsb" label="TSB" width="80" />
                <el-table-column prop="lgbm_q" label="LGBM-Q" width="90" />
                <el-table-column prop="deepar_like" label="DeepAR*" width="90" />
                <el-table-column prop="tft_like" label="TFT*" width="80" />
              </el-table>
              <div ref="focusChartRef" class="chart-box" />
              <p class="note">* 为简化复现，非完整 GluonTS 训练流水线。</p>
            </div>
          </el-tab-pane>

          <el-tab-pane label="库存回测" name="inv">
            <el-alert type="warning" :closable="false" show-icon class="mb" :title="result.inventory?.note || '库存三方法'" />
            <h4>九组合汇总</h4>
            <el-table :data="result.inventory?.summary || []" border size="small" class="mb" style="width:100%">
              <el-table-column prop="method" label="方法" />
              <el-table-column prop="stockoutMonths" label="缺货月次" />
              <el-table-column prop="stockoutQty" label="缺货量" />
              <el-table-column prop="fillRate" label="满足率%" />
              <el-table-column prop="avgInv" label="平均月末库存" />
            </el-table>
            <h4>分组合明细</h4>
            <el-table :data="inventoryFlat" border size="small" max-height="420" style="width:100%">
              <el-table-column prop="combo" label="组合" width="70" />
              <el-table-column prop="partCode" label="备件" width="110" />
              <el-table-column prop="method" label="方法" width="100" />
              <el-table-column prop="rop" label="ROP" width="70" />
              <el-table-column prop="stockoutMonths" label="缺货月" width="80" />
              <el-table-column prop="stockoutQty" label="缺货量" width="80" />
              <el-table-column prop="fillRate" label="满足率%" width="90" />
              <el-table-column prop="avgInv" label="均库存" width="90" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="扩展实验" name="ext">
            <h4>消融（代表件）</h4>
            <el-table :data="result.ablation || []" border size="small" class="mb" style="width:100%">
              <el-table-column prop="config" label="配置" min-width="200" />
              <el-table-column prop="wmape" label="wMAPE(%)" width="120" />
              <el-table-column prop="delta" label="相对降幅(pt)" width="120" />
              <el-table-column prop="note" label="说明" />
            </el-table>
            <h4>形状参数 k 策略</h4>
            <el-table :data="kTable" border size="small" class="mb" style="width:100%">
              <el-table-column prop="scheme" label="方案" min-width="180" />
              <el-table-column prop="kX" label="k_X" width="90" />
              <el-table-column prop="kY" label="k_Y" width="90" />
              <el-table-column prop="kZ" label="k_Z" width="90" />
              <el-table-column prop="note" label="说明" />
            </el-table>
            <h4>相对基线优势（按备件）</h4>
            <el-table :data="result.significance || []" border size="small" style="width:100%">
              <el-table-column prop="vs" label="对比" min-width="140" />
              <el-table-column prop="parts" label="备件数" width="80" />
              <el-table-column prop="twoStageBetterCount" label="两阶段更优数" width="120" />
              <el-table-column prop="meanGapWmape" label="均优势(pt)" width="110" />
              <el-table-column prop="significant" label="显著占优" width="100">
                <template #default="s">{{ s.row.significant ? '是' : '否' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="明细" name="detail">
            <p class="note">共 {{ result.detailTotal }} 行（展示前 500）</p>
            <el-table :data="detailFlat" border size="small" max-height="520" stripe style="width:100%">
              <el-table-column prop="partCode" label="编码" width="100" fixed />
              <el-table-column prop="month" label="月" width="90" />
              <el-table-column prop="abc" label="ABC" width="60" />
              <el-table-column prop="xyz" label="XYZ" width="60" />
              <el-table-column prop="actual" label="实际" width="70" />
              <el-table-column prop="two_stage" label="两阶段" width="80" />
              <el-table-column prop="sma3" label="SMA3" width="70" />
              <el-table-column prop="single_xgb" label="单阶段" width="80" />
              <el-table-column prop="occurrenceProb" label="p" width="70" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>

      <el-empty v-else description="暂无结果：请点击「运行论文叙事回测」" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import request from '@/utils/request'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

const testMonths = ref(6)
const running = ref(false)
const loading = ref(false)
const runStatus = ref<any>(null)
const result = ref<any>(null)
const tab = ref('group')
const focusChartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

const wmapeTwo = computed(() => result.value?.overall?.wmapeTwoStage ?? result.value?.overallMethods?.two_stage)
const wmapeSma = computed(() => result.value?.overall?.wmapeSma3 ?? result.value?.overallMethods?.sma3)
const advantage = computed(() => result.value?.advantageOverSma ?? (Number(wmapeSma.value) - Number(wmapeTwo.value)))
const covRate = computed(() => result.value?.coverage?.coverageRate ?? result.value?.overall?.cov_coverageRate)
const advantageClass = computed(() => (Number(advantage.value) >= 8 ? 'good' : 'warn'))

const statusAlertType = computed(() => {
  const s = runStatus.value?.status
  if (s === 'RUNNING') return 'warning'
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'error'
  return 'info'
})
const statusTitle = computed(() => {
  if (!runStatus.value) return ''
  return `${runStatus.value.status || ''}：${runStatus.value.message || ''}`
})

const focusTableRows = computed(() => {
  const series = result.value?.focusSeries || []
  return series.map((r: any) => ({
    month: r.month,
    actual: r.actual,
    ...(r.preds || {})
  }))
})

const inventoryFlat = computed(() => {
  const rows: any[] = []
  for (const g of result.value?.inventory?.byCombo || []) {
    for (const m of g.methods || []) {
      rows.push({ combo: g.combo, partCode: g.partCode, ...m })
    }
  }
  return rows
})

const kTable = computed(() => {
  return (result.value?.kStrategy || []).map((row: any) => {
    const k = row.k
    if (typeof k === 'object' && k !== null) {
      return { scheme: row.scheme, kX: k.X, kY: k.Y, kZ: k.Z, note: row.note }
    }
    return { scheme: row.scheme, kX: k, kY: k, kZ: k, note: row.note }
  })
})

const detailFlat = computed(() => {
  return (result.value?.detail || []).map((r: any) => ({
    partCode: r.partCode,
    month: r.month,
    abc: r.abc,
    xyz: r.xyz,
    actual: r.actual,
    occurrenceProb: r.occurrenceProb,
    ...(r.preds || {})
  }))
})

function fmt(v: any) {
  if (v == null || v === '' || Number.isNaN(Number(v))) return '—'
  const n = Number(v)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}
function formatMs(ms: number) {
  if (!ms) return '—'
  if (ms < 1000) return ms + ' ms'
  return (ms / 1000).toFixed(1) + ' s'
}
function labelOf(k: string) {
  return result.value?.methodLabels?.[k] || k
}
function methodRowClass({ row }: any) {
  return row.methodKey === 'two_stage' ? 'row-highlight' : ''
}

async function startRun() {
  running.value = true
  try {
    const res = await request.post('/ai/experiment/run', null, {
      params: { testMonths: testMonths.value, maxParts: 36 }
    })
    const data = res.data || res
    const payload = data.data || data
    if (payload.accepted === false) {
      ElMessage.warning(payload.message || '无法启动')
    } else {
      ElMessage.success(payload.message || '已启动')
    }
    startPoll()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '启动失败')
  } finally {
    running.value = false
  }
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(async () => {
    await fetchStatus()
    const s = runStatus.value?.status
    if (s === 'SUCCESS') {
      stopPoll()
      await loadLatest()
    } else if (s === 'FAILED') {
      stopPoll()
    }
  }, 3000)
  fetchStatus()
}
function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}
async function fetchStatus() {
  try {
    const res = await request.get('/ai/experiment/status')
    const data = res.data || res
    runStatus.value = data.data || data
  } catch { /* ignore */ }
}
async function loadLatest() {
  loading.value = true
  try {
    const res = await request.get('/ai/experiment/latest')
    const data = res.data || res
    const payload = data.data || data
    result.value = payload?.available ? payload : null
    if (result.value && tab.value === 'methods') {
      await nextTick()
      renderFocusChart()
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function renderFocusChart() {
  const series = result.value?.focusSeries || []
  if (!focusChartRef.value || !series.length) return
  if (!chart) chart = echarts.init(focusChartRef.value)
  const months = series.map((r: any) => r.month)
  chart.setOption({
    title: { text: `代表件 ${result.value.focusPartCode} 多方法对比`, left: 'center', textStyle: { fontSize: 13 } },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, type: 'scroll' },
    grid: { left: 48, right: 24, top: 40, bottom: 56 },
    xAxis: { type: 'category', data: months },
    yAxis: { type: 'value', name: '件' },
    series: [
      { name: '实际', type: 'line', data: series.map((r: any) => r.actual), lineStyle: { width: 3 } },
      { name: '两阶段', type: 'line', data: series.map((r: any) => r.preds?.two_stage) },
      { name: 'SMA-3', type: 'line', data: series.map((r: any) => r.preds?.sma3), lineStyle: { type: 'dashed' } },
      { name: '单阶段XGB', type: 'line', data: series.map((r: any) => r.preds?.single_xgb), lineStyle: { type: 'dashed' } },
      { name: 'SBA', type: 'line', data: series.map((r: any) => r.preds?.sba), lineStyle: { type: 'dotted' } }
    ]
  })
}

watch(tab, (t) => {
  if (t === 'methods') nextTick(() => { renderFocusChart(); requestAnimationFrame(() => chart?.resize()) })
})

onMounted(() => {
  loadLatest()
  fetchStatus().then(() => {
    if (runStatus.value?.status === 'RUNNING') startPoll()
  })
  window.addEventListener('resize', () => chart?.resize())
})
onBeforeUnmount(() => {
  stopPoll()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.header { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; justify-content: space-between; }
.head-btn-group { margin-left: auto; }
.run-form { margin: 0; }
.run-form :deep(.el-form-item) { margin-bottom: 0; }
.mb { margin-bottom: 14px; }
.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  padding: 12px;
  background: #f0f5ff;
  border-radius: 6px;
}
.kpi-item { text-align: center; }
.kpi-item.highlight { background: #fff; border-radius: 6px; padding: 8px; }
.kpi-label { font-size: 12px; color: #606266; margin-bottom: 4px; }
.kpi-val { font-size: 22px; font-weight: 700; color: #0f3086; }
.kpi-val.small { font-size: 16px; }
.kpi-val.muted { color: #909399; font-size: 18px; }
.kpi-val.good { color: #67c23a; }
.kpi-val.warn { color: #e6a23c; }
.kpi-sub { font-size: 11px; color: #909399; margin-top: 2px; }
.note { font-size: 12px; color: #909399; margin: 0 0 10px; line-height: 1.6; }
.tag { margin-right: 10px; }
.chart-box { width: 100%; height: 340px; margin-top: 12px; }
h4 { margin: 12px 0 8px; font-size: 14px; }
:deep(.row-highlight) { background-color: #ecf5ff !important; font-weight: 600; }
</style>
