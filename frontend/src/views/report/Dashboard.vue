<template>
  <div class="page-container dashboard-container">
    <!-- 顶部待办摘要条 -->
    <div class="todo-bar" v-if="todoBar.total > 0">
      <span class="todo-icon">🔔</span>
      <span class="todo-text">今日待办：</span>
      <el-tag v-if="todoBar.lowStock > 0" type="danger" size="small" class="todo-tag">
        低库存预警 {{ todoBar.lowStock }} 条
      </el-tag>
      <el-tag v-if="todoBar.overdueWO > 0" type="warning" size="small" class="todo-tag">
        逾期工单 {{ todoBar.overdueWO }} 条
      </el-tag>
      <el-tag v-if="todoBar.overduePO > 0" size="small" class="todo-tag" style="background:#e6f7ff;color:#1890ff;border-color:#91d5ff">
        采购延期 {{ todoBar.overduePO }} 条
      </el-tag>
      <el-button size="small" class="todo-link" @click="goToWarning">查看全部 →</el-button>
    </div>

    <!-- 月份筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <span class="filter-icon">📅</span>
        <span class="filter-label">KPI 月份</span>
        <el-date-picker
          v-model="selectedMonth"
          type="month"
          format="YYYY-MM"
          value-format="YYYY-MM"
          placeholder="选择月份"
          size="default"
          style="width:140px"
          @change="handleMonthChange"
        />
        <el-button
          size="default"
          :loading="loading"
          style="margin-left:8px"
          @click="handleRefresh"
        >🔄 刷新</el-button>
      </div>
    </el-card>

    <!-- KPI 卡片区 -->
    <div class="kpi-grid" v-loading="loading">
      <div
        v-for="kpi in kpiCards"
        :key="kpi.key"
        class="kpi-grid-item"
      >
        <div class="kpi-card" :style="{ background: kpi.gradient }">
          <div class="kpi-icon-wrap">
            <span class="kpi-emoji">{{ kpi.icon }}</span>
          </div>
          <div class="kpi-body">
            <div class="kpi-label">{{ kpi.label }}</div>
            <div class="kpi-value">{{ kpi.value }}</div>
            <div class="kpi-trend" v-if="kpi.trend !== null">
              <span :class="kpi.trend >= 0 ? 'trend-up' : 'trend-down'">
                {{ kpi.trend >= 0 ? '📈' : '📉' }} {{ Math.abs(kpi.trend) }}%
              </span>
              <span class="trend-label">较上月</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-grid-layout">
      <el-card shadow="hover" class="chart-card">
        <template #header>
          <div class="phead header">
            <span class="header-icon">⚙️</span>
            <div class="title">本月维修费用构成</div>
            <div class="head-btn-group">
              <span v-if="maintenanceTotal > 0" class="header-sub">合计 ¥{{ maintenanceTotal.toLocaleString() }}</span>
            </div>
          </div>
        </template>
        <div ref="maintenanceChartRef" style="height:280px"></div>
      </el-card>

      <el-card shadow="hover" class="chart-card">
        <template #header>
          <div class="phead header">
            <span class="header-icon">📊</span>
            <div class="title">备件消耗趋势（近6月）</div>
          </div>
        </template>
        <div ref="trendChartRef" style="height:280px"></div>
      </el-card>
    </div>

    <!-- 库存预警表格 -->
    <el-card shadow="hover" class="warning-table-card" v-if="lowStockList.length > 0">
      <template #header>
        <div class="phead header">
          <span class="header-icon">⚠️</span>
          <div class="title">库存预警</div>
          <div class="head-btn-group">
            <el-badge :value="lowStockList.length" type="danger" style="margin-right: 15px;"></el-badge>
            <el-button size="small" class="header-link" @click="goToWarning">查看全部 →</el-button>
          </div>
        </div>
      </template>
      <el-table :data="lowStockList.slice(0, 10)" size="small" border stripe>
        <el-table-column prop="severity" label="紧急程度" width="90" align="center">
          <template #default="scope">
            <el-tag
              :type="scope.row.severity === '紧急' ? 'danger' : 'warning'"
              size="small"
            >{{ scope.row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="预警标题" min-width="160"></el-table-column>
        <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip></el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default>
            <el-button size="small" @click="goToWarning">去处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { useDashboardStore } from '@/store/dashboard'
import { ElMessage } from 'element-plus'

interface KpiCard {
  key: string
  label: string
  value: string
  icon: string
  gradient: string
  trend: number | null
}

interface WarningItem {
  severity: string
  title: string
  detail: string
}

const GRADIENTS = [
  'linear-gradient(135deg, #1890ff 0%, #096dd9 100%)',
  'linear-gradient(135deg, #52c41a 0%, #389e0d 100%)',
  'linear-gradient(135deg, #fa8c16 0%, #d46b08 100%)',
  'linear-gradient(135deg, #f5222d 0%, #cf1322 100%)',
  'linear-gradient(135deg, #722ed1 0%, #531dab 100%)',
]

const router = useRouter()
const dashboardStore = useDashboardStore()
const { selectedMonth } = storeToRefs(dashboardStore)

const loading = ref(false)
const kpiCards = ref<KpiCard[]>([])
const maintenanceTotal = ref(0)
const lowStockList = ref<WarningItem[]>([])
const todoBar = ref({ total: 0, lowStock: 0, overdueWO: 0, overduePO: 0 })

// 图表 DOM 与实例 Ref
const maintenanceChartRef = ref<HTMLDivElement | null>(null)
const trendChartRef = ref<HTMLDivElement | null>(null)
let maintenanceChartInst: echarts.ECharts | null = null
let trendChartInst: echarts.ECharts | null = null

function currentMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function prevMonth(ym: string) {
  const [y, m] = ym.split('-').map(Number)
  const d = new Date(y, m - 2, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

async function loadAll() {
  loading.value = true
  await Promise.all([loadKpi(), loadWarnings(), renderCharts()])
  loading.value = false
}

async function handleMonthChange() {
  await loadAll()
}

async function handleRefresh() {
  await loadAll()
}

async function loadKpi() {
  try {
    const ym = selectedMonth.value || currentMonth()
    const prev = prevMonth(ym)
    const [curRes, prevRes] = await Promise.all([
      request.get('/report/kpi', { params: { yearMonth: ym } }),
      request.get('/report/kpi', { params: { yearMonth: prev } }),
    ])
    const k = curRes.data || {}
    const p = prevRes.data || {}

    const calcTrend = (cur: number, pre: number) => {
      if (!pre || pre === 0) return null
      return Math.round(((cur - pre) / pre) * 100)
    }

    kpiCards.value = [
      {
        key: 'inventory',
        label: '库存总金额',
        value: '¥ ' + (k.totalInventoryAmount || 0).toLocaleString(),
        icon: '📦',
        gradient: GRADIENTS[0],
        trend: calcTrend(k.totalInventoryAmount, p.totalInventoryAmount),
      },
      {
        key: 'turnover',
        label: '库存周转率',
        value: (k.inventoryTurnoverRate || 0) + ' 次/年',
        icon: '🔄',
        gradient: GRADIENTS[1],
        trend: calcTrend(k.inventoryTurnoverRate, p.inventoryTurnoverRate),
      },
      {
        key: 'purchase',
        label: '当月采购额',
        value: '¥ ' + (k.monthPurchaseAmount || 0).toLocaleString(),
        icon: '🛒',
        gradient: GRADIENTS[2],
        trend: calcTrend(k.monthPurchaseAmount, p.monthPurchaseAmount),
      },
      {
        key: 'repair',
        label: '当月维修费用',
        value: '¥ ' + (k.monthRepairCost || 0).toLocaleString(),
        icon: '⚙️',
        gradient: GRADIENTS[3],
        trend: calcTrend(k.monthRepairCost, p.monthRepairCost),
      },
      {
        key: 'avail',
        label: '设备可用率',
        value: (k.equipmentAvailability || 0) + ' %',
        icon: '📊',
        gradient: GRADIENTS[4],
        trend: calcTrend(k.equipmentAvailability, p.equipmentAvailability),
      },
    ]
  } catch (_) {
    ElMessage.error('加载 KPI 数据失败')
  }
}

async function loadWarnings() {
  try {
    const res = await request.get('/warnings')
    const data = res.data || {}
    const low = (data.lowStock || []).length
    const wo = (data.overdueWO || []).length
    const po = (data.overduePO || []).length
    todoBar.value = { total: low + wo + po, lowStock: low, overdueWO: wo, overduePO: po }
    lowStockList.value = data.lowStock || []
  } catch (_) {
    // 预警数据非关键，忽略失败
  }
}

async function renderCharts() {
  try {
    const ym = selectedMonth.value || currentMonth()
    const [mRes, tRes] = await Promise.all([
      request.get('/report/maintenance/cost-by-month', { params: { months: 1, yearMonth: ym } }),
      request.get('/report/consumption/trend', { params: { months: 6 } }),
    ])

    // 维修费用环形图
    const mData = (mRes.data || [])[0] || {}
    const partCost = Number(mData.partCost || 0)
    const laborCost = Number(mData.laborCost || 0)
    const outsourceCost = Number(mData.outsourceCost || 0)
    maintenanceTotal.value = partCost + laborCost + outsourceCost

    nextTick(() => {
      if (maintenanceChartRef.value) {
        if (maintenanceChartInst) maintenanceChartInst.dispose()
        maintenanceChartInst = echarts.init(maintenanceChartRef.value)
        maintenanceChartInst.setOption({
          color: ['#5470c6', '#91cc75', '#fac858'],
          tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
          legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 12 } },
          series: [{
            type: 'pie',
            radius: ['38%', '62%'],
            center: ['50%', '44%'],
            avoidLabelOverlap: true,
            label: { show: true, formatter: '{b}\n¥{c}' },
            emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
            data: [
              { value: partCost, name: '备件费' },
              { value: laborCost, name: '人工费' },
              { value: outsourceCost, name: '外协费' },
            ],
          }],
          graphic: [{
            type: 'text',
            left: 'center',
            top: '40%',
            style: {
              text: '¥' + maintenanceTotal.value.toLocaleString(),
              textAlign: 'center',
              fill: '#333',
              fontSize: 14,
              fontWeight: 'bold',
            },
          }],
        })
      }

      // 消耗趋势渐变面积图
      const rows = tRes.data || []
      if (trendChartRef.value) {
        if (trendChartInst) trendChartInst.dispose()
        trendChartInst = echarts.init(trendChartRef.value)
        trendChartInst.setOption({
          tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
          grid: { left: 40, right: 20, top: 20, bottom: 30 },
          xAxis: {
            type: 'category',
            data: rows.map((r: any) => r.month),
            axisLine: { lineStyle: { color: '#ddd' } },
            axisTick: { show: false },
          },
          yAxis: {
            type: 'value',
            splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } },
            axisLine: { show: false },
            axisTick: { show: false },
          },
          series: [{
            name: '消耗数量',
            type: 'line',
            smooth: true,
            symbol: 'circle',
            symbolSize: 7,
            lineStyle: { width: 3, color: '#1890ff' },
            itemStyle: { color: '#1890ff', borderWidth: 2, borderColor: '#fff' },
            label: { show: true, position: 'top', fontSize: 11, color: '#555' },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(24,144,255,0.35)' },
                { offset: 1, color: 'rgba(24,144,255,0.02)' },
              ]),
            },
            data: rows.map((r: any) => r.totalQty),
          }],
        })
      }
    })
  } catch (_) {
    // 图表数据非关键
  }
}

function resizeCharts() {
  if (maintenanceChartInst) maintenanceChartInst.resize()
  if (trendChartInst) trendChartInst.resize()
}

function goToWarning() {
  router.push('/home/warning-center')
}

// 侦听全局月份选择变化
watch(selectedMonth, () => {
  loadAll()
})

onMounted(() => {
  if (!selectedMonth.value) {
    selectedMonth.value = currentMonth()
  }
  loadAll()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  if (maintenanceChartInst) maintenanceChartInst.dispose()
  if (trendChartInst) trendChartInst.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px 24px;
  background: #f5f7fa;
  min-height: 100%;
}

/* 待办摘要条 */
.todo-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 6px;
  padding: 8px 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.todo-icon { font-size: 16px; }
.todo-text { font-size: 13px; color: #595959; font-weight: 600; }
.todo-tag { margin: 0 2px; }
.todo-link { margin-left: 4px; padding: 0; }

/* 筛选栏 */
.filter-card { margin-bottom: 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; }
.filter-icon { font-size: 16px; }
.filter-label { font-size: 14px; font-weight: 600; color: #333; white-space: nowrap; }

/* KPI 卡片 CSS Grid Layout */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.kpi-grid-item {
  min-width: 0;
}
@media (max-width: 1400px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 992px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 576px) {
  .kpi-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }
}

.kpi-card {
  border-radius: 10px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #fff;
  min-height: 100px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  transition: transform 0.2s, box-shadow 0.2s;
}
.kpi-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.18);
}
.kpi-icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.kpi-emoji { font-size: 26px; }
.kpi-body { flex: 1; min-width: 0; }
.kpi-label { font-size: 12px; color: rgba(255,255,255,0.85); margin-bottom: 4px; }
.kpi-value { font-size: 18px; font-weight: 700; line-height: 1.2; word-break: break-all; }
.kpi-trend { margin-top: 4px; font-size: 12px; display: flex; align-items: center; gap: 3px; }
.trend-up { color: #d4f7d4; font-weight: bold; }
.trend-down { color: #ffe0e0; font-weight: bold; }
.trend-label { color: rgba(255,255,255,0.7); }

/* 图表区 - CSS Grid 现代自适应布局 */
.chart-grid-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
@media (max-width: 992px) {
  .chart-grid-layout {
    grid-template-columns: 1fr;
  }
}

.chart-card { border-radius: 8px; }
.header-sub { font-size: 12px; color: #999; font-weight: 400; margin-left: auto; }
.header-link { margin-left: auto; padding: 0; }

/* 预警表格 */
.warning-table-card { border-radius: 8px; }
</style>
