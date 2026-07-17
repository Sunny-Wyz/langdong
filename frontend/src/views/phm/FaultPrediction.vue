<template>
  <div class="page-container fault-prediction-page">
    <!-- ============================================================
         顶部统计卡片区域
         ============================================================ -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card stat-total" shadow="hover">
          <div class="stat-icon"><i class="el-icon-s-data" /></div>
          <div class="stat-content">
            <div class="stat-label">预测设备数</div>
            <div class="stat-value">{{ dashboard.totalDevices || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-critical" shadow="hover">
          <div class="stat-icon"><i class="el-icon-warning-outline" /></div>
          <div class="stat-content">
            <div class="stat-label">高风险设备</div>
            <div class="stat-value">{{ dashboard.highRiskCount || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-warning" shadow="hover">
          <div class="stat-icon"><i class="el-icon-data-line" /></div>
          <div class="stat-content">
            <div class="stat-label">平均故障概率</div>
            <div class="stat-value">{{ formatPercent(dashboard.avgProbability) }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-info" shadow="hover">
          <div class="stat-icon"><i class="el-icon-message-solid" /></div>
          <div class="stat-content">
            <div class="stat-label">预测故障总数</div>
            <div class="stat-value">{{ dashboard.totalPredictedFaults || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============================================================
         操作栏：筛选 + 手动触发预测
         ============================================================ -->
    <div class="toolbar-row">
      <div class="toolbar-left">
        <el-input
          v-model="queryParams.deviceCode"
          placeholder="设备编码/名称"
          clearable
          style="width: 200px; margin-right: 10px"
          @clear="handleQuery"
        />
        <el-select
          v-model="queryParams.minProbability"
          placeholder="风险等级"
          clearable
          style="width: 150px; margin-right: 10px"
          @change="handleQuery"
        >
          <el-option label="全部" :value="null" />
          <el-option label="高风险 (>70%)" :value="0.7" />
          <el-option label="中风险 (50-70%)" :value="0.5" />
          <el-option label="低风险 (<50%)" :value="0" />
        </el-select>
        <el-button size="small" @click="handleQuery">查询</el-button>
        <el-button size="small" @click="handleReset">重置</el-button>
      </div>
      <div class="toolbar-right">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          inactive-text=""
          style="margin-right: 10px"
        />
      </div>
    </div>

    <!-- ============================================================
         预测结果列表
         ============================================================ -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="phead header">
          <i class="el-icon-s-data" />
          <div class="title">故障预测结果</div>
          <div class="head-btn-group">
            <span class="card-tip">（显示最新预测记录）</span>
          </div>
        </div>
      </template>

      <el-table v-loading="tableLoading" :data="predictionList" border stripe style="width:100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="deviceCode" label="设备编码" width="120" sortable="custom" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />

        <el-table-column label="故障概率" width="110" align="center" sort-by="failureProbability">
          <template #default="{ row }">
            <el-tag :type="getProbabilityTagType(row.failureProbability)" size="small">
              {{ formatPercent(row.failureProbability) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="预测故障数" width="110" align="center" sort-by="predictedFaultCount">
          <template #default="{ row }">
            <span style="font-weight: bold; color: #E6A23C">{{ row.predictedFaultCount || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="置信区间" width="120" align="center">
          <template #default="{ row }">
            <span style="color: #909399; font-size: 12px">
              [{{ row.faultCountLower }}-{{ row.faultCountUpper }}]
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="targetMonth" label="目标月份" width="100" align="center" />
        <el-table-column prop="predictionDate" label="预测日期" width="110" align="center" />
        <el-table-column prop="modelType" label="模型类型" width="150" show-overflow-tooltip />

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="hasPermission('phm:prediction:predict')"
              size="small"
              @click="handleRePredict(row)"
            >
              重新预测
            </el-button>
            <el-button
              size="small"
              @click="handleViewHistory(row)"
            >
              历史记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        :current-page="queryParams.page"
        :page-sizes="[10, 20, 50, 100]"
        :page-size="queryParams.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; text-align: right"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- ============================================================
         高风险设备提示卡片
         ============================================================ -->
    <el-card v-if="highRiskDevices.length > 0" class="high-risk-card" shadow="never">
      <template #header>
        <div class="phead header">
          <i class="el-icon-s-data" />
          <div class="title">高风险设备警告</div>
          <div class="head-btn-group">
            <span class="card-tip">（故障概率 > 70%）</span>
          </div>
        </div>
      </template>

      <el-table :data="highRiskDevices" border size="small" style="width:100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="deviceCode" label="设备编码" width="120" sortable="custom" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="故障概率" width="110" align="center">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ formatPercent(row.failureProbability) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="predictedFaultCount" label="预测故障数" width="110" align="center" />
        <el-table-column prop="targetMonth" label="目标月份" width="100" align="center" />
      </el-table>
    </el-card>

    <!-- ============================================================
         预测历史对话框
         ============================================================ -->
    <el-dialog
      v-model="historyDialogVisible"
      :title="'预测历史 - ' + selectedDevice.deviceCode"
      width="900px"
      @close="handleHistoryDialogClose"
    >
      <el-table v-loading="historyLoading" :data="historyList" border size="small" max-height="400">
        <el-table-column prop="predictionDate" label="预测日期" width="110" align="center" />
        <el-table-column prop="targetMonth" label="目标月份" width="100" align="center" />
        <el-table-column label="故障概率" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getProbabilityTagType(row.failureProbability)" size="small">
              {{ formatPercent(row.failureProbability) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="predictedFaultCount" label="预测故障数" width="110" align="center" />
        <el-table-column label="置信区间" width="120" align="center">
          <template #default="{ row }">
            [{{ row.faultCountLower }}-{{ row.faultCountUpper }}]
          </template>
        </el-table-column>
        <el-table-column prop="modelType" label="模型类型" min-width="150" show-overflow-tooltip />
      </el-table>

      <template #footer>
        <el-button @click="historyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'

const authStore = useAuthStore()

const dashboard = ref({
  totalDevices: 0,
  highRiskCount: 0,
  avgProbability: 0,
  totalPredictedFaults: 0
})

const queryParams = reactive<{
  deviceCode: string
  minProbability: number | null
  page: number
  pageSize: number
}>({
  deviceCode: '',
  minProbability: null,
  page: 1,
  pageSize: 20
})

const predictionList = ref<any[]>([])
const total = ref(0)
const tableLoading = ref(false)

const highRiskDevices = ref<any[]>([])

const historyDialogVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref<any[]>([])
const selectedDevice = ref<any>({})

const autoRefresh = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const permissions = computed(() => authStore.permissions || [])

async function loadDashboard() {
  try {
    // TODO: 调用 Dashboard API
    // 临时使用模拟数据
    dashboard.value = {
      totalDevices: 10,
      highRiskCount: 3,
      avgProbability: 0.65,
      totalPredictedFaults: 85
    }
  } catch (error) {
    // keep mock dashboard on failure
  }
}

async function loadPredictionList() {
  tableLoading.value = true
  try {
    const res = await request.get('/phm/prediction/latest', {
      params: {
        deviceCode: queryParams.deviceCode || null,
        minProbability: queryParams.minProbability,
        page: queryParams.page,
        pageSize: queryParams.pageSize
      }
    })

    if (res.data.code === 200) {
      predictionList.value = res.data.data || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.data.message || '加载预测列表失败')
    }
  } catch (error: any) {
    ElMessage.error('加载预测列表失败：' + error.message)
  } finally {
    tableLoading.value = false
  }
}

async function loadHighRiskDevices() {
  try {
    const res = await request.get('/phm/prediction/high-risk', {
      params: { threshold: 0.7, limit: 10 }
    })

    if (res.data.code === 200) {
      highRiskDevices.value = res.data.data || []
    }
  } catch (error) {
    // ignore high-risk load errors
  }
}

async function loadPredictionHistory(deviceId: any, months = 12) {
  historyLoading.value = true
  try {
    const res = await request.get(`/phm/prediction/history/${deviceId}`, {
      params: { months }
    })

    if (res.data.code === 200) {
      historyList.value = res.data.data || []
    } else {
      ElMessage.error(res.data.message || '加载预测历史失败')
    }
  } catch (error: any) {
    ElMessage.error('加载预测历史失败：' + error.message)
  } finally {
    historyLoading.value = false
  }
}

async function handleRePredict(row: any) {
  ElMessageBox.confirm(`确定要重新预测设备 ${row.deviceCode} 的故障情况吗？`, '确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    const loading = ElLoading.service({ text: '正在预测...' })
    try {
      const res = await request.post(`/phm/prediction/predict/${row.deviceId}`, null, {
        params: { predictionDays: 90 }
      })

      if (res.data.code === 200) {
        ElMessage.success('预测成功')
        loadPredictionList()
        loadHighRiskDevices()
      } else {
        ElMessage.error(res.data.message || '预测失败')
      }
    } catch (error: any) {
      ElMessage.error('预测失败：' + error.message)
    } finally {
      loading.close()
    }
  }).catch(() => {})
}

function handleViewHistory(row: any) {
  selectedDevice.value = row
  historyDialogVisible.value = true
  loadPredictionHistory(row.deviceId)
}

function handleHistoryDialogClose() {
  historyList.value = []
  selectedDevice.value = {}
}

function handleQuery() {
  queryParams.page = 1
  loadPredictionList()
}

function handleReset() {
  queryParams.deviceCode = ''
  queryParams.minProbability = null
  queryParams.page = 1
  queryParams.pageSize = 20
  loadPredictionList()
}

function handleSizeChange(val: number) {
  queryParams.pageSize = val
  queryParams.page = 1
  loadPredictionList()
}

function handlePageChange(val: number) {
  queryParams.page = val
  loadPredictionList()
}

function startAutoRefresh() {
  refreshTimer = setInterval(() => {
    loadPredictionList()
    loadHighRiskDevices()
  }, 30000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function formatPercent(value: any) {
  if (value == null || isNaN(value)) return '0%'
  return (value * 100).toFixed(1) + '%'
}

function getProbabilityTagType(probability: number) {
  if (probability >= 0.7) return 'danger'
  if (probability >= 0.5) return 'warning'
  return 'success'
}

function hasPermission(permission: string) {
  return permissions.value.includes(permission)
}

watch(autoRefresh, (val) => {
  if (val) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
})

onMounted(() => {
  loadDashboard()
  loadPredictionList()
  loadHighRiskDevices()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.fault-prediction-page {
  padding: 20px;
  background-color: #f0f2f5;
  min-height: 100vh;
}

/* ============================================================
   统计卡片样式
   ============================================================ */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-icon {
  font-size: 48px;
  margin-right: 20px;
  opacity: 0.8;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-total {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.stat-critical {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.stat-warning {
  background: linear-gradient(135deg, #fbc2eb 0%, #f6a192 100%);
  color: white;
}

.stat-info {
  background: linear-gradient(135deg, #a8edea 0%, #7ec9c4 100%);
  color: white;
}

/* ============================================================
   工具栏样式
   ============================================================ */
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
}

/* ============================================================
   表格卡片样式
   ============================================================ */
.table-card {
  margin-bottom: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.card-tip {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}

/* ============================================================
   高风险卡片样式
   ============================================================ */
.high-risk-card {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border: 2px solid #F56C6C;
}

.high-risk-card :deep(.el-card__header) {
  background-color: #FEF0F0;
  border-bottom: 1px solid #F56C6C;
}
</style>
