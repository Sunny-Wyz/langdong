<template>
  <div class="page-container ai-forecast-container">
    <el-card shadow="hover">
      <template #header>
        <div class="phead header">
          <span class="header-icon">📊</span>
          <div class="title">需求预测结果</div>
          <span style="margin-left: 12px; font-size: 12px; color: #909399; font-weight: normal;">
            月特征来自领用月汇总现算（两阶段 Hurdle-Gamma），非「训练数据看板」日表
          </span>
          <div class="head-btn-group">
            <el-button type="primary" link @click="goJobCenter" v-if="hasJobCenterPermission">任务中心</el-button>
            <el-button type="primary" link :loading="triggeringForecast" @click="triggerForecast" v-if="hasTriggerPermission">手动触发重算（下月·Hurdle-Gamma）</el-button>
          </div>
        </div>
      </template>

      <div v-if="hasProgressPermission && runStatus" style="margin-bottom: 14px;">
        <el-alert
          :title="runStatusTitle"
          :type="runStatusAlertType"
          :closable="false"
          show-icon
        >
          <div v-if="isRunActive" style="margin-top: 8px;">
            <el-progress :percentage="runStatus.percent || 0" :stroke-width="14" />
            <div style="font-size: 12px; color: #606266; margin-top: 4px;">
              阶段：{{ runStatus.stage || '-' }}
              ｜处理：{{ runStatus.processed || 0 }}/{{ runStatus.total || 0 }}
              ｜失败：{{ runStatus.failed || 0 }}
            </div>
          </div>
        </el-alert>
      </div>

      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form" size="default">
        <el-form-item label="预测目标月份">
          <el-date-picker v-model="searchForm.month" type="month" placeholder="选择月份" value-format="YYYY-MM"
            clearable>
          </el-date-picker>
        </el-form-item>
        <el-form-item label="备件编码">
          <el-input v-model="searchForm.partCode" placeholder="输入备件编码或名称" clearable></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">🔍 查询</el-button>
          <el-button @click="resetSearch">🔄 重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border style="width: 100%" v-loading="loading">
        <el-table-column prop="partCode" label="备件编码" width="120" sortable="custom"></el-table-column>
        <el-table-column prop="partName" label="备件名称" min-width="150" show-overflow-tooltip></el-table-column>
        <el-table-column prop="forecastMonth" label="预测月份" width="100"></el-table-column>
        <el-table-column prop="algoType" label="算法" width="180">
          <template #default="scope">
            <el-tag :type="getAlgoTagType(scope.row.algoType)" size="small">
              {{ getAlgoDisplayName(scope.row.algoType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="predictQty" label="预测消耗量" width="120">
          <template #default="scope">
            <span style="font-weight: bold; color: #409EFF">{{ scope.row.predictQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="demand3Months" label="未来3个月累计需求" width="150">
          <template #default="scope">
            <span style="font-weight: bold; color: #67C23A">{{ scope.row.demand3Months ?? 'N/A' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="90% 置信区间" width="150">
          <template #default="scope">
            [ {{ scope.row.lowerBound }} , {{ scope.row.upperBound }} ]
          </template>
        </el-table-column>
        <el-table-column prop="occurrenceProb" label="发生概率 (pt)" width="110">
          <template #default="scope">
            {{ scope.row.occurrenceProb != null ? (scope.row.occurrenceProb * 100).toFixed(1) + '%' : 'N/A' }}
          </template>
        </el-table-column>
        <el-table-column prop="positiveQty" label="正需求均值 (ŷt)" width="120">
          <template #default="scope">
            {{ scope.row.positiveQty ?? 'N/A' }}
          </template>
        </el-table-column>
        <el-table-column prop="leadTimeQuantile" label="提前期需求分位数" width="140">
          <template #default="scope">
            {{ scope.row.leadTimeQuantile ?? 'N/A' }}
          </template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="安全库存 (SS)" width="110">
          <template #default="scope">
            <span :style="{ fontWeight: 'bold', color: scope.row.safetyStock > 0 ? '#E6A23C' : '#909399' }">
              {{ scope.row.safetyStock ?? 'N/A' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="reorderPoint" label="补货触发点 (ROP)" width="130">
          <template #default="scope">
            <span :style="{ fontWeight: 'bold', color: scope.row.reorderPoint > 0 ? '#E6A23C' : '#909399' }">
              {{ scope.row.reorderPoint ?? 'N/A' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="mase" label="MASE指标" width="100">
          <template #default="scope">
            <span :style="{ color: (scope.row.mase && scope.row.mase > 1.0) ? '#F56C6C' : '#67C23A' }">
              {{ scope.row.mase || 'N/A' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="计算时间" width="160"></el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button @click="showTrend(scope.row)" type="primary" link size="small">📈 历史趋势</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange"
          :current-page="page" :page-sizes="[10, 20, 50, 100]" :page-size="size"
          layout="total, sizes, prev, pager, next, jumper" :total="total">
        </el-pagination>
      </div>
    </el-card>

    <!-- 趋势图弹窗 -->
    <el-dialog :title="chartTitle" v-model="chartVisible" width="70%" @opened="renderChart">
      <div ref="trendChartRef" style="width: 100%; height: 400px;" v-loading="chartLoading"></div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import request from '@/utils/request'
import * as echarts from 'echarts'
import { useAuthStore } from '@/store/auth'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'

interface SearchForm {
  month: string
  partCode: string
}

interface RunStatus {
  status: string
  percent?: number
  stage?: string
  processed?: number
  total?: number
  failed?: number
  message?: string
}

interface ForecastItem {
  partCode: string
  partName: string
  forecastMonth: string
  algoType: string
  predictQty: number
  demand3Months?: number
  lowerBound: number
  upperBound: number
  occurrenceProb?: number
  positiveQty?: number
  leadTimeQuantile?: number
  safetyStock?: number
  reorderPoint?: number
  mase?: number
  createTime: string
}

const authStore = useAuthStore()
const router = useRouter()

const searchForm = reactive<SearchForm>({ month: '', partCode: '' })
const tableData = ref<ForecastItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

// 图表和弹窗相关
const chartVisible = ref(false)
const chartLoading = ref(false)
const chartTitle = ref('预测趋势分析')
const trendChartRef = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null
const currentChartData = ref<ForecastItem[]>([])

const triggeringForecast = ref(false)
const runStatus = ref<RunStatus | null>(null)
let progressPollTimer: ReturnType<typeof setInterval> | null = null

// 权限判定
const hasTriggerPermission = computed(() => {
  const permissions = authStore.permissions || []
  const username = authStore.username
  return permissions.includes('ai:forecast:trigger') || username === 'admin'
})

const hasJobCenterPermission = computed(() => {
  const permissions = authStore.permissions || []
  const username = authStore.username
  return permissions.includes('ai:forecast:list') || username === 'admin'
})

const hasProgressPermission = computed(() => {
  const permissions = authStore.permissions || []
  const username = authStore.username
  return permissions.includes('ai:forecast:list') || permissions.includes('ai:forecast:trigger') || username === 'admin'
})

const isRunActive = computed(() => {
  return runStatus.value && runStatus.value.status === 'RUNNING'
})

const runStatusTitle = computed(() => {
  if (!runStatus.value) return ''
  if (runStatus.value.status === 'RUNNING') return runStatus.value.message || '重算任务执行中'
  if (runStatus.value.status === 'SUCCESS') return runStatus.value.message || '重算任务已完成'
  if (runStatus.value.status === 'FAILED') return runStatus.value.message || '重算任务执行失败'
  return runStatus.value.message || '暂无运行中的重算任务'
})

type AlertType = 'info' | 'warning' | 'success' | 'error'

const runStatusAlertType = computed<AlertType>(() => {
  if (!runStatus.value) return 'info'
  if (runStatus.value.status === 'RUNNING') return 'warning'
  if (runStatus.value.status === 'SUCCESS') return 'success'
  if (runStatus.value.status === 'FAILED') return 'error'
  return 'info'
})

function fetchData() {
  loading.value = true
  request.get('/ai/forecast/result', {
    params: {
      month: searchForm.month,
      partCode: searchForm.partCode,
      page: page.value,
      size: size.value
    }
  }).then(res => {
    if (res.data) {
      tableData.value = res.data.list || []
      total.value = res.data.total || 0
    }
  }).finally(() => {
    loading.value = false
  })
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function resetSearch() {
  searchForm.month = ''
  searchForm.partCode = ''
  handleSearch()
}

function handleSizeChange(val: number) {
  size.value = val
  page.value = 1
  fetchData()
}

function handleCurrentChange(val: number) {
  page.value = val
  fetchData()
}

function triggerForecast() {
  ElMessageBox.confirm('此操作将启动全量备件特征分析和算法预测任务，该任务耗时较长，是否继续？', '手动触发', {
    confirmButtonText: '确定启动',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    triggeringForecast.value = true
    request.post('/ai/forecast/trigger').then(res => {
      if (res && res.data && res.data.runStatus) {
        runStatus.value = res.data.runStatus
      }
      ElMessage.success(res.data.message || '重算任务已启动')
      startProgressPolling()
    }).catch(error => {
      const msg = error.response?.data?.message || '触发重算失败'
      ElMessage.error(msg)
    }).finally(() => {
      triggeringForecast.value = false
    })
  }).catch(() => {})
}

function fetchRunStatus(silent = true) {
  if (!hasProgressPermission.value) {
    return Promise.resolve()
  }
  return request.get('/ai/forecast/trigger/status')
    .then(res => {
      runStatus.value = res.data || null
      if (isRunActive.value && !progressPollTimer) {
        startProgressPolling()
      }
      if (!isRunActive.value) {
        stopProgressPolling()
        if (runStatus.value && runStatus.value.status === 'SUCCESS') {
          fetchData()
        }
      }
    })
    .catch(error => {
      if (error.response?.status === 403) {
        stopProgressPolling()
        return
      }
      if (!silent) {
        const msg = error.response?.data?.message || '获取重算进度失败'
        ElMessage.error(msg)
      }
    })
}

function startProgressPolling() {
  if (progressPollTimer) return
  progressPollTimer = setInterval(() => {
    fetchRunStatus(true)
  }, 3000)
  fetchRunStatus(true)
}

function stopProgressPolling() {
  if (!progressPollTimer) return
  clearInterval(progressPollTimer)
  progressPollTimer = null
}

function goJobCenter() {
  router.push('/ai/job-center')
}

type TagType = 'success' | 'warning' | 'info' | ''

function getAlgoTagType(algo: string): TagType {
  if (algo === 'TWO_STAGE') return 'success'
  if (algo === 'RF') return 'success'
  if (algo === 'SBA') return 'warning'
  if (algo === 'FALLBACK') return 'info'
  return ''
}

function getAlgoDisplayName(algo: string) {
  if (algo === 'TWO_STAGE') return '两阶段 Hurdle-Gamma'
  if (algo === 'RF') return '随机森林 (RF)'
  if (algo === 'SBA') return 'SBA 算法'
  if (algo === 'FALLBACK') return '数据不足回退'
  return algo || '未知算法'
}

function normalizeHistoryPayload(payload: any): ForecastItem[] {
  if (Array.isArray(payload)) {
    return payload
  }
  if (payload && Array.isArray(payload.list)) {
    return payload.list
  }
  if (payload && Array.isArray(payload.data)) {
    return payload.data
  }
  return []
}

function toNum(value: any): number | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

function showTrend(row: ForecastItem) {
  chartTitle.value = `[${row.partCode}] ${row.partName || row.partCode} - 预测趋势（含 90% 上下界）`
  chartVisible.value = true
  chartLoading.value = true

  request.get(`/ai/forecast/result/${row.partCode}`).then(res => {
    const list = normalizeHistoryPayload(res.data)
    // 兼容字段缺失：至少保证当前行可画
    currentChartData.value = list.length > 0 ? list : [row]
  }).catch(() => {
    console.warn('历史趋势接口失败，回退为当前行')
    currentChartData.value = [row]
  }).finally(() => {
    chartLoading.value = false
    if (chartVisible.value) {
      nextTick(() => {
        renderChart()
      })
    }
  })
}

function renderChart() {
  const dom = trendChartRef.value
  if (!dom || !currentChartData.value) return

  if (chartInstance) {
    chartInstance.dispose()
  }
  chartInstance = echarts.init(dom)

  // 按月份升序，避免乱序折线
  const data = [...currentChartData.value].sort((a, b) =>
    String(a.forecastMonth || '').localeCompare(String(b.forecastMonth || ''))
  )

  const xData = data.map(item => item.forecastMonth)
  const predictData = data.map(item => toNum(item.predictQty))
  const lowerData = data.map(item => toNum(item.lowerBound))
  const upperData = data.map(item => toNum(item.upperBound))

  // ECharts 置信带标准画法：先画下界（透明），再画 (上界-下界) 并 stack，形成上下界之间的色带
  const bandBase = lowerData.map(v => (v == null ? null : v))
  const bandHeight = data.map((_, i) => {
    const lo = lowerData[i]
    const hi = upperData[i]
    if (lo == null || hi == null) {
      return null
    }
    return Math.max(0, hi - lo)
  })

  const hasBand = bandHeight.some(v => v != null && v > 0)

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter(params: any) {
        if (!Array.isArray(params) || params.length === 0) {
          return ''
        }
        const idx = params[0].dataIndex
        const month = xData[idx]
        const pred = predictData[idx]
        const lo = lowerData[idx]
        const hi = upperData[idx]
        const lines = [`<div style="font-weight:600;margin-bottom:4px">${month}</div>`]
        if (pred != null) lines.push(`预测量：<b>${pred}</b>`)
        if (lo != null) lines.push(`下界 (90%)：<b>${lo}</b>`)
        if (hi != null) lines.push(`上界 (90%)：<b>${hi}</b>`)
        if (lo != null && hi != null) {
          lines.push(`区间宽度：${Math.round((hi - lo) * 100) / 100}`)
        }
        return lines.join('<br/>')
      }
    },
    legend: {
      data: hasBand
        ? ['预测量', '下界', '上界', '90% 置信区间']
        : ['预测量', '下界', '上界']
    },
    grid: {
      left: '3%', right: '4%', bottom: '3%', containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xData,
      name: '预测月份'
    },
    yAxis: {
      type: 'value',
      name: '需求量 (件)',
      min: (value: { min: number }) => Math.max(0, Math.floor(value.min * 0.9))
    },
    series: [
      // 置信带底座（不可见）
      {
        name: '_band_base',
        type: 'line',
        data: bandBase,
        stack: 'confidence',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { opacity: 0 },
        tooltip: { show: false },
        silent: true,
        z: 1
      },
      // 置信带高度 = upper - lower
      {
        name: '90% 置信区间',
        type: 'line',
        data: bandHeight,
        stack: 'confidence',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: {
          color: 'rgba(64, 158, 255, 0.18)'
        },
        tooltip: { show: false },
        z: 1
      },
      {
        name: '下界',
        type: 'line',
        data: lowerData,
        symbol: 'circle',
        symbolSize: 5,
        itemStyle: { color: '#67C23A' },
        lineStyle: { width: 1.5, type: 'dashed', color: '#67C23A' },
        z: 3
      },
      {
        name: '上界',
        type: 'line',
        data: upperData,
        symbol: 'circle',
        symbolSize: 5,
        itemStyle: { color: '#E6A23C' },
        lineStyle: { width: 1.5, type: 'dashed', color: '#E6A23C' },
        z: 3
      },
      {
        name: '预测量',
        type: 'line',
        data: predictData,
        symbol: 'circle',
        symbolSize: 8,
        itemStyle: { color: '#409EFF' },
        lineStyle: { width: 3, color: '#409EFF' },
        z: 4
      }
    ]
  }
  chartInstance.setOption(option)
  // 弹窗打开后 DOM 尺寸稳定后再自适应
  setTimeout(() => {
    chartInstance?.resize()
  }, 50)
}

onMounted(() => {
  fetchData()
  if (hasProgressPermission.value) {
    fetchRunStatus(true)
  }
})

onBeforeUnmount(() => {
  stopProgressPolling()
  if (chartInstance) {
    chartInstance.dispose()
  }
})
</script>

<style scoped>
.ai-forecast-container {
  padding: 20px;
}

.search-form {
  margin-bottom: 20px;
}

.pagination-container {
  margin-top: 20px;
  text-align: right;
}
</style>
