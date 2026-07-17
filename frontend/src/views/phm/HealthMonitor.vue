<template>
  <div class="page-container health-monitor-page">
    <!-- ============================================================
         顶部统计卡片区域
         ============================================================ -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card stat-total" shadow="hover">
          <div class="stat-icon"><i class="el-icon-s-data" /></div>
          <div class="stat-content">
            <div class="stat-label">设备总数</div>
            <div class="stat-value">{{ dashboard.totalDevices || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-healthy" shadow="hover">
          <div class="stat-icon"><i class="el-icon-success" /></div>
          <div class="stat-content">
            <div class="stat-label">健康设备</div>
            <div class="stat-value">{{ dashboard.riskDistribution?.LOW || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-warning" shadow="hover">
          <div class="stat-icon"><i class="el-icon-warning" /></div>
          <div class="stat-content">
            <div class="stat-label">预警设备</div>
            <div class="stat-value">{{ (dashboard.riskDistribution?.MEDIUM || 0) + (dashboard.riskDistribution?.HIGH || 0) }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-critical" shadow="hover">
          <div class="stat-icon"><i class="el-icon-error" /></div>
          <div class="stat-content">
            <div class="stat-label">严重风险</div>
            <div class="stat-value">{{ dashboard.riskDistribution?.CRITICAL || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============================================================
         操作栏：手动触发评估 + 自动刷新
         ============================================================ -->
    <div class="toolbar-row">
      <div class="toolbar-left">
        <span class="avg-score-label">平均健康分：</span>
        <el-tag type="info" size="default">{{ formatDecimal(dashboard.avgHealthScore) }} 分</el-tag>
      </div>
      <div class="toolbar-right">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          inactive-text=""
          style="margin-right: 10px"
        />
        <el-button
          v-if="hasPermission('phm:health:evaluate')"
          size="small"
          :loading="triggering"
          @click="handleTriggerEvaluation"
        >
          手动触发评估
        </el-button>
      </div>
    </div>

    <!-- ============================================================
         风险设备排行榜
         ============================================================ -->
    <el-card class="ranking-card" shadow="never">
      <template #header>
        <div class="phead header">
          <i class="el-icon-s-data" />
          <div class="title">风险设备排行榜</div>
          <div class="head-btn-group">
            <span class="card-tip">（按健康评分升序，最多显示20台）</span>
          </div>
        </div>
      </template>

      <el-table v-loading="rankingLoading" :data="rankingData" border stripe style="width:100%">
        <el-table-column type="index" label="排名" width="60" align="center" />
        <el-table-column prop="deviceCode" label="设备编码" width="120" sortable="custom" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="deviceModel" label="设备型号" width="120" show-overflow-tooltip />

        <el-table-column label="健康评分" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: getScoreColor(row.healthScore) }">
              {{ formatDecimal(row.healthScore) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getRiskTagType(row.riskLevel)" size="small">
              {{ getRiskLevelText(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="recordDate" label="评估日期" width="120" align="center" />

        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="viewTrend(row.deviceId)">
              查看趋势
            </el-button>
            <el-button size="small" @click="viewDetails(row.deviceId)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ============================================================
         健康趋势图（对话框）
         ============================================================ -->
    <el-dialog
      v-model="trendDialogVisible"
      :title="'设备健康趋势 - ' + selectedDeviceName"
      width="900px"
      @close="closeTrendDialog"
    >
      <div v-if="trendLoading" class="chart-placeholder">
        <i class="el-icon-loading" /> 加载中...
      </div>
      <div v-else-if="!echartsAvailable" class="chart-placeholder">
        <i class="el-icon-info" /> ECharts 未安装，请执行 npm install
      </div>
      <div v-else ref="trendChartRef" class="trend-chart" />
    </el-dialog>

    <!-- ============================================================
         设备详情对话框
         ============================================================ -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="'设备健康详情 - ' + selectedDeviceName"
      width="600px"
    >
      <div v-if="detailLoading" class="dialog-loading">
        <i class="el-icon-loading" /> 加载中...
      </div>
      <div v-else-if="deviceDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备编码">{{ deviceDetail.deviceCode }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ deviceDetail.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="设备型号">{{ deviceDetail.deviceModel }}</el-descriptions-item>
          <el-descriptions-item label="设备重要性">
            <el-tag :type="deviceDetail.importanceLevel === 'CRITICAL' ? 'danger' : deviceDetail.importanceLevel === 'IMPORTANT' ? 'warning' : 'info'" size="small">
              {{ getImportanceLevelText(deviceDetail.importanceLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="健康评分">
            <span :style="{ color: getScoreColor(deviceDetail.healthScore), fontSize: '18px', fontWeight: 'bold' }">
              {{ formatDecimal(deviceDetail.healthScore) }} 分
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="getRiskTagType(deviceDetail.riskLevel)" size="default">
              {{ getRiskLevelText(deviceDetail.riskLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="运行时长评分">{{ formatDecimal(deviceDetail.runtimeScore) }}</el-descriptions-item>
          <el-descriptions-item label="故障评分">{{ formatDecimal(deviceDetail.faultScore) }}</el-descriptions-item>
          <el-descriptions-item label="工单评分">{{ formatDecimal(deviceDetail.workorderScore) }}</el-descriptions-item>
          <el-descriptions-item label="换件评分">{{ formatDecimal(deviceDetail.replacementScore) }}</el-descriptions-item>
          <el-descriptions-item label="评估日期">{{ deviceDetail.recordDate }}</el-descriptions-item>
          <el-descriptions-item label="算法版本">{{ deviceDetail.algorithmVersion }}</el-descriptions-item>
        </el-descriptions>

        <!-- 雷达图：维度评分 -->
        <div v-if="echartsAvailable" style="margin-top: 20px;">
          <h4 style="margin-bottom: 10px;">维度评分雷达图</h4>
          <div ref="radarChartRef" class="radar-chart" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { ElMessage, ElMessageBox } from 'element-plus'

const authStore = useAuthStore()

const dashboard = ref<any>({
  totalDevices: 0,
  riskDistribution: {},
  avgHealthScore: 0,
  recentAlerts: []
})

const rankingData = ref<any[]>([])
const rankingLoading = ref(false)

const triggering = ref(false)
const autoRefresh = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const echartsAvailable = ref(false)
let echartsLib: any = null
let trendChart: any = null
let radarChart: any = null
const trendChartRef = ref<HTMLDivElement | null>(null)
const radarChartRef = ref<HTMLDivElement | null>(null)

const trendDialogVisible = ref(false)
const trendData = ref<any[]>([])
const trendLoading = ref(false)
const selectedDeviceId = ref<any>(null)
const selectedDeviceName = ref('')

const detailDialogVisible = ref(false)
const deviceDetail = ref<any>(null)
const detailLoading = ref(false)

const permissions = computed(() => authStore.permissions || [])

function hasPermission(perm: string) {
  return permissions.value.includes(perm)
}

async function fetchDashboard() {
  try {
    const res = await request.get('/phm/health/dashboard')
    if (res.data.code === 200) {
      dashboard.value = res.data.data || {}
    }
  } catch (e) {
    ElMessage.error('获取Dashboard数据失败')
  }
}

async function fetchRanking() {
  rankingLoading.value = true
  try {
    const res = await request.get('/phm/health/ranking', {
      params: { limit: 20 }
    })
    if (res.data.code === 200) {
      rankingData.value = res.data.data || []
    }
  } catch (e) {
    ElMessage.error('获取风险设备排行榜失败')
  } finally {
    rankingLoading.value = false
  }
}

async function fetchTrend(deviceId: any) {
  trendLoading.value = true
  try {
    const endDate = new Date().toISOString().split('T')[0]
    const startDate = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]

    const res = await request.get(`/phm/health/trend/${deviceId}`, {
      params: { startDate, endDate }
    })
    if (res.data.code === 200) {
      trendData.value = res.data.data || []
      nextTick(() => {
        renderTrendChart()
      })
    }
  } catch (e) {
    ElMessage.error('获取健康趋势失败')
  } finally {
    trendLoading.value = false
  }
}

async function fetchDeviceDetail(deviceId: any) {
  detailLoading.value = true
  try {
    const res = await request.get(`/phm/health/device/${deviceId}/latest`)
    if (res.data.code === 200) {
      deviceDetail.value = res.data.data
    }
  } catch (e) {
    ElMessage.error('获取设备详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function initECharts() {
  try {
    const echarts = await import('echarts')
    echartsLib = echarts
    echartsAvailable.value = true
  } catch (e) {
    echartsAvailable.value = false
  }
}

function renderTrendChart() {
  if (!echartsAvailable.value || !trendChartRef.value) return

  if (!trendChart) {
    trendChart = echartsLib.init(trendChartRef.value)
  }

  const dates = trendData.value.map(item => item.recordDate)
  const scores = trendData.value.map(item => item.healthScore)

  const option = {
    title: { text: '健康评分趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { top: '15%', left: '10%', right: '10%', bottom: '15%' },
    xAxis: {
      type: 'category',
      data: dates,
      name: '日期'
    },
    yAxis: {
      type: 'value',
      name: '健康评分',
      min: 0,
      max: 100
    },
    series: [{
      type: 'line',
      data: scores,
      smooth: true,
      lineStyle: { width: 3 },
      areaStyle: { opacity: 0.3 },
      markLine: {
        data: [
          { yAxis: 40, name: 'CRITICAL阈值', lineStyle: { color: '#f56c6c' } },
          { yAxis: 60, name: 'HIGH阈值', lineStyle: { color: '#e6a23c' } },
          { yAxis: 80, name: 'MEDIUM阈值', lineStyle: { color: '#409eff' } }
        ]
      }
    }]
  }

  trendChart.setOption(option)
}

function renderRadarChart() {
  if (!echartsAvailable.value || !radarChartRef.value || !deviceDetail.value) return

  if (radarChart) {
    radarChart.dispose()
  }
  radarChart = echartsLib.init(radarChartRef.value)

  const option = {
    tooltip: {},
    radar: {
      indicator: [
        { name: '运行时长评分', max: 100 },
        { name: '故障评分', max: 100 },
        { name: '工单评分', max: 100 },
        { name: '换件评分', max: 100 }
      ]
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          deviceDetail.value.runtimeScore || 0,
          deviceDetail.value.faultScore || 0,
          deviceDetail.value.workorderScore || 0,
          deviceDetail.value.replacementScore || 0
        ],
        name: '维度评分'
      }],
      areaStyle: { opacity: 0.3 }
    }]
  }

  radarChart.setOption(option)
}

function resizeCharts() {
  if (trendChart) trendChart.resize()
  if (radarChart) radarChart.resize()
}

function handleTriggerEvaluation() {
  ElMessageBox.confirm(
    '确认手动触发批量健康评估？将对所有设备进行评估',
    '确认触发',
    { type: 'warning' }
  ).then(async () => {
    triggering.value = true
    try {
      const res = await request.post('/phm/health/batch-evaluate')
      if (res.data.code === 200) {
        ElMessage.success(res.data.message || '评估任务已启动')
        setTimeout(() => {
          fetchDashboard()
          fetchRanking()
        }, 2000)
      }
    } catch (e: any) {
      ElMessage.error('触发失败：' + (e.response?.data?.message || '未知错误'))
    } finally {
      triggering.value = false
    }
  }).catch(() => {})
}

function viewTrend(deviceId: any) {
  const device = rankingData.value.find(d => d.deviceId === deviceId)
  selectedDeviceId.value = deviceId
  selectedDeviceName.value = device ? `${device.deviceCode} - ${device.deviceName}` : ''
  trendDialogVisible.value = true
  fetchTrend(deviceId)
}

function closeTrendDialog() {
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
}

function viewDetails(deviceId: any) {
  const device = rankingData.value.find(d => d.deviceId === deviceId)
  selectedDeviceId.value = deviceId
  selectedDeviceName.value = device ? `${device.deviceCode} - ${device.deviceName}` : ''
  detailDialogVisible.value = true
  fetchDeviceDetail(deviceId)
}

function startAutoRefresh() {
  refreshTimer = setInterval(() => {
    fetchDashboard()
    fetchRanking()
  }, 5 * 60 * 1000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function formatDecimal(val: any, digits = 2) {
  return val != null ? Number(val).toFixed(digits) : '0.00'
}

function getScoreColor(score: number) {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#409eff'
  if (score >= 40) return '#e6a23c'
  return '#f56c6c'
}

function getRiskTagType(riskLevel: string) {
  const map: Record<string, string> = {
    CRITICAL: 'danger',
    HIGH: 'warning',
    MEDIUM: 'info',
    LOW: 'success'
  }
  return map[riskLevel] || ''
}

function getRiskLevelText(riskLevel: string) {
  const map: Record<string, string> = {
    CRITICAL: '严重',
    HIGH: '高风险',
    MEDIUM: '中等',
    LOW: '健康'
  }
  return map[riskLevel] || riskLevel
}

function getImportanceLevelText(level: string) {
  const map: Record<string, string> = {
    CRITICAL: '关键设备',
    IMPORTANT: '重要设备',
    NORMAL: '一般设备'
  }
  return map[level] || level
}

watch(autoRefresh, (val) => {
  if (val) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
})

watch(detailDialogVisible, (val) => {
  if (val && echartsAvailable.value && deviceDetail.value) {
    nextTick(() => {
      renderRadarChart()
    })
  }
})

watch(deviceDetail, (val) => {
  if (detailDialogVisible.value && echartsAvailable.value && val) {
    nextTick(() => {
      renderRadarChart()
    })
  }
})

onMounted(() => {
  fetchDashboard()
  fetchRanking()
  initECharts()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  stopAutoRefresh()
  window.removeEventListener('resize', resizeCharts)
  if (trendChart) trendChart.dispose()
  if (radarChart) radarChart.dispose()
})
</script>

<style scoped>
.health-monitor-page {
  padding: 20px;
}

/* 统计卡片 */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
  cursor: pointer;
  transition: transform 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
}

.stat-icon {
  font-size: 48px;
  margin-right: 20px;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-total .stat-icon { color: #409eff; }
.stat-total .stat-value { color: #409eff; }

.stat-healthy .stat-icon { color: #67c23a; }
.stat-healthy .stat-value { color: #67c23a; }

.stat-warning .stat-icon { color: #e6a23c; }
.stat-warning .stat-value { color: #e6a23c; }

.stat-critical .stat-icon { color: #f56c6c; }
.stat-critical .stat-value { color: #f56c6c; }

/* 工具栏 */
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.avg-score-label {
  font-size: 14px;
  color: #606266;
  margin-right: 10px;
}

/* 卡片 */
.ranking-card {
  margin-bottom: 20px;
}

.card-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 10px;
}

/* 图表 */
.trend-chart {
  width: 100%;
  height: 400px;
}

.radar-chart {
  width: 100%;
  height: 300px;
}

.chart-placeholder {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 16px;
}

.dialog-loading {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}
</style>
