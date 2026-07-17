<template>
  <div class="page-container ai-train-data-container">
    <el-card shadow="hover">
      <template #header>
        <div class="phead header">
          <i class="el-icon-data-analysis" />
          <div class="title">训练数据看板</div>
          <div class="head-btn-group">
            <el-button class="ghost-btn" :loading="refreshing" @click="refreshTrainData">刷新训练集</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
        title="日粒度离线样本表（备件×日密面板），不是月度两阶段 Hurdle-Gamma 的实时训练矩阵"
      >
        <div style="font-size: 12px; line-height: 1.6; margin-top: 4px;">
          默认只看「有真实出库」的行（插补=否），并按出库量优先排序，避免首页全是 0。
          月度预测主链路从领用月汇总现算特征；本表供浏览/周模型等日粒度场景。可每日 00:30 自动刷新。
        </div>
      </el-alert>

      <el-descriptions v-if="meta" :column="3" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="业务日起">{{ meta.minBizDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务日止">{{ meta.maxBizDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近有消耗日">{{ meta.lastDemandDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="样本行数">{{ meta.totalRows ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="非零出库行">
          {{ meta.nonzeroOutboundRows ?? '-' }}
          <span v-if="meta.nonzeroOutboundRatio != null">（{{ meta.nonzeroOutboundRatio }}%）</span>
        </el-descriptions-item>
        <el-descriptions-item label="插补/NONE 行">
          {{ meta.imputedRows ?? '-' }} / {{ meta.noneSourceRows ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="备件数">{{ meta.distinctParts ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近更新">{{ formatTime(meta.lastUpdatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="目标窗口">{{ meta.windowStart || '-' }} ~ {{ meta.windowEnd || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-form :inline="true" :model="searchForm" class="search-form" size="small">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            unlink-panels
          />
        </el-form-item>
        <el-form-item label="备件编码">
          <el-input v-model.trim="searchForm.partCode" placeholder="如 C0010003" clearable />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="searchForm.sourceLevel" clearable placeholder="全部" style="width: 140px">
            <el-option label="TRACE" value="TRACE" />
            <el-option label="REQ_OUT" value="REQ_OUT" />
            <el-option label="TRACE_REQ" value="TRACE_REQ" />
            <el-option label="NONE" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="插补">
          <el-select v-model="searchForm.isImputed" clearable placeholder="全部" style="width: 100px">
            <el-option label="是" :value="1" />
            <el-option label="否" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="searchForm.orderBy" style="width: 150px">
            <el-option label="出库量优先" value="outbound_desc" />
            <el-option label="业务日最新" value="biz_date_desc" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button class="ghost-btn" @click="handleSearch">查询</el-button>
          <el-button class="ghost-btn" @click="resetSearch">重置为推荐筛选</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border v-loading="loading" style="width: 100%">
        <el-table-column prop="bizDate" label="业务日期" width="110" />
        <el-table-column prop="partCode" label="备件编码" width="120" />
        <el-table-column prop="dailyOutboundQty" label="日出库量" width="95">
          <template #header>
            <el-tooltip content="TRACE 与领用出库取较大值；插补日为 0" placement="top">
              <span>日出库量</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="dailyRequisitionApplyQty" label="领用申请量" width="105" />
        <el-table-column prop="dailyRequisitionOutQty" label="领用出库量" width="105" />
        <el-table-column prop="dailyInstallQty" label="安装量" width="85" />
        <el-table-column prop="dailyWorkOrderCnt" label="工单数" width="85" />
        <el-table-column prop="dailyPurchaseArrivalQty" label="到货量" width="85" />
        <el-table-column prop="sourceLevel" label="来源" width="95">
          <template #default="scope">
            <el-tag size="small" :type="sourceTagType(scope.row.sourceLevel)">{{ scope.row.sourceLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isImputed" label="插补" width="70">
          <template #default="scope">
            <span>{{ scope.row.isImputed === 1 ? '是' : '否' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160" />
      </el-table>

      <el-empty
        v-if="!loading && tableData.length === 0"
        description="当前筛选无数据。密面板下无消耗日会被过滤；可改为插补=全部或放宽日期。"
        :image-size="80"
        style="margin-top: 12px"
      />

      <div class="pagination-container">
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="page"
          :page-sizes="[20, 50, 100]"
          :page-size="size"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const searchForm = reactive<{
  dateRange: string[]
  partCode: string
  sourceLevel: string
  isImputed: number | null
  orderBy: string
}>({
  dateRange: [],
  partCode: '',
  sourceLevel: '',
  isImputed: 0,
  orderBy: 'outbound_desc'
})
const tableData = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const meta = ref<any>(null)
const defaultsApplied = ref(false)

function applyMeta(payload: any) {
  if (payload && typeof payload === 'object') {
    meta.value = payload
  }
}

function applyRecommendedFiltersFromMeta() {
  if (defaultsApplied.value || !meta.value) {
    return
  }
  // 默认：插补=否；日期=最近有消耗日前 90 天（若有）
  searchForm.isImputed = 0
  searchForm.orderBy = 'outbound_desc'
  const lastDemand = meta.value.lastDemandDate
  if (lastDemand) {
    const end = String(lastDemand).slice(0, 10)
    const endDate = new Date(end + 'T00:00:00')
    const startDate = new Date(endDate)
    startDate.setDate(startDate.getDate() - 89)
    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
    const fmt = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    searchForm.dateRange = [fmt(startDate), end]
  }
  defaultsApplied.value = true
}

function fetchMeta() {
  return request.get('/ai/train-data/meta').then(res => {
    const body = res.data || {}
    applyMeta(body.data || body)
    applyRecommendedFiltersFromMeta()
  }).catch(() => {
    /* meta optional */
  })
}

function fetchData() {
  loading.value = true
  const [startDate, endDate] = searchForm.dateRange || []
  request
    .get('/ai/train-data/list', {
      params: {
        page: page.value,
        size: size.value,
        startDate,
        endDate,
        partCode: searchForm.partCode,
        sourceLevel: searchForm.sourceLevel,
        isImputed: searchForm.isImputed,
        orderBy: searchForm.orderBy
      }
    })
    .then(res => {
      const data = res.data || {}
      tableData.value = data.list || []
      total.value = data.total || 0
      if (data.meta) {
        applyMeta(data.meta)
      }
    })
    .finally(() => {
      loading.value = false
    })
}

async function refreshTrainData() {
  try {
    await ElMessageBox.confirm(
      '将按业务最大出库日贴齐窗口，重跑近 2 年日粒度训练样本（可能需要几十秒）。是否继续？',
      '刷新训练集',
      { type: 'warning', confirmButtonText: '开始刷新', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  refreshing.value = true
  try {
    const res = await request.post(
      '/ai/train-data/refresh',
      {},
      { timeout: 300000 }
    )
    const body = res.data || {}
    ElMessage.success(body.message || '训练数据已刷新')
    defaultsApplied.value = false
    if (body.data && body.data.meta) {
      applyMeta(body.data.meta)
      applyRecommendedFiltersFromMeta()
    } else {
      await fetchMeta()
    }
    page.value = 1
    fetchData()
  } catch (error: any) {
    const msg =
      error?.response?.data?.message ||
      error?.message ||
      '刷新失败'
    ElMessage.error(msg)
  } finally {
    refreshing.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function resetSearch() {
  defaultsApplied.value = false
  searchForm.dateRange = []
  searchForm.partCode = ''
  searchForm.sourceLevel = ''
  searchForm.isImputed = 0
  searchForm.orderBy = 'outbound_desc'
  applyRecommendedFiltersFromMeta()
  page.value = 1
  fetchData()
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

function sourceTagType(sourceLevel: string) {
  if (sourceLevel === 'TRACE') return 'success'
  if (sourceLevel === 'REQ_OUT') return 'warning'
  if (sourceLevel === 'TRACE_REQ') return 'primary'
  return 'info'
}

function formatTime(value: any) {
  if (!value) return '-'
  if (typeof value === 'string') return value.replace('T', ' ').slice(0, 19)
  return String(value)
}

onMounted(async () => {
  await fetchMeta()
  fetchData()
})
</script>

<style scoped>
.ai-train-data-container {
  padding: 20px;
}

.header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title {
  font-weight: 600;
  flex: 1;
}

.search-form {
  margin-bottom: 16px;
}

.pagination-container {
  margin-top: 20px;
  text-align: right;
}

.ghost-btn {
  background: #fff !important;
  border: 1px solid #0f3086 !important;
  color: #0f3086 !important;
}

.ghost-btn:hover,
.ghost-btn:focus {
  background: #fff !important;
  border-color: #0f3086 !important;
  color: #0f3086 !important;
}
</style>
