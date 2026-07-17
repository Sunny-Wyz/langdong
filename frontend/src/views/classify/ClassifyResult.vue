<template>
  <div class="page-container classify-result-page">
    <!-- ============================================================
         顶部操作栏：筛选条件 + 操作按钮
         ============================================================ -->
    <div class="toolbar-row">
      <div class="filter-area">
        <!-- ABC分类过滤 -->
        <el-select v-model="filter.abcClass" placeholder="ABC分类" clearable style="width:110px; margin-right:10px" @change="fetchList">
          <el-option label="A类（关键）" value="A" />
          <el-option label="B类（重要）" value="B" />
          <el-option label="C类（一般）" value="C" />
        </el-select>

        <!-- XYZ分类过滤 -->
        <el-select v-model="filter.xyzClass" placeholder="XYZ分类" clearable style="width:120px; margin-right:10px" @change="fetchList">
          <el-option label="X类（稳定）" value="X" />
          <el-option label="Y类（波动）" value="Y" />
          <el-option label="Z类（随机）" value="Z" />
        </el-select>

        <!-- 备件编码关键词 -->
        <el-input
          v-model="filter.partCode"
          placeholder="备件编码搜索"
          clearable
          style="width:160px; margin-right:10px"
          @clear="fetchList"
          @keyup.enter="fetchList"
        >
          <template #append>
            <el-button @click="fetchList">搜索</el-button>
          </template>
        </el-input>

        <!-- 月份选择 -->
        <el-date-picker
          v-model="filter.month"
          type="month"
          placeholder="选择月份"
          format="YYYY-MM"
          value-format="YYYY-MM"
          clearable
          style="width:160px; margin-right:10px"
          @change="fetchList"
        />
      </div>

      <div class="action-area">
        <!-- 导出Excel -->
        <el-button type="success" :loading="exporting" @click="handleExport">
          导出Excel
        </el-button>
        <!-- 手动触发重算（仅ADMIN可见） -->
        <el-button
          v-if="hasPermission('classify:trigger:run')"
          size="small"
          :loading="triggering"
          @click="handleTrigger"
          style="margin-left:10px"
        >
          手动触发重算
        </el-button>
      </div>
    </div>

    <!-- ============================================================
         ABC×XYZ 热力矩阵图（ECharts）
         ============================================================ -->
    <el-card class="matrix-card" shadow="never">
      <template #header>
        <div class="phead header">
          <i class="el-icon-s-data" />
          <div class="title">ABC × XYZ 分布热力矩阵</div>
          <div class="head-btn-group">
            <span class="matrix-tip">（点击格子可过滤列表）</span>
          </div>
        </div>
      </template>
      <div v-if="matrixLoading" class="matrix-placeholder">
        <i class="el-icon-loading" /> 加载中...
      </div>
      <div v-else-if="!echartsAvailable" class="matrix-placeholder echarts-notice">
        <i class="el-icon-info" />
        ECharts 图表需先执行 <code>npm install</code> 安装依赖后才可显示。
        <div class="matrix-fallback">
          <table class="fallback-matrix">
            <thead>
              <tr>
                <th></th>
                <th v-for="xyz in ['X（稳定）','Y（波动）','Z（随机）']" :key="xyz">{{ xyz }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="abc in ['A','B','C']" :key="abc">
                <td class="row-label">{{ abc }}类</td>
                <td
                  v-for="xyz in ['X','Y','Z']"
                  :key="xyz"
                  class="matrix-cell"
                  :class="getCellClass(abc + xyz)"
                  @click="filterByCell(abc, xyz)"
                >
                  {{ matrixData[abc + xyz] || 0 }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else ref="matrixChartRef" class="matrix-chart" />
    </el-card>

    <!-- ============================================================
         分类结果数据表格
         ============================================================ -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="phead header">
          <i class="el-icon-s-data" />
          <div class="title">分类结果明细</div>
          <div class="head-btn-group">
            <span class="table-tip">共 {{ total }} 条记录</span>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe style="width:100%">
        <el-table-column prop="partCode" label="备件编码" width="110" sortable="custom" />
        <el-table-column prop="partName" label="备件名称" min-width="150" show-overflow-tooltip />

        <!-- ABC类别：A=危险红，B=警告黄，C=成功绿 -->
        <el-table-column label="ABC类别" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.abcClass === 'A' ? 'danger' : row.abcClass === 'B' ? 'warning' : 'success'"
              size="small"
            >
              {{ row.abcClass }}类
            </el-tag>
          </template>
        </el-table-column>

        <!-- XYZ类别 -->
        <el-table-column label="XYZ类别" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.xyzClass === 'X' ? 'primary' : row.xyzClass === 'Y' ? 'info' : ''"
              size="small"
            >
              {{ row.xyzClass }}类
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="compositeScore" label="综合得分" width="90" align="right">
          <template #default="{ row }">{{ formatDecimal(row.compositeScore) }}</template>
        </el-table-column>
        <el-table-column prop="annualCost" label="年消耗金额" width="110" align="right">
          <template #default="{ row }">¥ {{ formatDecimal(row.annualCost) }}</template>
        </el-table-column>
        <el-table-column prop="cv2" label="CV²" width="80" align="right">
          <template #default="{ row }">{{ formatDecimal(row.cv2, 4) }}</template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="安全库存(SS)" width="110" align="right" />
        <el-table-column prop="reorderPoint" label="补货触发点(ROP)" width="130" align="right" />
        <el-table-column prop="strategyCode" label="策略编码" width="90" align="center" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.strategyCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="classifyMonth" label="分类月份" width="100" align="center" />
        <el-table-column prop="createTime" label="记录时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-area">
        <el-pagination
          :current-page="pagination.page"
          :page-sizes="[10, 20, 50, 100]"
          :page-size="pagination.pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { ElMessage, ElMessageBox } from 'element-plus'

const authStore = useAuthStore()

const filter = reactive({
  abcClass: '',
  xyzClass: '',
  partCode: '',
  month: ''
})

const tableData = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 20
})

const matrixData = ref<Record<string, number>>({})
const matrixLoading = ref(false)
const matrixChartRef = ref<HTMLDivElement | null>(null)
let matrixChart: any = null
const echartsAvailable = ref(false)
let echartsLib: any = null

const exporting = ref(false)
const triggering = ref(false)

const permissions = computed(() => authStore.permissions || [])

function hasPermission(perm: string) {
  return permissions.value.includes(perm)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await request.get('/classify/result', {
      params: {
        abcClass: filter.abcClass || undefined,
        xyzClass: filter.xyzClass || undefined,
        partCode: filter.partCode || undefined,
        month: filter.month || undefined,
        page: pagination.page,
        pageSize: pagination.pageSize
      }
    })
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error('获取分类结果失败')
  } finally {
    loading.value = false
  }
}

async function fetchMatrix() {
  matrixLoading.value = true
  try {
    const res = await request.get('/classify/matrix')
    matrixData.value = res.data || {}
  } catch (e) {
    ElMessage.error('获取矩阵数据失败')
  } finally {
    matrixLoading.value = false
    nextTick(() => {
      ensureMatrixChart()
      renderMatrix()
    })
  }
}

async function initECharts() {
  try {
    const echarts = await import('echarts')
    echartsLib = echarts
    echartsAvailable.value = true
    nextTick(() => {
      ensureMatrixChart()
      renderMatrix()
    })
  } catch (e) {
    echartsAvailable.value = false
  }
}

function ensureMatrixChart() {
  if (!echartsAvailable.value || !echartsLib) return
  if (!matrixChartRef.value) return
  if (matrixChart) return
  matrixChart = echartsLib.init(matrixChartRef.value)
}

function renderMatrix() {
  if (!matrixChart || !echartsAvailable.value) return

  const abcList = ['C', 'B', 'A']
  const xyzList = ['X', 'Y', 'Z']

  const heatmapData: number[][] = []
  for (let xi = 0; xi < xyzList.length; xi++) {
    for (let ai = 0; ai < abcList.length; ai++) {
      const key = abcList[ai] + xyzList[xi]
      heatmapData.push([xi, ai, matrixData.value[key] || 0])
    }
  }

  const option = {
    tooltip: {
      formatter: (params: any) => {
        const abc = abcList[params.data[1]]
        const xyz = xyzList[params.data[0]]
        return `${abc}${xyz}：${params.data[2]} 个备件`
      }
    },
    grid: { top: '50px', left: '100px', right: '80px', bottom: '60px', containLabel: true },
    xAxis: {
      type: 'category',
      data: ['X（需求稳定）', 'Y（需求波动）', 'Z（需求随机）'],
      name: 'XYZ分类',
      nameLocation: 'end',
      nameGap: 15
    },
    yAxis: {
      type: 'category',
      data: ['C类（一般）', 'B类（重要）', 'A类（关键）'],
      name: 'ABC分类',
      nameLocation: 'end',
      nameGap: 15
    },
    visualMap: {
      min: 0,
      max: Math.max(...Object.values(matrixData.value), 1),
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: '5px',
      itemWidth: 14, // 厚度
      itemHeight: 600, // 长度（横向时的拉伸长度，覆盖整个下方）
      inRange: { color: ['#f0f9ff', '#0369a1'] }
    },
    series: [{
      type: 'heatmap',
      data: heatmapData,
      label: {
        show: true,
        formatter: (params: any) => params.data[2],
        fontSize: 16,
        fontWeight: 'bold'
      },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' }
      }
    }]
  }

  matrixChart.setOption(option)

  matrixChart.off('click')
  matrixChart.on('click', (params: any) => {
    if (params.componentType === 'series') {
      const abc = abcList[params.data[1]]
      const xyz = xyzList[params.data[0]]
      filterByCell(abc, xyz)
    }
  })
}

function resizeChart() {
  if (matrixChart) {
    matrixChart.resize()
  }
}

function filterByCell(abc: string, xyz: string) {
  filter.abcClass = abc
  filter.xyzClass = xyz
  pagination.page = 1
  fetchList()
  ElMessage.info(`已过滤：${abc}类 × ${xyz}类`)
}

function getCellClass(key: string) {
  const val = matrixData.value[key] || 0
  if (val === 0) return 'cell-empty'
  if (val > 20) return 'cell-high'
  if (val > 5) return 'cell-medium'
  return 'cell-low'
}

function handleTrigger() {
  ElMessageBox.confirm(
    '确认重新触发分类计算？此操作将更新所有备件的分类结果和库存策略参数',
    '确认触发',
    { type: 'warning', confirmButtonText: '确认触发', cancelButtonText: '取消' }
  ).then(async () => {
    triggering.value = true
    try {
      const res = await request.post('/classify/trigger')
      ElMessage.success(res.data.message || '任务已启动，请稍后刷新查看最新分类结果')
    } catch (e: any) {
      const msg = e.response?.data?.message || '触发失败，请检查权限或联系管理员'
      ElMessage.error(msg)
    } finally {
      triggering.value = false
    }
  }).catch(() => {})
}

async function handleExport() {
  if (tableData.value.length === 0) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  exporting.value = true
  try {
    const XLSX = await import('xlsx')
    const { saveAs } = await import('file-saver')

    const headers = [
      '备件编码', '备件名称', 'ABC类别', 'XYZ类别', '综合得分',
      '年消耗金额', 'CV²', '安全库存', '补货触发点', '策略编码',
      '分类月份', '记录时间'
    ]
    const rows = tableData.value.map(row => [
      row.partCode,
      row.partName,
      row.abcClass,
      row.xyzClass,
      row.compositeScore,
      row.annualCost,
      row.cv2,
      row.safetyStock,
      row.reorderPoint,
      row.strategyCode,
      row.classifyMonth,
      formatTime(row.createTime)
    ])

    const wsData = [headers, ...rows]
    const ws = XLSX.utils.aoa_to_sheet(wsData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '备件分类结果')
    const buf = XLSX.write(wb, { type: 'array', bookType: 'xlsx' })
    const blob = new Blob([buf], { type: 'application/octet-stream' })
    const month = filter.month || new Date().toISOString().slice(0, 7)
    saveAs(blob, `备件分类结果_${month}.xlsx`)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败，请确认已执行 npm install 安装 xlsx 和 file-saver')
  } finally {
    exporting.value = false
  }
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchList()
}

function handleSizeChange(size: number) {
  pagination.pageSize = size
  pagination.page = 1
  fetchList()
}

function formatDecimal(val: any, places = 2) {
  if (val === null || val === undefined) return '—'
  return Number(val).toFixed(places)
}

function formatTime(t: any) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  fetchList()
  fetchMatrix()
  initECharts()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  if (matrixChart) {
    matrixChart.dispose()
    matrixChart = null
  }
})
</script>

<style scoped>
.classify-result-page {
  padding: 16px;
}

/* 顶部工具栏 */
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-area {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}

/* 矩阵卡片 */
.matrix-card {
  margin-bottom: 16px;
}

.matrix-tip {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

.matrix-chart {
  height: 320px;
  width: 100%;
}

.matrix-placeholder {
  height: 60px;
  display: flex;
  align-items: center;
  color: #999;
  font-size: 14px;
  gap: 6px;
}

.echarts-notice {
  flex-direction: column;
  align-items: flex-start;
  height: auto;
  padding: 10px 0;
}

/* 降级HTML矩阵表格 */
.matrix-fallback {
  margin-top: 12px;
}

.fallback-matrix {
  border-collapse: collapse;
  text-align: center;
}

.fallback-matrix th,
.fallback-matrix td {
  border: 1px solid #ddd;
  padding: 10px 20px;
  min-width: 80px;
}

.fallback-matrix th {
  background: #f5f7fa;
  font-weight: bold;
}

.row-label {
  background: #f5f7fa;
  font-weight: bold;
}

.matrix-cell {
  cursor: pointer;
  transition: background 0.2s;
}

.matrix-cell:hover {
  background: #e6f3ff !important;
}

.cell-empty  { background: #f9f9f9; color: #ccc; }
.cell-low    { background: #dbeafe; }
.cell-medium { background: #93c5fd; }
.cell-high   { background: #1d4ed8; color: #fff; }

/* 表格卡片 */
.table-card {
  margin-bottom: 16px;
}

.table-tip {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

/* 分页 */
.pagination-area {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
